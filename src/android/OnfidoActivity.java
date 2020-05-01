package com.plugin.onfido;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;

import android.content.Intent;
import android.os.Bundle;

import com.onfido.android.sdk.capture.ExitCode;
import com.onfido.android.sdk.capture.Onfido;
import com.onfido.android.sdk.capture.OnfidoConfig;
import com.onfido.android.sdk.capture.OnfidoFactory;
import com.onfido.android.sdk.capture.errors.OnfidoException;
import com.onfido.android.sdk.capture.ui.camera.face.FaceCaptureVariant;
import com.onfido.android.sdk.capture.ui.camera.face.stepbuilder.FaceCaptureStepBuilder;
import com.onfido.android.sdk.capture.ui.options.FlowStep;
import com.onfido.android.sdk.capture.ui.options.CaptureScreenStep;
import com.onfido.android.sdk.capture.ui.options.stepbuilder.DocumentCaptureStepBuilder;
import com.onfido.android.sdk.capture.upload.Captures;
import com.onfido.android.sdk.capture.upload.DocumentSide;
import com.onfido.android.sdk.capture.ui.country_selection.CountryAlternatives;
import com.onfido.android.sdk.capture.DocumentType;
import com.onfido.android.sdk.capture.utils.CountryCode;

import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.*;


public class OnfidoActivity extends Activity {
    private Onfido client;
    private boolean firstTime = true;
    private static final String TAG = "OnFidoBridge";

    private static final FlowStep canadaLicenceStep = DocumentCaptureStepBuilder.forDrivingLicence()
        .withCountry(CountryCode.CA)
        .build();

    private static final FlowStep passportStep = DocumentCaptureStepBuilder.forPassport()
        .build();

    private Map<String,FlowStep> createMapStringToFlowStep(){
        HashMap flowStepMapping = new HashMap<String, FlowStep>();

        flowStepMapping.put("welcome", FlowStep.WELCOME);
        flowStepMapping.put("document", FlowStep.CAPTURE_DOCUMENT);
        flowStepMapping.put("drivers_licence_CA", OnfidoActivity.canadaLicenceStep);
        flowStepMapping.put("passport", OnfidoActivity.passportStep);
        flowStepMapping.put("face", FlowStep.CAPTURE_FACE);
        // FaceCaptureStep is deprecated
        // update to use FaceCaptureStepBuilder.forVideo()
        flowStepMapping.put("face_video", FaceCaptureStepBuilder().withIntro(true).build());
        flowStepMapping.put("final", FlowStep.FINAL);

        return flowStepMapping;
    }

    private FlowStep[] generateFlowStep(ArrayList<String> flowSteps){
        Map<String,FlowStep> mapping = createMapStringToFlowStep();
        FlowStep[] steps = new FlowStep[flowSteps.size()];

        for (int i = 0 ; i < flowSteps.size() ; i++) {
            steps[i] = mapping.get(flowSteps.get(i));
        }

        return steps;
    }

    @Override
    public void onStart() {
        super.onStart();

        if (firstTime == true) {
            client = OnfidoFactory.create(this).getClient();

            Bundle extras = getIntent().getExtras();
            String applicantId = "";
            String token = "";
            String locale = "en";
            ArrayList<String> flowSteps = null;

            if (extras != null) {
                token = extras.getString("token");
                flowSteps = extras.getStringArrayList("flow_steps");
                locale = extras.getString("locale");
            }

            FlowStep[] flow = generateFlowStep(flowSteps);

            final OnfidoConfig config = OnfidoConfig.builder(this)
                    .withSDKToken(token)
                    .withCustomFlow(flow)
                    .withLocale(new Locale(locale))
                    .build();

            client.startActivityForResult(this, 1, config);
        }
    }

    protected JSONObject buildCaptureJsonObject(Captures captures) throws JSONException {
        JSONObject captureJson = new JSONObject();
        if (captures.getDocument() == null) {
            captureJson.put("document", null);
        }

        JSONObject docJson = new JSONObject();

        DocumentSide frontSide = captures.getDocument().getFront();
        if (frontSide != null) {
            JSONObject docSideJson = new JSONObject();
            docSideJson.put("id", frontSide.getId());
            docSideJson.put("side", frontSide.getSide());
            docSideJson.put("type", frontSide.getType());

            docJson.put("front", docSideJson);
        }

        DocumentSide backSide = captures.getDocument().getBack();
        if (backSide != null) {
            JSONObject docSideJson = new JSONObject();
            docSideJson.put("id", backSide.getId());
            docSideJson.put("side", backSide.getSide());
            docSideJson.put("type", backSide.getType());

            docJson.put("back", docSideJson);
        }

        captureJson.put("document", docJson);

        return captureJson;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        client.handleActivityResult(resultCode, data, new Onfido.OnfidoResultListener() {
            @Override
            public void userCompleted(Captures captures) {
                Intent intent = new Intent();
                JSONObject captureJson;
                try {
                    captureJson = buildCaptureJsonObject(captures);
                } catch (JSONException e) {
                    Log.d(TAG, "userCompleted: failed to build json result");
                    return;
                }

                Log.d(TAG, "userCompleted: successfully returned data to plugin");
                intent.putExtra("data", captureJson.toString());
                setResult(Activity.RESULT_OK, intent);
                finish();// Exit of this activity !

            }

            @Override
            public void userExited(ExitCode exitCode) {
                Intent intent = new Intent();
                Log.d(TAG, "userExited: YES");
                setResult(Activity.RESULT_CANCELED, intent);
                finish();// Exit of this activity !
            }

            @Override
            public void onError(OnfidoException e) {
                Intent intent = new Intent();
                Log.d(TAG, "onError: YES");
                e.printStackTrace();
                setResult(Activity.RESULT_CANCELED, intent);
                finish();// Exit of this activity !
            }
        });
    }
}
