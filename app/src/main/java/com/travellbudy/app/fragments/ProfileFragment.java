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
import com.travellbudy.app.dialogs.ProfileReviewsBottomSheet;
import com.travellbudy.app.models.User;

import java.util.Locale;

public class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;
    private ValueEventListener userListener;
    private DatabaseReference userRef;
    private StorageReference storageRef;
    private boolean isUploadingPhoto = false;
    private boolean isUploadingCover = false;
    
    // Trip counter listeners
    private DatabaseReference tripsRef;
    private DatabaseReference tripRequestsRef;
    private ValueEventListener hostedTripsListener;
    private ValueEventListener joinedTripsListener;
    private String currentUserId;
    private String currentUserName = "User";

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

        currentUserId = currentUser.getUid();
        userRef = FirebaseDatabase.getInstance().getReference("users").child(currentUserId);
        storageRef = FirebaseStorage.getInstance().getReference();
        tripsRef = FirebaseDatabase.getInstance().getReference("trips");
        tripRequestsRef = FirebaseDatabase.getInstance().getReference("tripRequests");

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

        // Rating stat - open reviews bottom sheet
        binding.layoutRatingStat.setOnClickListener(v -> {
            openReviewsBottomSheet();
        });

        loadProfile();
        loadTripCounters();
    }

    private void loadProfile() {
        userListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (binding == null) return;

                User user = snapshot.getValue(User.class);
                if (user == null) return;

                // Save display name for reviews bottom sheet
                currentUserName = user.displayName != null ? user.displayName : "User";

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

    /**
     * Load trip counters by querying actual trip data from Firebase.
     * - Hosted = trips where driverUid equals current user (excluding canceled)
     * - Joined = trips where user has an approved request (excluding canceled trips)
     */
    private void loadTripCounters() {
        if (currentUserId == null) {
            android.util.Log.e("ProfileFragment", "currentUserId is null, cannot load counters");
            return;
        }
        
        android.util.Log.d("ProfileFragment", "Loading trip counters for user: " + currentUserId);

        // Load hosted trips count (trips created by this user, excluding canceled)
        hostedTripsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (binding == null) return;
                
                android.util.Log.d("ProfileFragment", "Hosted trips snapshot received, count: " + snapshot.getChildrenCount());
                
                int hostedCount = 0;
                for (DataSnapshot tripSnapshot : snapshot.getChildren()) {
                    com.travellbudy.app.models.Trip trip = tripSnapshot.getValue(com.travellbudy.app.models.Trip.class);
                    if (trip != null) {
                        android.util.Log.d("ProfileFragment", "Trip: " + trip.tripId + ", effectiveStatus: " + trip.getEffectiveStatus());
                        // Count only non-canceled trips using date-based status
                        if (trip.isCountable()) {
                            hostedCount++;
                        }
                    }
                }
                android.util.Log.d("ProfileFragment", "Final hosted count: " + hostedCount);
                binding.tvHostedCount.setText(String.valueOf(hostedCount));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                android.util.Log.e("ProfileFragment", "Hosted trips query cancelled: " + error.getMessage());
            }
        };
        tripsRef.orderByChild("driverUid").equalTo(currentUserId)
                .addValueEventListener(hostedTripsListener);

        // Load joined trips count (trips where user has approved request, excluding canceled trips)
        loadJoinedTripsCount();
    }

    private void loadJoinedTripsCount() {
        android.util.Log.d("ProfileFragment", "Loading joined trips count for user: " + currentUserId);
        
        // Use single-value listener to avoid flickering from real-time updates
        // Count will refresh when profile is resumed
        tripRequestsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot allRequestsSnapshot) {
                if (binding == null) return;
                
                android.util.Log.d("ProfileFragment", "Trip requests snapshot received");
                
                // Collect all trip IDs where user has an approved request
                final java.util.List<String> approvedTripIds = new java.util.ArrayList<>();
                
                for (DataSnapshot tripRequestsSnapshot : allRequestsSnapshot.getChildren()) {
                    String tripId = tripRequestsSnapshot.getKey();
                    
                    for (DataSnapshot requestSnapshot : tripRequestsSnapshot.getChildren()) {
                        String riderUid = requestSnapshot.child("riderUid").getValue(String.class);
                        String status = requestSnapshot.child("status").getValue(String.class);
                        
                        if (currentUserId.equals(riderUid) && "approved".equals(status)) {
                            approvedTripIds.add(tripId);
                            android.util.Log.d("ProfileFragment", "Found approved request for trip: " + tripId);
                            break;
                        }
                    }
                }
                
                if (approvedTripIds.isEmpty()) {
                    if (binding != null) {
                        binding.tvJoinedCount.setText("0");
                    }
                    return;
                }
                
                // Now check which of these trips are countable (not canceled)
                final int[] validCount = {0};
                final int[] checkedCount = {0};
                final int totalToCheck = approvedTripIds.size();
                
                for (String tripId : approvedTripIds) {
                    tripsRef.child(tripId).addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot tripSnapshot) {
                            checkedCount[0]++;
                            
                            com.travellbudy.app.models.Trip trip = tripSnapshot.getValue(com.travellbudy.app.models.Trip.class);
                            if (trip != null && trip.isCountable()) {
                                validCount[0]++;
                            }
                            
                            if (checkedCount[0] >= totalToCheck && binding != null) {
                                android.util.Log.d("ProfileFragment", "Final joined count: " + validCount[0]);
                                binding.tvJoinedCount.setText(String.valueOf(validCount[0]));
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            checkedCount[0]++;
                            if (checkedCount[0] >= totalToCheck && binding != null) {
                                binding.tvJoinedCount.setText(String.valueOf(validCount[0]));
                            }
                        }
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                android.util.Log.e("ProfileFragment", "Trip requests query cancelled: " + error.getMessage());
                if (binding != null) {
                    binding.tvJoinedCount.setText("0");
                }
            }
        });
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

    /**
     * Opens the Profile Reviews bottom sheet showing all reviews for this user.
     */
    private void openReviewsBottomSheet() {
        if (currentUserId == null) return;
        
        ProfileReviewsBottomSheet bottomSheet = ProfileReviewsBottomSheet.newInstance(
                currentUserId, currentUserName);
        bottomSheet.show(getParentFragmentManager(), "ProfileReviewsBottomSheet");
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
        // Check if user is still logged in before reattaching listeners
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null && userRef != null && userListener != null) {
            userRef.addValueEventListener(userListener);
        }
        // Reload trip counters on resume
        if (currentUser != null && tripsRef != null) {
            loadTripCounters();
        }
    }
    
    @Override
    public void onPause() {
        super.onPause();
        // Remove listeners when fragment is paused to prevent callbacks during logout
        if (userListener != null && userRef != null) {
            userRef.removeEventListener(userListener);
        }
        // Remove trip counter listeners
        removeTripCounterListeners();
    }
    
    private void removeTripCounterListeners() {
        if (hostedTripsListener != null && tripsRef != null) {
            tripsRef.orderByChild("driverUid").equalTo(currentUserId)
                    .removeEventListener(hostedTripsListener);
        }
        // joinedTripsListener uses single-value listeners now, no need to remove
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Ensure all listeners are removed
        if (userListener != null && userRef != null) {
            userRef.removeEventListener(userListener);
            userListener = null;
        }
        removeTripCounterListeners();
        hostedTripsListener = null;
        joinedTripsListener = null;
        userRef = null;
        tripsRef = null;
        tripRequestsRef = null;
        binding = null;
    }
}

