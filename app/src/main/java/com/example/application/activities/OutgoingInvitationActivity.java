package com.example.application.activities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.application.R;
import com.example.application.models.User;
import com.example.application.network.ApiClient;
import com.example.application.network.ApiService;
import com.example.application.utilities.Constants;
import com.example.application.utilities.PreferenceManager;
import com.google.firebase.iid.FirebaseInstanceId;

import org.jitsi.meet.sdk.JitsiMeetActivity;
import org.jitsi.meet.sdk.JitsiMeetConferenceOptions;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URL;
import java.util.UUID;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class OutgoingInvitationActivity extends AppCompatActivity {

    private PreferenceManager preferenceManager;
    private String inviterToken = null;
    private String meetingRoom = null;
    private String meetingType=null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_outgoing_invitation);
        preferenceManager = new PreferenceManager(getApplicationContext());



        ImageView imageMeetingType = findViewById(R.id.imageMeetingType);
         meetingType = getIntent().getStringExtra("type");

        if (meetingType != null) {
            if (meetingType.equals("video")) {
                imageMeetingType.setImageResource(R.drawable.ic_videoajdf);
            }else {
                imageMeetingType.setImageResource(R.drawable.ic_audio);
            }
        }


        TextView textFirstChar = findViewById(R.id.textFirstChar);
        TextView textUsername = findViewById(R.id.textUsername);
        TextView textEmail = findViewById(R.id.textEmail);
        User user = (User) getIntent().getSerializableExtra("user");
        if (user != null) {
            textFirstChar.setText(user.name.substring(0, 1));
            textUsername.setText(String.format("%s", user.name));
            textEmail.setText(user.email);

        }
        ImageView imageStopInvitation = findViewById(R.id.imageStopInvitation);
        imageStopInvitation.setOnClickListener(v -> {
            if (user !=null){
                cancelInvitation(user.token);
            }
        });

        FirebaseInstanceId.getInstance().getInstanceId().addOnCompleteListener(task ->{
            if (task.isSuccessful() && task.getResult()!=null){
                inviterToken=task.getResult().getToken();
                if (meetingType != null && user != null) {
                    initiateMeeting(meetingType, user.token);
                }
            }
        });


    }
    private void initiateMeeting(String meetingType, String receiverToken) {
        try {
            JSONArray tokens = new JSONArray();
            tokens.put(receiverToken);

            JSONObject body = new JSONObject();
            JSONObject data = new JSONObject();

            data.put(Constants.REMOTE_MSG_TYPE, Constants.REMOTE_MSG_INVITATION);
            data.put(Constants.REMOTE_MSG_MEETING_TYPE, meetingType);
            data.put(Constants.KEY_NAME, preferenceManager.getString(Constants.KEY_NAME));
            data.put(Constants.KEY_EMAIL, preferenceManager.getString(Constants.KEY_EMAIL));
            data.put(Constants.REMOTE_MSG_INVITER_TOKEN, inviterToken);

            meetingRoom = preferenceManager
                    .getString(Constants.KEY_USER_ID) + "_" + UUID.randomUUID().toString().substring(0, 5);
            data.put(Constants.REMOTE_MSG_MEETING_ROOM, meetingRoom);

            body.put(Constants.REMOTE_MSG_DATA, data);
            body.put(Constants.REMOTE_MSG_REGISTRATION_IDS, tokens);

            sendRemoteMessage(body.toString(), Constants.REMOTE_MSG_INVITATION);
        } catch (Exception exception) {
            Toast.makeText(this, exception.getMessage(), Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void cancelInvitation(String receiverToken) {
        try {
            JSONArray tokens = new JSONArray();
            tokens.put(receiverToken);

            JSONObject body = new JSONObject();
            JSONObject data = new JSONObject();

            data.put(Constants.REMOTE_MSG_TYPE, Constants.REMOTE_MSG_INVITATION_RESPONSE);
            data.put(Constants.REMOTE_MSG_INVITATION_RESPONSE, Constants.REMOTE_MSG_INVITATION_CANCELLED);

            body.put(Constants.REMOTE_MSG_DATA, data);
            body.put(Constants.REMOTE_MSG_REGISTRATION_IDS, tokens);

            sendRemoteMessage(body.toString(), Constants.REMOTE_MSG_INVITATION_RESPONSE);
        } catch (Exception exception) {
            Toast.makeText(OutgoingInvitationActivity.this, exception.getMessage(), Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void sendRemoteMessage(String remoteMessageBody,String type){
        ApiClient.getClient().create(ApiService.class).sendRemoteMessage(
                Constants.getRemoteMsgHeaders(),remoteMessageBody
        ).enqueue(new Callback<String>() {
            @Override
            public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                if (response.isSuccessful()) {
                    if (type.equals(Constants.REMOTE_MSG_INVITATION)) {
                        Toast.makeText(OutgoingInvitationActivity.this, "Invitation sent successfully", Toast.LENGTH_SHORT).show();
                    } else if (type.equals(Constants.REMOTE_MSG_INVITATION_RESPONSE)) {
                        Toast.makeText(OutgoingInvitationActivity.this, "Invitation cancelled", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<String> call,@NonNull Throwable t) {

                Toast.makeText(OutgoingInvitationActivity.this, t.getMessage(), Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private final BroadcastReceiver invitationResponseReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String type = intent.getStringExtra(Constants.REMOTE_MSG_INVITATION_RESPONSE);
            if (type != null) {
                if (type.equals(Constants.REMOTE_MSG_INVITATION_ACCEPTED)) {
                    try {
                        URL serverURL = new URL("https://meet.jit.si");

                        JitsiMeetConferenceOptions.Builder builder=new JitsiMeetConferenceOptions.Builder();
                        builder.setServerURL(serverURL);
                        builder.setWelcomePageEnabled(false);
                        builder.setRoom(meetingRoom);
                        if (meetingType.equals("audio")){
                            builder.setAudioMuted(true);
                        }
                        JitsiMeetActivity.launch(OutgoingInvitationActivity.this, builder.build());
                        finish();
                    } catch (Exception exception) {
                        Toast.makeText(context, exception.getMessage(), Toast.LENGTH_SHORT).show();
                        finish();
                    }
                }
            }
            }
    };

    @Override
    protected void onStart() {
        super.onStart();
        LocalBroadcastManager.getInstance(getApplicationContext())
                .registerReceiver(invitationResponseReceiver,
                        new IntentFilter(Constants.REMOTE_MSG_INVITATION_RESPONSE));
    }

    @Override
    protected void onStop() {
        super.onStop();
        LocalBroadcastManager.getInstance(getApplicationContext())
                .unregisterReceiver(invitationResponseReceiver);
    }
}
