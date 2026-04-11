package com.travellbudy.app.fragments;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

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
import com.travellbudy.app.EditProfileActivity;
import com.travellbudy.app.R;
import com.travellbudy.app.SettingsActivity;
import com.travellbudy.app.databinding.FragmentProfileBinding;
import com.travellbudy.app.models.User;

import java.util.Locale;

public class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;
    private ValueEventListener userListener;
    private DatabaseReference userRef;
    private StorageReference storageRef;
    private boolean isUploadingPhoto = false;
    private boolean isUploadingCover = false;

    private final ActivityResultLauncher<String> profilePhotoPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    uploadProfilePhoto(uri);
                }
            });

    private final ActivityResultLauncher<String> coverPhotoPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    uploadCoverPhoto(uri);
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;

        userRef = FirebaseDatabase.getInstance().getReference("users").child(currentUser.getUid());
        storageRef = FirebaseStorage.getInstance().getReference();

        // Edit Profile button
        binding.btnEditProfile.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), EditProfileActivity.class)));

        // Settings button - goes to settings screen
        binding.btnSettings.setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), SettingsActivity.class));
        });

        // Change profile photo
        binding.btnChangePhoto.setOnClickListener(v -> {
            if (!isUploadingPhoto) {
                profilePhotoPickerLauncher.launch("image/*");
            } else {
                Toast.makeText(requireContext(), R.string.upload_in_progress, Toast.LENGTH_SHORT).show();
            }
        });

        // Change cover photo
        binding.ivCoverPhoto.setOnClickListener(v -> {
            if (!isUploadingCover) {
                coverPhotoPickerLauncher.launch("image/*");
            } else {
                Toast.makeText(requireContext(), R.string.upload_in_progress, Toast.LENGTH_SHORT).show();
            }
        });

        // Add interest
        binding.btnAddInterest.setOnClickListener(v -> {
            Toast.makeText(requireContext(), "Add interests coming soon", Toast.LENGTH_SHORT).show();
        });

        // My Adventures - navigate to My Trips screen
        binding.btnMyAdventures.setOnClickListener(v -> {
            Navigation.findNavController(v).navigate(R.id.myTripsFragment);
        });

        loadProfile();
    }

    private void loadProfile() {
        userListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (binding == null) return;

                User user = snapshot.getValue(User.class);
                if (user == null) return;

                // Display name
                binding.tvDisplayName.setText(user.displayName != null ? user.displayName : "");

                // Bio / tagline
                if (user.bio != null && !user.bio.isEmpty()) {
                    binding.tvBio.setText(user.bio);
                    binding.tvBio.setVisibility(View.VISIBLE);
                } else {
                    binding.tvBio.setVisibility(View.GONE);
                }

                // Rating
                double avgRating = user.ratingSummary != null ? user.ratingSummary.averageRating : 0.0;
                binding.tvAvgRating.setText(String.format(Locale.getDefault(), "%.1f", avgRating));

                // Trip counters
                int joinedCount = 0;
                int hostedCount = 0;
                if (user.tripCounters != null) {
                    joinedCount = user.tripCounters.tripsAsRider;
                    hostedCount = user.tripCounters.tripsAsDriver;
                }
                binding.tvJoinedCount.setText(String.valueOf(joinedCount));
                binding.tvHostedCount.setText(String.valueOf(hostedCount));

                // Profile photo
                if (getContext() != null) {
                    if (user.photoUrl != null && !user.photoUrl.isEmpty()) {
                        Glide.with(requireContext())
                                .load(user.photoUrl)
                                .placeholder(R.drawable.bg_profile_placeholder)
                                .error(R.drawable.bg_profile_placeholder)
                                .circleCrop()
                                .into(binding.ivProfilePhoto);
                    } else {
                        binding.ivProfilePhoto.setImageResource(R.drawable.bg_profile_placeholder);
                    }
                }

                // Cover photo
                if (getContext() != null) {
                    if (user.coverPhotoUrl != null && !user.coverPhotoUrl.isEmpty()) {
                        Glide.with(requireContext())
                                .load(user.coverPhotoUrl)
                                .centerCrop()
                                .into(binding.ivCoverPhoto);
                    } else {
                        binding.ivCoverPhoto.setImageResource(R.drawable.placeholder_adventure_1);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        };
        userRef.addValueEventListener(userListener);
    }

    private void uploadProfilePhoto(Uri imageUri) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;

        isUploadingPhoto = true;
        Toast.makeText(requireContext(), R.string.uploading_photo, Toast.LENGTH_SHORT).show();

        // Show the selected image immediately
        if (binding != null && getContext() != null) {
            Glide.with(requireContext())
                    .load(imageUri)
                    .circleCrop()
                    .into(binding.ivProfilePhoto);
        }

        String fileName = "profile_photos/" + currentUser.getUid() + ".jpg";
        StorageReference photoRef = storageRef.child(fileName);

        photoRef.putFile(imageUri)
                .addOnSuccessListener(taskSnapshot -> {
                    photoRef.getDownloadUrl().addOnSuccessListener(downloadUri -> {
                        String newPhotoUrl = downloadUri.toString();

                        // Update database
                        userRef.child("photoUrl").setValue(newPhotoUrl)
                                .addOnSuccessListener(aVoid -> {
                                    // Update Firebase Auth profile
                                    currentUser.updateProfile(new UserProfileChangeRequest.Builder()
                                            .setPhotoUri(downloadUri)
                                            .build());

                                    // Update driverPhotoUrl in all trips where user is the driver
                                    updatePhotoInTrips(currentUser.getUid(), newPhotoUrl);

                                    isUploadingPhoto = false;
                                    if (getContext() != null) {
                                        Toast.makeText(requireContext(), R.string.photo_updated, Toast.LENGTH_SHORT).show();
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    isUploadingPhoto = false;
                                    if (getContext() != null) {
                                        Toast.makeText(requireContext(), R.string.error_photo_update_failed, Toast.LENGTH_SHORT).show();
                                    }
                                });
                    });
                })
                .addOnFailureListener(e -> {
                    isUploadingPhoto = false;
                    if (getContext() != null) {
                        Toast.makeText(requireContext(), R.string.error_photo_upload_failed, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void updatePhotoInTrips(String userId, String newPhotoUrl) {
        FirebaseDatabase.getInstance().getReference("trips")
                .orderByChild("driverUid")
                .equalTo(userId)
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

    private void uploadCoverPhoto(Uri imageUri) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;

        isUploadingCover = true;
        Toast.makeText(requireContext(), R.string.uploading_photo, Toast.LENGTH_SHORT).show();

        // Show the selected image immediately
        if (binding != null && getContext() != null) {
            Glide.with(requireContext())
                    .load(imageUri)
                    .centerCrop()
                    .into(binding.ivCoverPhoto);
        }

        String fileName = "cover_photos/" + currentUser.getUid() + ".jpg";
        StorageReference photoRef = storageRef.child(fileName);

        photoRef.putFile(imageUri)
                .addOnSuccessListener(taskSnapshot -> {
                    photoRef.getDownloadUrl().addOnSuccessListener(downloadUri -> {
                        // Update database with new cover photo URL
                        userRef.child("coverPhotoUrl").setValue(downloadUri.toString())
                                .addOnSuccessListener(aVoid -> {
                                    isUploadingCover = false;
                                    if (getContext() != null) {
                                        Toast.makeText(requireContext(), R.string.cover_photo_updated, Toast.LENGTH_SHORT).show();
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    isUploadingCover = false;
                                    if (getContext() != null) {
                                        Toast.makeText(requireContext(), R.string.error_photo_update_failed, Toast.LENGTH_SHORT).show();
                                    }
                                });
                    });
                })
                .addOnFailureListener(e -> {
                    isUploadingCover = false;
                    if (getContext() != null) {
                        Toast.makeText(requireContext(), R.string.error_photo_upload_failed, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    public void onResume() {
        super.onResume();
        // Check if user is still logged in before reattaching listener
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null && userRef != null && userListener != null) {
            userRef.addValueEventListener(userListener);
        }
    }
    
    @Override
    public void onPause() {
        super.onPause();
        // Remove listener when fragment is paused to prevent callbacks during logout
        if (userListener != null && userRef != null) {
            userRef.removeEventListener(userListener);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Ensure listener is removed
        if (userListener != null && userRef != null) {
            userRef.removeEventListener(userListener);
            userListener = null;
        }
        userRef = null;
        binding = null;
    }
}

