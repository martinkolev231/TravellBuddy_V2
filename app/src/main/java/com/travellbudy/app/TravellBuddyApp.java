package com.travellbudy.app;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;
import android.text.TextUtils;

import com.google.android.libraries.places.api.Places;
import com.google.firebase.FirebaseApp;
import com.google.firebase.database.FirebaseDatabase;
import com.travellbudy.app.util.Constants;

public class TravellBuddyApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        FirebaseApp.initializeApp(this);

        // Enable persistence before any DB reference is created.
        FirebaseDatabase.getInstance().setPersistenceEnabled(true);

        // Keep critical paths synced for offline usage.
        FirebaseDatabase.getInstance().getReference(Constants.PATH_TRIPS).keepSynced(true);
        FirebaseDatabase.getInstance().getReference(Constants.PATH_TRIP_REQUESTS).keepSynced(true);

        createNotificationChannel();
        initializePlacesSdk();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                Constants.NOTIF_CHANNEL_ID_MAIN,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription(getString(R.string.notification_channel_description));

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    private void initializePlacesSdk() {
        String apiKey = getString(R.string.google_maps_api_key);
        if (!Places.isInitialized() && !TextUtils.isEmpty(apiKey)
                && !"YOUR_GOOGLE_MAPS_API_KEY".equals(apiKey)) {
            Places.initialize(getApplicationContext(), apiKey);
        }
    }
}
