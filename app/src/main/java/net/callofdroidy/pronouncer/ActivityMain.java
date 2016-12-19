package net.callofdroidy.pronouncer;

import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.DriveId;
import com.google.android.gms.drive.OpenFileActivityBuilder;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Locale;

import butterknife.BindView;
import butterknife.ButterKnife;

import static net.callofdroidy.pronouncer.Constants.REQUEST_CODE_OPENER;
import static net.callofdroidy.pronouncer.Constants.REQUEST_CODE_RESOLVE_CONNECTION;

public class ActivityMain extends AppCompatActivity implements GoogleApiClient.OnConnectionFailedListener,
        GoogleApiClient.ConnectionCallbacks, View.OnClickListener {
    private static final String TAG = "ActivityMain";

    TextToSpeech tts;

    GoogleApiClient mGoogleApiClient;
    DriveId mFileDriveId;
    JSONObject words = null;
    @BindView(R.id.et_word)
    EditText etWord;
    @BindView(R.id.btn_last_one)
    Button btnLastOne;
    @BindView(R.id.btn_start)
    Button btnStart;
    @BindView(R.id.tv_blank)
    TextView tvBlank;

    int currentIndex = 0;
    int totalLength;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        btnLastOne.setOnClickListener(this);
        btnStart.setOnClickListener(this);

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Drive.API)
                .addScope(Drive.SCOPE_FILE)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
        mGoogleApiClient.connect();
    }

    public void openDriveFile() {
        Log.e(TAG, "openDriveFile: trying, driveId: " + mFileDriveId.toString());
        DriveFile driveFile = mFileDriveId.asDriveFile();

        driveFile.open(mGoogleApiClient, DriveFile.MODE_READ_ONLY, null).setResultCallback(new ResultCallback<DriveApi.DriveContentsResult>() {
            @Override
            public void onResult(@NonNull DriveApi.DriveContentsResult driveContentsResult) {
                if (!driveContentsResult.getStatus().isSuccess()) {
                    // display an error saying file can't be opened
                    Toast.makeText(ActivityMain.this, "onResult: fileStatus: " + driveContentsResult.getStatus(), Toast.LENGTH_SHORT).show();
                    return;
                }
                // DriveContents object contains pointers to the actual byte stream
                DriveContents contents = driveContentsResult.getDriveContents();

                BufferedReader reader = new BufferedReader(new InputStreamReader(contents.getInputStream()));
                StringBuilder builder = new StringBuilder();
                String line;
                try {
                    while ((line = reader.readLine()) != null)
                        builder.append(line);
                } catch (IOException e) {
                    Toast.makeText(ActivityMain.this, "IOException: " + e.toString(), Toast.LENGTH_SHORT).show();
                }
                try {
                    words = new JSONObject(builder.toString());
                    Toast.makeText(ActivityMain.this, "File loaded !", Toast.LENGTH_SHORT).show();
                    initTts();
                    totalLength = words.names().length();
                    prepareCurrentWord();
                } catch (JSONException e) {
                    Log.e(TAG, "onResult: " + e.toString());
                }
            }
        });
    }

    public void initTts() {
        tts = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int i) {
                tts.setLanguage(Locale.UK);
            }
        });
    }

    public void prepareCurrentWord(){
        try {
            String key = words.names().getString(currentIndex);
            String value = words.getString(key);
            etWord.setText(key);
            tvBlank.setText(value);
        } catch (JSONException e) {
            Toast.makeText(this, e.toString(), Toast.LENGTH_SHORT).show();
        }
    }

    public void pronounceTheWord(){
        String word = etWord.getText().toString();
        tts.speak(word, TextToSpeech.QUEUE_FLUSH, null, null);
        tts.playSilentUtterance(1500, TextToSpeech.QUEUE_ADD, null);
        tts.speak(word, TextToSpeech.QUEUE_ADD, null, null);
        tts.playSilentUtterance(1500, TextToSpeech.QUEUE_ADD, null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mGoogleApiClient == null) {
            // Create the API client and bind it to an instance variable.
            // We use this instance as the callback for connection and connection
            // failures.
            // Since no account name is passed, the user is prompted to choose.
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addApi(Drive.API)
                    .addScope(Drive.SCOPE_FILE)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();

            mGoogleApiClient.connect();
        }
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        Log.e(TAG, "API client connected.");

        if (mFileDriveId != null) {
            openDriveFile();
            return;
        }

        IntentSender intentSender = Drive.DriveApi
                .newOpenFileActivityBuilder()
                .setMimeType(new String[]{"application/json", "text/plain"})
                .build(mGoogleApiClient);
        try {
            startIntentSenderForResult(intentSender, REQUEST_CODE_OPENER, null, 0, 0, 0);
        } catch (IntentSender.SendIntentException e) {
            Log.e(TAG, "onConnected: " + e.toString());
        }
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        if (connectionResult.hasResolution()) {
            try {
                connectionResult.startResolutionForResult(this, REQUEST_CODE_RESOLVE_CONNECTION);
            } catch (IntentSender.SendIntentException e) {
                // Unable to resolve, message user appropriately
            }
        } else {
            GooglePlayServicesUtil.getErrorDialog(connectionResult.getErrorCode(), this, 0).show();
        }
    }

    @Override
    public void onConnectionSuspended(int cause) {
        Log.e(TAG, "GoogleApiClient connection suspended");
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        switch (requestCode) {
            case REQUEST_CODE_RESOLVE_CONNECTION:
                if (resultCode == RESULT_OK) {
                    mGoogleApiClient.connect();
                }
                break;
            case (REQUEST_CODE_OPENER):
                if (resultCode == RESULT_OK) {
                    mFileDriveId = data.getParcelableExtra(OpenFileActivityBuilder.EXTRA_RESPONSE_DRIVE_ID);
                    openDriveFile();
                } else
                    super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_start:
                prepareCurrentWord();
                pronounceTheWord();
                currentIndex++;
                break;
            case R.id.btn_last_one:
                if(currentIndex > 0){
                    currentIndex--;
                    prepareCurrentWord();
                }
                break;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if(tts != null){
            tts.stop();
            tts.shutdown();
        }

        if (mGoogleApiClient != null) {
            if (mGoogleApiClient.isConnected())
                mGoogleApiClient.disconnect();
        }

    }
}
