package com.travellbudy.app;

import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.travellbudy.app.databinding.ActivityEditProfileBinding;
import com.travellbudy.app.models.User;

import java.util.HashMap;
import java.util.Map;

public class EditProfileActivity extends AppCompatActivity {

    private ActivityEditProfileBinding binding;
    private DatabaseReference userRef;
    private StorageReference storageRef;
    private String currentUserId;

    // Photo picker launcher
    private final ActivityResultLauncher<PickVisualMediaRequest> photoPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) {
                    uploadProfilePhoto(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEditProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Back button
        binding.btnBack.setOnClickListener(v -> finish());

        // Change photo click listeners
        binding.btnChangePhoto.setOnClickListener(v -> openPhotoPicker());
        binding.tvChangePhoto.setOnClickListener(v -> openPhotoPicker());

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            finish();
            return;
        }

        currentUserId = currentUser.getUid();
        userRef = FirebaseDatabase.getInstance().getReference("users").child(currentUserId);
        storageRef = FirebaseStorage.getInstance().getReference();

        loadProfile();

        binding.btnSave.setOnClickListener(v -> saveProfile());
    }

    private void openPhotoPicker() {
        photoPickerLauncher.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
    }

    private void uploadProfilePhoto(Uri imageUri) {
        // Show progress
        binding.progressBar.setVisibility(View.VISIBLE);
        Toast.makeText(this, R.string.uploading_photo, Toast.LENGTH_SHORT).show();

        // Create storage reference for profile photo
        StorageReference photoRef = storageRef.child("profile_photos/" + currentUserId + ".jpg");

        photoRef.putFile(imageUri)
                .addOnSuccessListener(taskSnapshot -> {
                    // Get download URL
                    photoRef.getDownloadUrl().addOnSuccessListener(downloadUri -> {
                        String photoUrl = downloadUri.toString();

                        // Update database with new photo URL
                        userRef.child("photoUrl").setValue(photoUrl)
                                .addOnSuccessListener(aVoid -> {
                                    binding.progressBar.setVisibility(View.GONE);

                                    // Update UI with new photo
                                    Glide.with(EditProfileActivity.this)
                                            .load(photoUrl)
                                            .placeholder(R.drawable.bg_profile_placeholder)
                                            .into(binding.ivPhoto);

                                    // Also update Firebase Auth profile photo
                                    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                                    if (user != null) {
                                        user.updateProfile(new UserProfileChangeRequest.Builder()
                                                .setPhotoUri(downloadUri)
                                                .build());
                                    }

                                    // Update photo in all trips where user is the driver
                                    updatePhotoInTrips(photoUrl);

                                    Toast.makeText(this, R.string.photo_updated, Toast.LENGTH_SHORT).show();
                                })
                                .addOnFailureListener(e -> {
                                    binding.progressBar.setVisibility(View.GONE);
                                    Toast.makeText(this, R.string.error_photo_update_failed, Toast.LENGTH_SHORT).show();
                                });
                    });
                })
                .addOnFailureListener(e -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, R.string.error_photo_upload_failed, Toast.LENGTH_SHORT).show();
                });
    }

    /**
     * Update the driver photo URL in all trips where the user is the driver
     */
    private void updatePhotoInTrips(String newPhotoUrl) {
        FirebaseDatabase.getInstance().getReference("trips")
                .orderByChild("driverUid")
                .equalTo(currentUserId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        for (DataSnapshot tripSnapshot : snapshot.getChildren()) {
                            tripSnapshot.getRef().child("driverPhotoUrl").setValue(newPhotoUrl);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        // Silent fail - main photo update already succeeded
                    }
                });
    }

    private void loadProfile() {
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                User user = snapshot.getValue(User.class);
                if (user != null) {
                    binding.etName.setText(user.displayName);
                    binding.etBio.setText(user.bio);

                    // Load profile photo if available
                    if (user.photoUrl != null && !user.photoUrl.isEmpty()) {
                        Glide.with(EditProfileActivity.this)
                                .load(user.photoUrl)
                                .placeholder(R.drawable.bg_profile_placeholder)
                                .into(binding.ivPhoto);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }

    private void saveProfile() {
        String name = binding.etName.getText().toString().trim();
        String bio = binding.etBio.getText().toString().trim();

        if (TextUtils.isEmpty(name)) {
            Toast.makeText(this, R.string.error_empty_name, Toast.LENGTH_SHORT).show();
            return;
        }

        binding.progressBar.setVisibility(View.VISIBLE);
        binding.btnSave.setEnabled(false);

        Map<String, Object> updates = new HashMap<>();
        updates.put("displayName", name);
        updates.put("bio", bio);

        userRef.updateChildren(updates).addOnCompleteListener(task -> {
            binding.progressBar.setVisibility(View.GONE);
            binding.btnSave.setEnabled(true);

            if (task.isSuccessful()) {
                // Also update Firebase Auth profile
                FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                if (user != null) {
                    user.updateProfile(new UserProfileChangeRequest.Builder()
                            .setDisplayName(name)
                            .build());
                }
                Toast.makeText(this, R.string.success_profile_updated, Toast.LENGTH_SHORT).show();
                finish();
            } else {
                Toast.makeText(this, R.string.error_profile_update_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
