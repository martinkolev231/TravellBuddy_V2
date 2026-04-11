package com.travellbudy.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.messaging.FirebaseMessaging;
import com.travellbudy.app.databinding.ActivityHomeBinding;
import com.travellbudy.app.firebase.FirebaseManager;
import com.travellbudy.app.util.Constants;

public class HomeActivity extends AppCompatActivity {

    private ActivityHomeBinding binding;
    private NavController navController;
    private Snackbar offlineSnackbar;
    private DatabaseReference connectedRef;
    private ValueEventListener connectedListener;

    private final ActivityResultLauncher<String> notificationsPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                // No-op: app can still function without notifications; user can enable later.
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityHomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Check auth on create only - don't use AuthStateListener to avoid conflicts with logout flow
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            startActivity(new Intent(this, WelcomeActivity.class));
            finish();
            return;
        }

        initializeApp();
    }

    private void initializeApp() {
        maybeRequestNotificationPermission();
        syncFcmToken();

        // Set up Navigation Component
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.navHostFragment);
        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();
            // Wire BottomNavigationView to NavController
            NavigationUI.setupWithNavController(binding.bottomNavigation, navController);
        }

        // Apply window insets to bottom navigation for proper system bar handling
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNavigation, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(view.getPaddingLeft(), view.getPaddingTop(), 
                    view.getPaddingRight(), insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        // Monitor connectivity
        setupConnectivityListener();
    }

    private void maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        notificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    private void syncFcmToken() {
        String uid = FirebaseManager.getInstance().getCurrentUid();
        if (uid == null) {
            return;
        }
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token -> {
            FirebaseManager.getInstance().getUserRef(uid)
                    .child(Constants.FIELD_FCM_TOKEN)
                    .setValue(token);
        });
    }

    private void setupConnectivityListener() {
        connectedRef = FirebaseManager.getInstance().getConnectedRef();
        connectedListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // Check if activity is still valid
                if (isFinishing() || isDestroyed() || binding == null) return;
                
                try {
                    Boolean connected = snapshot.getValue(Boolean.class);
                    if (connected != null && !connected) {
                        if (offlineSnackbar == null || !offlineSnackbar.isShown()) {
                            offlineSnackbar = Snackbar.make(binding.getRoot(),
                                    R.string.msg_offline, Snackbar.LENGTH_INDEFINITE);
                            offlineSnackbar.show();
                        }
                    } else {
                        if (offlineSnackbar != null && offlineSnackbar.isShown()) {
                            offlineSnackbar.dismiss();
                            Snackbar.make(binding.getRoot(),
                                    R.string.msg_back_online, Snackbar.LENGTH_SHORT).show();
                        }
                    }
                } catch (Exception e) {
                    // Ignore UI errors during activity lifecycle changes
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        };
        connectedRef.addValueEventListener(connectedListener);
    }

    /**
     * Navigate to My Trips tab from anywhere in the app
     */
    public void navigateToMyTrips() {
        if (navController != null) {
            binding.bottomNavigation.setSelectedItemId(R.id.myTripsFragment);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        return navController != null && navController.navigateUp()
                || super.onSupportNavigateUp();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (connectedRef != null && connectedListener != null) {
            connectedRef.removeEventListener(connectedListener);
        }
    }
}
