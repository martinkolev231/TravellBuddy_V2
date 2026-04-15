package com.travellbudy.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
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
        if (shouldShowOnboarding()) {
            startActivity(new Intent(this, OnboardingActivity.class));
            finish();
            return;
        }

        FirebaseUser currentUser = authRepository.getCurrentUser();
        if (currentUser == null) {
            // Not logged in - go to auth
            startActivity(new Intent(this, AuthActivity.class));
            finish();
            return;
        }

        // User is logged in - check if admin
        checkAdminStatusAndNavigate(currentUser.getUid());
    }

    private void checkAdminStatusAndNavigate(String userId) {
        FirebaseDatabase.getInstance().getReference("users")
            .child(userId)
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    Intent intent;
                    
                    // Check if user is admin by role field (value "admin")
                    String role = snapshot.child("role").getValue(String.class);
                    boolean isAdmin = "admin".equals(role);
                    
                    // Also check if user is banned
                    Boolean isBanned = snapshot.child("isBanned").getValue(Boolean.class);
                    
                    if (isBanned != null && isBanned) {
                        // User is banned - sign out and go to welcome
                        authRepository.signOut();
                        intent = new Intent(SplashActivity.this, WelcomeActivity.class);
                    } else if (isAdmin) {
                        // Admin user - go to admin panel
                        intent = new Intent(SplashActivity.this, AdminPanelActivity.class);
                    } else {
                        // Check admins whitelist as fallback
                        checkAdminsWhitelistAndNavigate(userId);
                        return;
                    }
                    
                    startActivity(intent);
                    finish();
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    // Error reading user data - proceed to home as fallback
                    startActivity(new Intent(SplashActivity.this, HomeActivity.class));
                    finish();
                }
            });
    }
    
    private void checkAdminsWhitelistAndNavigate(String userId) {
        FirebaseDatabase.getInstance().getReference("admins")
            .child(userId)
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    Boolean isInWhitelist = snapshot.getValue(Boolean.class);
                    Intent intent;
                    
                    if (isInWhitelist != null && isInWhitelist) {
                        // User is in admins whitelist - go to admin panel
                        intent = new Intent(SplashActivity.this, AdminPanelActivity.class);
                    } else {
                        // Regular user - go to home
                        intent = new Intent(SplashActivity.this, HomeActivity.class);
                    }
                    
                    startActivity(intent);
                    finish();
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    // Error - proceed to home as fallback
                    startActivity(new Intent(SplashActivity.this, HomeActivity.class));
                    finish();
                }
            });
    }

    private boolean shouldShowOnboarding() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return !prefs.getBoolean(KEY_ONBOARDING_DONE, false);
    }
}
