package com.travellbudy.app;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;


import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.travellbudy.app.databinding.ActivitySettingsBinding;
import com.travellbudy.app.models.SeatRequest;
import com.travellbudy.app.models.Trip;
import com.travellbudy.app.utils.TestDataHelper;

public class SettingsActivity extends AppCompatActivity {

    private ActivitySettingsBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());


        // Check if user is admin and show admin section
        checkAdminStatus();

        // Back button
        binding.btnBack.setOnClickListener(v -> finish());

        // Personal Information
        binding.btnPersonalInfo.setOnClickListener(v -> {
            Intent intent = new Intent(this, EditProfileActivity.class);
            startActivity(intent);
        });

        // Password & Security
        binding.btnPasswordSecurity.setOnClickListener(v -> changePassword());

        // Admin Portal
        binding.btnAdminPortal.setOnClickListener(v -> {
            Toast.makeText(this, "Admin Portal coming soon", Toast.LENGTH_SHORT).show();
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

        // Log Out
        binding.btnLogOut.setOnClickListener(v -> showLogoutDialog());

        // Delete account
        binding.btnDeleteAccount.setOnClickListener(v -> showDeleteAccountDialog());

        // DEBUG: Create test trips (REMOVE BEFORE PRODUCTION)
        binding.btnCreateTestTrips.setOnClickListener(v -> {
            TestDataHelper.createTestTrips();
            Toast.makeText(this, 
                "Creating test trips...\n" +
                "• 3 HOSTED (ongoing + 2 completed)\n" +
                "• 2 JOINED (ongoing in 1 min + upcoming)\n" +
                "Check Logcat for details.", 
                Toast.LENGTH_LONG).show();
        });
    }

    private void checkAdminStatus() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        FirebaseDatabase.getInstance().getReference("users")
                .child(user.getUid())
                .child("isAdmin")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        Boolean isAdmin = snapshot.getValue(Boolean.class);
                        if (isAdmin != null && isAdmin) {
                            binding.tvAdminSectionHeader.setVisibility(View.VISIBLE);
                            binding.adminCard.setVisibility(View.VISIBLE);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        // Silently ignore - admin section stays hidden
                    }
                });
    }

    private void changePassword() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && user.getEmail() != null) {
            FirebaseAuth.getInstance().sendPasswordResetEmail(user.getEmail())
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Toast.makeText(this, R.string.msg_password_reset_sent, Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show();
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

    private void performLogout() {
        try {
            FirebaseAuth.getInstance().signOut();
        } catch (Exception e) {
            // Ignore sign out errors
        }
        
        try {
            Intent intent = new Intent(SettingsActivity.this, WelcomeActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        } catch (Exception e) {
            // If navigation fails, just finish the activity
            finish();
        }
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

    private void deleteAccount() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        String uid = user.getUid();
        FirebaseDatabase database = FirebaseDatabase.getInstance();

        // Clean up all user-related data before deleting the account
        database.getReference("trips")
                .orderByChild("driverUid")
                .equalTo(uid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        // Delete all trips created by this user and their associated data
                        for (DataSnapshot tripSnap : snapshot.getChildren()) {
                            Trip trip = tripSnap.getValue(Trip.class);
                            if (trip == null) continue;

                            String tripId = trip.tripId != null ? trip.tripId : tripSnap.getKey();
                            
                            // Delete trip requests for this trip
                            database.getReference("tripRequests").child(tripId).removeValue();
                            
                            // Delete trip members for this trip
                            database.getReference("tripMembers").child(tripId).removeValue();
                            
                            // Delete ratings for this trip
                            database.getReference("ratings").child(tripId).removeValue();
                            
                            // Delete the group chat for this trip (chatId is "trip_" + tripId)
                            String groupChatId = "trip_" + tripId;
                            database.getReference("chats").child(groupChatId).removeValue();
                            
                            // Delete the trip itself
                            tripSnap.getRef().removeValue();
                        }

                        // Cancel user's seat requests on other trips
                        cleanupUserRequests(uid, database, () -> {
                            // Remove user from userChats
                            database.getReference("userChats").child(uid).removeValue();
                            
                            // Remove user's notifications
                            database.getReference("notifications").child(uid).removeValue();
                            
                            // Remove user data
                            database.getReference("users").child(uid).removeValue();

                            // Finally delete the Firebase Auth account
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
                        });
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(SettingsActivity.this, R.string.error_generic,
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }
    
    /**
     * Cancels all seat requests made by the user on other people's trips.
     */
    private void cleanupUserRequests(String uid, FirebaseDatabase database, Runnable onComplete) {
        database.getReference("tripRequests")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        for (DataSnapshot tripRequestsSnap : snapshot.getChildren()) {
                            for (DataSnapshot reqSnap : tripRequestsSnap.getChildren()) {
                                SeatRequest req = reqSnap.getValue(SeatRequest.class);
                                if (req != null && uid.equals(req.riderUid)) {
                                    // Remove user's request
                                    reqSnap.getRef().removeValue();
                                }
                            }
                        }
                        onComplete.run();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        // Continue even if cleanup fails
                        onComplete.run();
                    }
                });
    }
}

