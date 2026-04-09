package com.travellbudy.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;

import com.google.firebase.auth.FirebaseUser;
import com.travellbudy.app.databinding.ActivitySplashBinding;
import com.travellbudy.app.repository.AuthRepository;

public class SplashActivity extends AppCompatActivity {

    private static final long SPLASH_DELAY_MS = 1200L;
    private static final String PREFS_NAME = "travellbuddy_prefs";
    private static final String KEY_ONBOARDING_DONE = "onboarding_done";

    // Repository-only Firebase access from UI layer.
    private AuthRepository authRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Install the splash screen - must be before super.onCreate()
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);

        super.onCreate(savedInstanceState);
        ActivitySplashBinding binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        authRepository = new AuthRepository(getApplication());

        new Handler(Looper.getMainLooper()).postDelayed(this::navigateNext, SPLASH_DELAY_MS);
    }

    private void navigateNext() {
        Intent intent;
        if (shouldShowOnboarding()) {
            intent = new Intent(this, OnboardingActivity.class);
        } else {
            FirebaseUser currentUser = authRepository.getCurrentUser();
            intent = currentUser != null
                    ? new Intent(this, HomeActivity.class)
                    : new Intent(this, AuthActivity.class);
        }

        startActivity(intent);
        finish();
    }

    private boolean shouldShowOnboarding() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return !prefs.getBoolean(KEY_ONBOARDING_DONE, false);
    }
}
