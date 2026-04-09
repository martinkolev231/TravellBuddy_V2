package com.travellbudy.app.firebase;

import android.app.PendingIntent;
import android.content.Intent;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.travellbudy.app.R;
import com.travellbudy.app.SplashActivity;
import com.travellbudy.app.util.Constants;

import java.util.Map;

public class TravellBuddyFirebaseMessagingService extends FirebaseMessagingService {

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        writeTokenForCurrentUser(token);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        super.onMessageReceived(message);

        String title = getString(R.string.app_name);
        String body = getString(R.string.msg_notification_fallback);
        String type = "";
        String tripId = "";
        String chatId = "";

        if (message.getNotification() != null) {
            if (!TextUtils.isEmpty(message.getNotification().getTitle())) {
                title = message.getNotification().getTitle();
            }
            if (!TextUtils.isEmpty(message.getNotification().getBody())) {
                body = message.getNotification().getBody();
            }
        }

        Map<String, String> data = message.getData();
        if (data != null && !data.isEmpty()) {
            if (!TextUtils.isEmpty(data.get("title"))) {
                title = data.get("title");
            }
            if (!TextUtils.isEmpty(data.get("body"))) {
                body = data.get("body");
            }
            type = safe(data.get("type"));
            tripId = safe(data.get("tripId"));
            chatId = safe(data.get("chatId"));
        }

        showNotification(title, body, type, tripId, chatId);
    }

    private void showNotification(@NonNull String title,
                                  @NonNull String body,
                                  @NonNull String type,
                                  @NonNull String tripId,
                                  @NonNull String chatId) {
        Intent launchIntent = new Intent(this, SplashActivity.class);
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (!TextUtils.isEmpty(type)) {
            launchIntent.putExtra(Constants.EXTRA_NOTIFICATION_TYPE, type);
        }
        if (!TextUtils.isEmpty(tripId)) {
            launchIntent.putExtra(Constants.EXTRA_TRIP_ID, tripId);
        }
        if (!TextUtils.isEmpty(chatId)) {
            launchIntent.putExtra(Constants.EXTRA_CHAT_ID, chatId);
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                (int) System.currentTimeMillis(),
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, Constants.NOTIF_CHANNEL_ID_MAIN)
                .setSmallIcon(R.drawable.ic_chat)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        NotificationManagerCompat.from(this)
                .notify((int) (System.currentTimeMillis() & 0xfffffff), builder.build());
    }

    private void writeTokenForCurrentUser(@NonNull String token) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            return;
        }

        FirebaseDatabase.getInstance().getReference(Constants.PATH_USERS)
                .child(user.getUid())
                .child(Constants.FIELD_FCM_TOKEN)
                .setValue(token);
    }

    @NonNull
    private String safe(String value) {
        return value == null ? "" : value;
    }
}

