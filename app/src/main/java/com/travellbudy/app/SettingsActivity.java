package com.travellbudy.app;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.credentials.ClearCredentialStateRequest;
import androidx.credentials.CredentialManager;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.travellbudy.app.databinding.ActivitySettingsBinding;
import com.travellbudy.app.models.SeatRequest;
import com.travellbudy.app.models.Trip;

public class SettingsActivity extends AppCompatActivity {

    private ActivitySettingsBinding binding;
    private CredentialManager credentialManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        credentialManager = CredentialManager.create(this);

        // Back button
        binding.btnBack.setOnClickListener(v -> finish());

        // Version
        binding.tvVersion.setText("TravellBuddy v1.0.0");

        // Personal Information
        binding.btnPersonalInfo.setOnClickListener(v -> {
            Intent intent = new Intent(this, EditProfileActivity.class);
            startActivity(intent);
        });

        // Password & Security
        binding.btnPasswordSecurity.setOnClickListener(v -> changePassword());

        // Currency
        binding.btnCurrency.setOnClickListener(v -> {
            Toast.makeText(this, "Currency settings coming soon", Toast.LENGTH_SHORT).show();
        });

        // Help Center
        binding.btnHelpCenter.setOnClickListener(v -> {
            Toast.makeText(this, "Help Center coming soon", Toast.LENGTH_SHORT).show();
        });

        // Terms of Service
        binding.btnTermsOfService.setOnClickListener(v -> {
            Toast.makeText(this, "Terms of Service coming soon", Toast.LENGTH_SHORT).show();
        });

        // Privacy Policy
        binding.btnPrivacyPolicy.setOnClickListener(v -> {
            Toast.makeText(this, "Privacy Policy coming soon", Toast.LENGTH_SHORT).show();
        });

        // Log out
        binding.btnLogOut.setOnClickListener(v -> showLogoutDialog());

        // Delete account
        binding.btnDeleteAccount.setOnClickListener(v -> showDeleteAccountDialog());
    }

    private void changePassword() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && user.getEmail() != null) {
            FirebaseAuth.getInstance().sendPasswordResetEmail(user.getEmail())
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Toast.makeText(this, R.string.msg_password_reset_sent, Toast.LENGTH_SHORT).show();
                        }
                    });
        }
    }

    private void showLogoutDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.TransparentDialog);
        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_logout, null);
        builder.setView(dialogView);
        
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        
        dialogView.findViewById(R.id.btnConfirmLogout).setOnClickListener(v -> {
            dialog.dismiss();
            performLogout();
        });
        
        dialogView.findViewById(R.id.btnCancelLogout).setOnClickListener(v -> dialog.dismiss());
        
        dialog.show();
    }

    private void showDeleteAccountDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.TransparentDialog);
        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_delete_account, null);
        builder.setView(dialogView);
        
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        
        dialogView.findViewById(R.id.btnConfirmDelete).setOnClickListener(v -> {
            dialog.dismiss();
            deleteAccount();
        });
        
        dialogView.findViewById(R.id.btnCancelDelete).setOnClickListener(v -> dialog.dismiss());
        
        dialog.show();
    }

    private void performLogout() {
        // Clear Credential Manager state (clears cached Google credential)
        credentialManager.clearCredentialStateAsync(
                new ClearCredentialStateRequest(),
                new android.os.CancellationSignal(),
                Runnable::run,
                new androidx.credentials.CredentialManagerCallback<Void, androidx.credentials.exceptions.ClearCredentialException>() {
                    @Override
                    public void onResult(Void unused) {
                        // Credential state cleared
                    }

                    @Override
                    public void onError(@NonNull androidx.credentials.exceptions.ClearCredentialException e) {
                        // Non-fatal — proceed with sign-out anyway
                    }
                }
        );

        FirebaseAuth.getInstance().signOut();
        Intent intent = new Intent(SettingsActivity.this, WelcomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void resendVerificationEmail() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Please sign in first", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d("SettingsActivity", "Attempting to send verification email to: " + user.getEmail());
        Log.d("SettingsActivity", "User UID: " + user.getUid());
        Log.d("SettingsActivity", "Email verified: " + user.isEmailVerified());

        // Reload user to get latest email verification status
        user.reload().addOnCompleteListener(reloadTask -> {
            if (!reloadTask.isSuccessful()) {
                Log.e("SettingsActivity", "Failed to reload user", reloadTask.getException());
                Toast.makeText(this, "Failed to check verification status", Toast.LENGTH_SHORT).show();
                return;
            }

            FirebaseUser refreshedUser = FirebaseAuth.getInstance().getCurrentUser();
            if (refreshedUser == null) {
                Log.e("SettingsActivity", "User is null after reload");
                return;
            }

            Log.d("SettingsActivity", "After reload - Email verified: " + refreshedUser.isEmailVerified());

            if (refreshedUser.isEmailVerified()) {
                Toast.makeText(this, "Your email is already verified!", Toast.LENGTH_SHORT).show();
                return;
            }

            Log.d("SettingsActivity", "Sending verification email...");
            
            refreshedUser.sendEmailVerification()
                    .addOnSuccessListener(aVoid -> {
                        Log.d("SettingsActivity", "Verification email sent successfully!");
                        Toast.makeText(this, 
                                "Verification email sent to " + refreshedUser.getEmail() + 
                                ". Please check your inbox and spam folder.", 
                                Toast.LENGTH_LONG).show();
                    })
                    .addOnFailureListener(e -> {
                        Log.e("SettingsActivity", "Failed to send verification email", e);
                        Log.e("SettingsActivity", "Error class: " + e.getClass().getName());
                        Log.e("SettingsActivity", "Error message: " + e.getMessage());
                        Toast.makeText(this, 
                                "Failed to send email: " + e.getMessage(), 
                                Toast.LENGTH_LONG).show();
                    });
        });
    }

    private void deleteAccount() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        String uid = user.getUid();

        // First, clean up user's active trips (edge case: user deletes account with active trips)
        FirebaseDatabase.getInstance().getReference("trips")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        for (DataSnapshot tripSnap : snapshot.getChildren()) {
                            Trip trip = tripSnap.getValue(Trip.class);
                            if (trip == null) continue;

                            // If user is the driver, cancel the trip
                            if (uid.equals(trip.driverUid) && !"canceled".equals(trip.status)
                                    && !"completed".equals(trip.status)) {
                                tripSnap.getRef().child("status").setValue("canceled");
                                // Cancel all requests via tripRequests node
                                FirebaseDatabase.getInstance().getReference("tripRequests")
                                        .child(trip.tripId)
                                        .addListenerForSingleValueEvent(new ValueEventListener() {
                                            @Override
                                            public void onDataChange(@NonNull DataSnapshot reqSnapshot) {
                                                for (DataSnapshot reqSnap : reqSnapshot.getChildren()) {
                                                    SeatRequest req = reqSnap.getValue(SeatRequest.class);
                                                    if (req != null && ("approved".equals(req.status) || "pending".equals(req.status))) {
                                                        reqSnap.getRef().child("status").setValue("canceled_by_rider");
                                                    }
                                                }
                                            }

                                            @Override
                                            public void onCancelled(@NonNull DatabaseError error) {
                                            }
                                        });
                            }

                            // If user is a rider, cancel their requests via tripRequests node
                            FirebaseDatabase.getInstance().getReference("tripRequests")
                                    .child(trip.tripId)
                                    .orderByChild("riderUid")
                                    .equalTo(uid)
                                    .addListenerForSingleValueEvent(new ValueEventListener() {
                                        @Override
                                        public void onDataChange(@NonNull DataSnapshot reqSnapshot) {
                                            for (DataSnapshot reqSnap : reqSnapshot.getChildren()) {
                                                SeatRequest req = reqSnap.getValue(SeatRequest.class);
                                                if (req != null && ("approved".equals(req.status) || "pending".equals(req.status))) {
                                                    reqSnap.getRef().child("status").setValue("canceled_by_rider");
                                                }
                                            }
                                        }

                                        @Override
                                        public void onCancelled(@NonNull DatabaseError error) {
                                        }
                                    });
                        }

                        // Now remove user data and delete account
                        FirebaseDatabase.getInstance().getReference("users").child(uid).removeValue();

                        user.delete().addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                Intent intent = new Intent(SettingsActivity.this, WelcomeActivity.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(intent);
                                finish();
                            } else {
                                Toast.makeText(SettingsActivity.this, R.string.error_generic,
                                        Toast.LENGTH_SHORT).show();
                            }
                        });
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(SettingsActivity.this, R.string.error_generic,
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }
}

