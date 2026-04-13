package com.travellbudy.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.travellbudy.app.databinding.ActivityUserProfileBinding;
import com.travellbudy.app.dialogs.ProfileReviewsBottomSheet;
import com.travellbudy.app.models.Trip;
import com.travellbudy.app.models.User;
import com.travellbudy.app.ui.trip.UserAdventuresAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class UserProfileActivity extends AppCompatActivity {

    public static final String EXTRA_USER_ID = "user_id";

    private ActivityUserProfileBinding binding;
    private String userId;
    private User currentUser;
    private DatabaseReference userRef;
    private ValueEventListener userListener;
    private UserAdventuresAdapter adventuresAdapter;
    private final List<Trip> userTrips = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityUserProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        userId = getIntent().getStringExtra(EXTRA_USER_ID);
        if (userId == null) {
            finish();
            return;
        }

        setupViews();
        loadUserProfile();
        loadUserTrips();
    }

    private void setupViews() {
        // Back button
        binding.btnBack.setOnClickListener(v -> finish());

        // Message button
        binding.btnMessage.setOnClickListener(v -> startDirectMessage());

        // See all button
        binding.btnSeeAll.setOnClickListener(v -> {
            // Could navigate to a full adventures list
            Toast.makeText(this, "Coming soon", Toast.LENGTH_SHORT).show();
        });

        // Rating stat - open reviews bottom sheet
        binding.layoutRatingStat.setOnClickListener(v -> openReviewsBottomSheet());

        // Setup RecyclerView for recent adventures
        adventuresAdapter = new UserAdventuresAdapter(userTrips, trip -> {
            Intent intent = new Intent(this, TripDetailsActivity.class);
            intent.putExtra(TripDetailsActivity.EXTRA_TRIP_ID, trip.tripId);
            startActivity(intent);
        });
        binding.rvRecentAdventures.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        binding.rvRecentAdventures.setAdapter(adventuresAdapter);
    }

    private void loadUserProfile() {
        userRef = FirebaseDatabase.getInstance().getReference("users").child(userId);
        userListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                currentUser = snapshot.getValue(User.class);
                if (currentUser != null) {
                    updateUI();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(UserProfileActivity.this, R.string.error_generic, Toast.LENGTH_SHORT).show();
            }
        };
        userRef.addValueEventListener(userListener);
    }

    private void updateUI() {
        // Header name
        String displayName = getDisplayName();
        binding.tvHeaderName.setText(displayName);
        binding.tvDisplayName.setText(displayName);

        // Profile photo
        if (currentUser.photoUrl != null && !currentUser.photoUrl.isEmpty()) {
            Glide.with(this)
                    .load(currentUser.photoUrl)
                    .placeholder(R.drawable.bg_profile_placeholder)
                    .into(binding.ivProfilePhoto);
        }


        // Bio
        if (currentUser.bio != null && !currentUser.bio.isEmpty()) {
            binding.tvBio.setText(currentUser.bio);
            binding.tvBio.setVisibility(View.VISIBLE);
        } else {
            binding.tvBio.setVisibility(View.GONE);
        }

        // Rating
        if (currentUser.ratingSummary != null && currentUser.ratingSummary.totalRatings > 0) {
            binding.tvRating.setText(String.format(Locale.getDefault(), "%.1f", 
                    currentUser.ratingSummary.averageRating));
            binding.tvRatingLabel.setText(String.format(Locale.getDefault(), 
                    "RATING (%d)", currentUser.ratingSummary.totalRatings));
        } else {
            binding.tvRating.setText("—");
            binding.tvRatingLabel.setText("RATING");
        }

        // Trip counters - load dynamically from Firebase
        loadTripCounters();

        // Hide message button if viewing own profile
        FirebaseUser me = FirebaseAuth.getInstance().getCurrentUser();
        if (me != null && me.getUid().equals(userId)) {
            binding.btnMessage.setVisibility(View.GONE);
        }

        // Load interests (if available in the future, for now use placeholder logic)
        setupInterests();
    }

    private String getDisplayName() {
        if (currentUser.displayName != null && !currentUser.displayName.isEmpty()) {
            // Abbreviate last name like "Lucas M."
            String[] parts = currentUser.displayName.split(" ");
            if (parts.length >= 2) {
                return parts[0] + " " + parts[parts.length - 1].charAt(0) + ".";
            }
            return currentUser.displayName;
        }
        return "User";
    }

    private void setupInterests() {
        // For now, show some placeholder interests
        // In a real app, you would load these from the user's profile
        binding.interestsSection.setVisibility(View.VISIBLE);
        binding.interestsRow1.removeAllViews();
        binding.interestsRow2.removeAllViews();

        String[] row1Interests = {"Skiing", "Mountaineering", "Road Trips"};
        String[] row2Interests = {"Photography", "Hostels"};
        
        for (String interest : row1Interests) {
            addInterestChip(interest, binding.interestsRow1);
        }
        for (String interest : row2Interests) {
            addInterestChip(interest, binding.interestsRow2);
        }
    }

    private void addInterestChip(String interest, LinearLayout container) {
        TextView chip = (TextView) LayoutInflater.from(this)
                .inflate(R.layout.item_interest_chip, container, false);
        chip.setText(interest);
        
        // Add margins
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 8, 0);
        chip.setLayoutParams(params);
        
        container.addView(chip);
    }

    private void loadUserTrips() {
        FirebaseDatabase.getInstance().getReference("trips")
                .orderByChild("driverUid")
                .equalTo(userId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        userTrips.clear();
                        for (DataSnapshot child : snapshot.getChildren()) {
                            Trip trip = child.getValue(Trip.class);
                            if (trip != null) {
                                userTrips.add(trip);
                            }
                        }
                        
                        if (!userTrips.isEmpty()) {
                            binding.recentAdventuresSection.setVisibility(View.VISIBLE);
                            adventuresAdapter.notifyDataSetChanged();
                        } else {
                            binding.recentAdventuresSection.setVisibility(View.GONE);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        binding.recentAdventuresSection.setVisibility(View.GONE);
                    }
                });
    }

    /**
     * Load trip counters by querying actual trip data from Firebase.
     * Uses date-based automatic status detection:
     * - Hosted = trips where driverUid equals the user (excluding canceled)
     * - Joined = trips where user has an approved request (excluding canceled trips)
     */
    private void loadTripCounters() {
        DatabaseReference tripsRef = FirebaseDatabase.getInstance().getReference("trips");
        DatabaseReference tripRequestsRef = FirebaseDatabase.getInstance().getReference("tripRequests");

        // Load hosted trips count using date-based status
        tripsRef.orderByChild("driverUid").equalTo(userId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        int hostedCount = 0;
                        for (DataSnapshot tripSnapshot : snapshot.getChildren()) {
                            Trip trip = tripSnapshot.getValue(Trip.class);
                            if (trip != null && trip.isCountable()) {
                                hostedCount++;
                            }
                        }
                        binding.tvHosted.setText(String.valueOf(hostedCount));
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        binding.tvHosted.setText("0");
                    }
                });

        // Load joined trips count - iterate through all trips and check requests for each
        tripsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot tripsSnapshot) {
                if (!tripsSnapshot.exists() || tripsSnapshot.getChildrenCount() == 0) {
                    binding.tvJoined.setText("0");
                    return;
                }
                
                final int[] joinedCount = {0};
                final int[] checkedCount = {0};
                final long totalTrips = tripsSnapshot.getChildrenCount();
                
                for (DataSnapshot tripSnapshot : tripsSnapshot.getChildren()) {
                    Trip trip = tripSnapshot.getValue(Trip.class);
                    String tripId = tripSnapshot.getKey();
                    
                    // Skip canceled trips (using date-based status) and trips the user is hosting
                    if (trip == null || !trip.isCountable() || userId.equals(trip.driverUid)) {
                        checkedCount[0]++;
                        if (checkedCount[0] >= totalTrips) {
                            binding.tvJoined.setText(String.valueOf(joinedCount[0]));
                        }
                        continue;
                    }
                    
                    // Check if user has approved or pending request for this trip
                    tripRequestsRef.child(tripId).orderByChild("riderUid").equalTo(userId)
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot requestsSnapshot) {
                                    checkedCount[0]++;
                                    
                                    for (DataSnapshot requestSnapshot : requestsSnapshot.getChildren()) {
                                        String status = requestSnapshot.child("status").getValue(String.class);
                                        // Count both approved AND pending requests
                                        if ("approved".equals(status) || "pending".equals(status)) {
                                            joinedCount[0]++;
                                            break;
                                        }
                                    }
                                    
                                    if (checkedCount[0] >= totalTrips) {
                                        binding.tvJoined.setText(String.valueOf(joinedCount[0]));
                                    }
                                }

                                @Override
                                public void onCancelled(@NonNull DatabaseError error) {
                                    checkedCount[0]++;
                                    if (checkedCount[0] >= totalTrips) {
                                        binding.tvJoined.setText(String.valueOf(joinedCount[0]));
                                    }
                                }
                            });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                binding.tvJoined.setText("0");
            }
        });
    }

    private void startDirectMessage() {
        if (currentUser == null) return;

        FirebaseUser me = FirebaseAuth.getInstance().getCurrentUser();
        if (me == null) return;

        // Create a unique conversation ID based on both user IDs (sorted to be consistent)
        String myUid = me.getUid();
        String conversationId = myUid.compareTo(userId) < 0 
                ? "dm_" + myUid + "_" + userId 
                : "dm_" + userId + "_" + myUid;

        // Get the display name for the chat title
        String chatTitle = currentUser.displayName != null ? currentUser.displayName : "Chat";

        // Navigate to chat using the trip_id as conversation ID
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra(ChatActivity.EXTRA_TRIP_ID, conversationId);
        intent.putExtra(ChatActivity.EXTRA_TRIP_ROUTE, chatTitle);
        startActivity(intent);
    }

    /**
     * Opens the Profile Reviews bottom sheet showing all reviews for this user.
     */
    private void openReviewsBottomSheet() {
        if (userId == null) return;
        
        String displayName = currentUser != null && currentUser.displayName != null 
                ? currentUser.displayName : "User";
        
        ProfileReviewsBottomSheet bottomSheet = ProfileReviewsBottomSheet.newInstance(
                userId, displayName);
        bottomSheet.show(getSupportFragmentManager(), "ProfileReviewsBottomSheet");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (userListener != null && userRef != null) {
            userRef.removeEventListener(userListener);
        }
    }
}


