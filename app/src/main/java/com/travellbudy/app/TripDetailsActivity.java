package com.travellbudy.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.travellbudy.app.databinding.ActivityTripDetailsBinding;
import com.travellbudy.app.dialogs.RateUserDialogFragment;
import com.travellbudy.app.models.SeatRequest;
import com.travellbudy.app.models.Trip;
import com.travellbudy.app.models.User;
import com.travellbudy.app.repository.RatingRepository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class TripDetailsActivity extends AppCompatActivity {

    public static final String EXTRA_TRIP_ID = "trip_id";
    public static final String RESULT_TRIP_DELETED = "trip_deleted";
    public static final int RESULT_CODE_DELETED = 100;

    private ActivityTripDetailsBinding binding;
    private DatabaseReference tripRef;
    private DatabaseReference requestsRef;
    private String tripId;
    private Trip currentTrip;
    private String currentUserId;
    private ValueEventListener tripListener;
    private ValueEventListener requestListener;
    private String existingRequestId;
    private String existingRequestStatus;
    private RatingRepository ratingRepository;
    private boolean canRateTrip = false;
    private String hostUidToRate;
    private String hostNameToRate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTripDetailsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        tripId = getIntent().getStringExtra(EXTRA_TRIP_ID);
        if (tripId == null) {
            finish();
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            finish();
            return;
        }
        currentUserId = user.getUid();

        tripRef = FirebaseDatabase.getInstance().getReference("trips").child(tripId);
        requestsRef = FirebaseDatabase.getInstance().getReference("tripRequests").child(tripId);
        ratingRepository = new RatingRepository(getApplication());

        setupListeners();
        setupButtons();
        checkRatingEligibility();
    }

    private void setupListeners() {
        tripListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                currentTrip = snapshot.getValue(Trip.class);
                if (currentTrip != null) {
                    updateUI();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(TripDetailsActivity.this, R.string.error_generic, Toast.LENGTH_SHORT).show();
            }
        };
        tripRef.addValueEventListener(tripListener);

        // Listen for current user's request
        requestListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                existingRequestId = null;
                existingRequestStatus = null;
                for (DataSnapshot child : snapshot.getChildren()) {
                    SeatRequest req = child.getValue(SeatRequest.class);
                    if (req != null && currentUserId.equals(req.riderUid)) {
                        existingRequestId = req.requestId;
                        existingRequestStatus = req.status;
                        break;
                    }
                }
                updateButtonStates();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        };
        requestsRef.addValueEventListener(requestListener);
    }

    private void updateUI() {
        // Cover photo
        Glide.with(this)
                .load(currentTrip.imageUrl)
                .centerCrop()
                .into(binding.ivCoverPhoto);

        // Activity Type badge
        String activityType = formatActivityType(currentTrip.activityType);
        binding.tvActivityType.setText(activityType.toUpperCase());

        // Check if user is host and if trip is upcoming
        boolean isHost = currentUserId != null && currentUserId.equals(currentTrip.driverUid);
        boolean isUpcoming = currentTrip.isUpcoming();
        boolean isUpcomingHosted = isHost && isUpcoming;

        android.util.Log.d("TripDetails", "currentUserId: " + currentUserId + ", driverUid: " + currentTrip.driverUid + ", isHost: " + isHost + ", isUpcoming: " + isUpcoming);

        // Show HOSTED badge for host's trips, show spots left for non-hosts
        if (isHost) {
            binding.tvHostedBadge.setVisibility(View.VISIBLE);
            binding.tvSpotsLeft.setVisibility(View.GONE);
            // Show delete button in top overlay for hosts
            binding.btnDeleteTop.setVisibility(View.VISIBLE);
        } else {
            binding.tvHostedBadge.setVisibility(View.GONE);
            binding.btnDeleteTop.setVisibility(View.GONE);
            // Spots left badge for non-hosts
            int spotsLeft = currentTrip.availableSeats;
            if (spotsLeft == 1) {
                binding.tvSpotsLeft.setText("1 SPOT LEFT");
            } else {
                binding.tvSpotsLeft.setText(spotsLeft + " SPOTS LEFT");
            }
            binding.tvSpotsLeft.setVisibility(spotsLeft > 0 ? View.VISIBLE : View.GONE);
        }

        // Title - use carModel as title, fallback to destination
        String title = currentTrip.carModel != null && !currentTrip.carModel.isEmpty() 
                ? currentTrip.carModel 
                : currentTrip.destinationCity;
        binding.tvTitle.setText(title);

        // Location
        binding.tvLocation.setText(currentTrip.destinationCity);

        // Dates - Display in compact format like "Apr 28 - 30" (same month) or "Apr 28 - May 2"
        if (currentTrip.departureTime > 0 && currentTrip.estimatedArrivalTime > 0) {
            DateTimeFormatter monthDayFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);
            DateTimeFormatter dayOnlyFormatter = DateTimeFormatter.ofPattern("d", Locale.ENGLISH);
            LocalDateTime startDate = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(currentTrip.departureTime), ZoneId.systemDefault());
            LocalDateTime endDate = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(currentTrip.estimatedArrivalTime), ZoneId.systemDefault());
            
            String dateRange;
            if (startDate.getMonth() == endDate.getMonth() && startDate.getYear() == endDate.getYear()) {
                // Same month: "Apr 28 - 30"
                dateRange = startDate.format(monthDayFormatter) + " - " + endDate.format(dayOnlyFormatter);
            } else {
                // Different months: "Apr 28 - May 2"
                dateRange = startDate.format(monthDayFormatter) + " - " + endDate.format(monthDayFormatter);
            }
            binding.tvDates.setText(dateRange);
        } else {
            binding.tvDates.setText("Dates TBD");
        }

        // Budget - Display as €amount format
        if (currentTrip.pricePerSeat > 0) {
            binding.tvBudget.setText(String.format(Locale.getDefault(), "€%.0f", currentTrip.pricePerSeat));
        } else {
            binding.tvBudget.setText("Free");
        }

        // Description
        String description = currentTrip.description != null && !currentTrip.description.isEmpty()
                ? currentTrip.description
                : "No description provided.";
        binding.tvDescription.setText(description);

        // Host info - Show "(You)" if current user is the host
        if (isHost) {
            binding.tvHostName.setText(currentTrip.driverName + " (You)");
        } else {
            binding.tvHostName.setText(currentTrip.driverName);
        }
        
        // Set background color for the host avatar
        binding.ivHostPhoto.setCircleBackgroundColor(getColor(R.color.surface_variant));
        
        if (currentTrip.driverPhotoUrl != null && !currentTrip.driverPhotoUrl.isEmpty()) {
            Glide.with(this)
                    .load(currentTrip.driverPhotoUrl)
                    .placeholder(R.drawable.ic_person)
                    .error(R.drawable.ic_person)
                    .into(binding.ivHostPhoto);
        } else {
            binding.ivHostPhoto.setImageResource(R.drawable.ic_person);
        }

        // Host rating (placeholder for now)
        binding.tvHostRating.setText("—");
        
        // Fetch actual host rating
        fetchHostRating(currentTrip.driverUid);
        
        // Make host card clickable to view profile
        View.OnClickListener hostProfileClickListener = v -> {
            if (currentTrip != null && currentTrip.driverUid != null) {
                Intent intent = new Intent(TripDetailsActivity.this, UserProfileActivity.class);
                intent.putExtra(UserProfileActivity.EXTRA_USER_ID, currentTrip.driverUid);
                startActivity(intent);
            }
        };
        binding.ivHostPhoto.setOnClickListener(hostProfileClickListener);
        binding.tvHostName.setOnClickListener(hostProfileClickListener);
        binding.hostCard.setOnClickListener(hostProfileClickListener);
        
        // Fetch actual trips hosted count
        fetchHostTripsCount(currentTrip.driverUid);

        // Participants
        int totalParticipants = currentTrip.totalSeats - currentTrip.availableSeats;
        binding.tvParticipantsTitle.setText(String.format("Participants (%d/%d)", totalParticipants, currentTrip.totalSeats));

        // Load participant avatars
        loadParticipantAvatars();

        // For upcoming hosted trips: hide bottom buttons entirely, use top delete button
        if (isUpcomingHosted) {
            binding.bottomButtonsContainer.setVisibility(View.GONE);
        } else if (isHost) {
            // For non-upcoming hosted trips, show bottom delete button
            binding.bottomButtonsContainer.setVisibility(View.VISIBLE);
            binding.btnDeleteAdventure.setVisibility(View.VISIBLE);
            binding.btnJoinActivity.setVisibility(View.GONE);
        } else {
            // For non-hosts, show join button
            binding.bottomButtonsContainer.setVisibility(View.VISIBLE);
            binding.btnDeleteAdventure.setVisibility(View.GONE);
            binding.btnJoinActivity.setVisibility(View.VISIBLE);
        }

        // Status
        if ("canceled".equals(currentTrip.status)) {
            binding.tvStatus.setVisibility(View.VISIBLE);
            binding.tvStatus.setText(R.string.label_trip_cancelled);
            binding.tvStatus.setBackgroundResource(R.drawable.bg_status_banner_cancelled);
            binding.tvStatus.setTextColor(getColor(R.color.status_cancelled_text));
        } else if (currentTrip.isFull()) {
            binding.tvStatus.setVisibility(View.VISIBLE);
            binding.tvStatus.setText(R.string.label_trip_full);
            binding.tvStatus.setBackgroundResource(R.drawable.bg_status_banner_pending);
            binding.tvStatus.setTextColor(getColor(R.color.status_pending_text));
        }

        updateButtonStates();
    }

    private void loadParticipantAvatars() {
        if (currentTrip == null || tripId == null) return;

        // Clear existing avatars
        binding.participantsContainer.removeAllViews();

        android.util.Log.d("TripDetails", "Loading participant avatars. Host UID: " + currentTrip.driverUid);

        // First fetch the host's current photo from users node (more reliable than trip snapshot)
        fetchAndAddParticipantAvatar(currentTrip.driverUid);

        // Then load approved participants from trip requests
        FirebaseDatabase.getInstance().getReference("tripRequests").child(tripId)
                .orderByChild("status").equalTo("approved")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        android.util.Log.d("TripDetails", "Found " + snapshot.getChildrenCount() + " approved requests");
                        for (DataSnapshot child : snapshot.getChildren()) {
                            SeatRequest request = child.getValue(SeatRequest.class);
                            if (request != null && request.riderUid != null) {
                                // Fetch rider photo from users node
                                fetchAndAddParticipantAvatar(request.riderUid);
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        android.util.Log.e("TripDetails", "Error loading trip requests: " + error.getMessage());
                    }
                });
    }

    private void fetchAndAddParticipantAvatar(String userId) {
        // First try to get photo from users database
        FirebaseDatabase.getInstance().getReference("users").child(userId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String photoUrl = null;
                        
                        // Try different photo field names
                        if (snapshot.hasChild("photoUrl")) {
                            photoUrl = snapshot.child("photoUrl").getValue(String.class);
                        }
                        if ((photoUrl == null || photoUrl.isEmpty()) && snapshot.hasChild("photoURL")) {
                            photoUrl = snapshot.child("photoURL").getValue(String.class);
                        }
                        if ((photoUrl == null || photoUrl.isEmpty()) && snapshot.hasChild("profilePhotoUrl")) {
                            photoUrl = snapshot.child("profilePhotoUrl").getValue(String.class);
                        }
                        
                        android.util.Log.d("TripDetails", "Fetched user " + userId + " - photoUrl: " + photoUrl);
                        
                        // If still no photo and this is the current user, try Firebase Auth
                        if ((photoUrl == null || photoUrl.isEmpty()) && userId.equals(currentUserId)) {
                            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                            if (currentUser != null && currentUser.getPhotoUrl() != null) {
                                photoUrl = currentUser.getPhotoUrl().toString();
                                android.util.Log.d("TripDetails", "Using Firebase Auth photo for current user: " + photoUrl);
                            }
                        }
                        
                        addParticipantAvatar(photoUrl, userId);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        android.util.Log.e("TripDetails", "Error fetching user " + userId + ": " + error.getMessage());
                        addParticipantAvatar(null, userId);
                    }
                });
    }

    private void addParticipantAvatar(String photoUrl, String userId) {
        // Use CircleImageView for perfect circles
        de.hdodenhof.circleimageview.CircleImageView avatar = 
                new de.hdodenhof.circleimageview.CircleImageView(this);
        float density = getResources().getDisplayMetrics().density;
        int size = (int) (48 * density);
        android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(size, size);
        
        // Add slight overlap between avatars (except first one)
        int childCount = binding.participantsContainer.getChildCount();
        if (childCount > 0) {
            params.leftMargin = (int) (-10 * density);
        }
        avatar.setLayoutParams(params);
        
        // White border
        avatar.setBorderWidth((int) (2 * density));
        avatar.setBorderColor(getColor(R.color.white));
        
        // Gray background for placeholder
        avatar.setCircleBackgroundColor(getColor(R.color.surface_variant));
        
        // Set elevation for proper stacking
        avatar.setElevation((childCount + 1) * density);

        android.util.Log.d("TripDetails", "Loading avatar for user: " + userId + ", photoUrl: " + photoUrl);

        if (photoUrl != null && !photoUrl.isEmpty()) {
            Glide.with(this)
                    .load(photoUrl)
                    .placeholder(R.drawable.ic_person)
                    .error(R.drawable.ic_person)
                    .into(avatar);
        } else {
            avatar.setImageResource(R.drawable.ic_person);
        }

        // Make avatar clickable to view profile
        avatar.setOnClickListener(v -> {
            if (userId != null) {
                Intent intent = new Intent(TripDetailsActivity.this, UserProfileActivity.class);
                intent.putExtra(UserProfileActivity.EXTRA_USER_ID, userId);
                startActivity(intent);
            }
        });

        binding.participantsContainer.addView(avatar);
    }

    private String formatActivityType(String type) {
        if (type == null) return "Adventure";
        switch (type) {
            case "road_trip": return "Road Trip";
            case "hiking": return "Hiking";
            case "beach": return "Beach";
            case "city_break": return "City Break";
            case "camping": return "Camping";
            case "festival": return "Festival";
            case "city_explore": return "City Explore";
            case "photography": return "Photography";
            case "outdoor_sports": return "Outdoor Sports";
            case "backpacking": return "Backpacking";
            case "weekend": return "Weekend";
            default: return type.substring(0, 1).toUpperCase() + type.substring(1).replace("_", " ");
        }
    }

    private String getBudgetText(double price) {
        if (price <= 0) return "Free";
        if (price <= 100) return "$ Budget";
        if (price <= 500) return "$$ Moderate";
        return "$$$ Premium";
    }


    private void updateButtonStates() {
        if (currentTrip == null) return;

        boolean isDriver = currentUserId.equals(currentTrip.driverUid);
        boolean isCancelled = "canceled".equals(currentTrip.status);

        // Update Join button based on state
        if (isDriver || isCancelled) {
            binding.btnJoinActivity.setVisibility(View.GONE);
        } else if (existingRequestId != null) {
            // User already has a request
            switch (existingRequestStatus != null ? existingRequestStatus : "") {
                case "pending":
                    binding.btnJoinActivity.setText(R.string.label_request_pending);
                    binding.btnJoinActivity.setEnabled(false);
                    binding.btnJoinActivity.setVisibility(View.VISIBLE);
                    break;
                case "approved":
                    binding.btnJoinActivity.setText(R.string.label_request_approved);
                    binding.btnJoinActivity.setEnabled(false);
                    binding.btnJoinActivity.setVisibility(View.VISIBLE);
                    break;
                case "denied":
                    binding.btnJoinActivity.setText(R.string.label_request_denied);
                    binding.btnJoinActivity.setEnabled(false);
                    binding.btnJoinActivity.setVisibility(View.VISIBLE);
                    break;
                default:
                    binding.btnJoinActivity.setVisibility(View.GONE);
                    break;
            }
        } else {
            // No existing request
            int spotsLeft = currentTrip.availableSeats;
            String buttonText = getString(R.string.btn_join_activity) + "  (" + spotsLeft + " spots left)";
            binding.btnJoinActivity.setText(buttonText);
            binding.btnJoinActivity.setEnabled(!currentTrip.isFull());
            binding.btnJoinActivity.setVisibility(View.VISIBLE);
        }
    }

    private void setupButtons() {
        // Back button
        binding.btnBack.setOnClickListener(v -> finish());

        // Share button
        binding.btnShare.setOnClickListener(v -> shareTrip());

        // Delete button (top overlay - for hosts)
        binding.btnDeleteTop.setOnClickListener(v -> showDeleteConfirmation());

        // Join Activity button
        binding.btnJoinActivity.setOnClickListener(v -> sendSeatRequest());

        // Delete Adventure button (legacy bottom button)
        binding.btnDeleteAdventure.setOnClickListener(v -> showDeleteConfirmation());

        // Rate Host button
        binding.btnRateHost.setOnClickListener(v -> showRateHostDialog());
    }

    /**
     * Checks if the current user is eligible to rate the host of this trip.
     * Updates the Rate Host button visibility accordingly.
     */
    private void checkRatingEligibility() {
        if (tripId == null || currentUserId == null) return;

        ratingRepository.canUserRateTrip(tripId, currentUserId).observe(this, result -> {
            if (result.isSuccess() && result.data != null) {
                RatingRepository.CanRateResult canRateResult = result.data;
                canRateTrip = canRateResult.canRate;
                hostUidToRate = canRateResult.hostUid;
                hostNameToRate = canRateResult.hostName;
                updateRateButtonVisibility();
            } else {
                canRateTrip = false;
                updateRateButtonVisibility();
            }
        });
    }

    /**
     * Updates the visibility of the Rate Host button based on eligibility.
     */
    private void updateRateButtonVisibility() {
        if (binding == null) return;
        binding.btnRateHost.setVisibility(canRateTrip ? View.VISIBLE : View.GONE);
    }

    /**
     * Shows the rate host dialog.
     */
    private void showRateHostDialog() {
        if (!canRateTrip || hostUidToRate == null) {
            Toast.makeText(this, R.string.error_cannot_rate, Toast.LENGTH_SHORT).show();
            return;
        }

        RateUserDialogFragment dialog = RateUserDialogFragment.newInstance(
                hostUidToRate,
                hostNameToRate != null ? hostNameToRate : "Host",
                tripId
        );
        dialog.setOnRatingSubmittedListener(() -> {
            // Refresh the rating eligibility check after submission
            canRateTrip = false;
            updateRateButtonVisibility();
        });
        dialog.show(getSupportFragmentManager(), "RateUserDialog");
    }

    private void showDeleteConfirmation() {
        android.util.Log.d("TripDetails", "showDeleteConfirmation called");
        
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this, R.style.TransparentDialog);
        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_delete_adventure, null);
        builder.setView(dialogView);
        
        androidx.appcompat.app.AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        
        dialogView.findViewById(R.id.btnConfirmDelete).setOnClickListener(v -> {
            dialog.dismiss();
            deleteAdventure();
        });
        
        dialogView.findViewById(R.id.btnCancelDelete).setOnClickListener(v -> dialog.dismiss());
        
        dialog.show();
    }

    private void deleteAdventure() {
        if (tripId == null) return;
        
        android.util.Log.d("TripDetails", "Deleting trip: " + tripId);
        
        // Remove the listener first to avoid callbacks during deletion
        if (tripListener != null) {
            tripRef.removeEventListener(tripListener);
        }
        
        // Delete trip requests first, then delete the trip
        DatabaseReference tripRequestsRef = FirebaseDatabase.getInstance().getReference("tripRequests").child(tripId);
        tripRequestsRef.removeValue().addOnCompleteListener(task1 -> {
            // Now delete the trip itself
            tripRef.removeValue()
                    .addOnSuccessListener(aVoid -> {
                        android.util.Log.d("TripDetails", "Trip deleted successfully: " + tripId);
                        Toast.makeText(this, "Adventure deleted", Toast.LENGTH_SHORT).show();
                        
                        // Set result so calling activity knows trip was deleted
                        Intent resultIntent = new Intent();
                        resultIntent.putExtra(RESULT_TRIP_DELETED, tripId);
                        setResult(RESULT_CODE_DELETED, resultIntent);
                        
                        // Force sync by going online (in case offline cache issue)
                        FirebaseDatabase.getInstance().goOnline();
                        
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        android.util.Log.e("TripDetails", "Failed to delete trip: " + tripId, e);
                        Toast.makeText(this, "Failed to delete: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
        });
    }

    private void sendSeatRequest() {
        if (existingRequestId != null) {
            Toast.makeText(this, R.string.error_already_requested, Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        String requestId = requestsRef.push().getKey();
        if (requestId == null) return;

        String riderName = user.getDisplayName() != null ? user.getDisplayName() : "Rider";
        String riderPhotoUrl = user.getPhotoUrl() != null ? user.getPhotoUrl().toString() : null;
        SeatRequest request = new SeatRequest(requestId, tripId, currentUserId, riderName,
                currentTrip != null ? currentTrip.driverUid : "", "");

        requestsRef.child(requestId).setValue(request.toMap())
                .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, R.string.success_request_sent, Toast.LENGTH_SHORT).show();
                        // Send notification to trip host
                        sendJoinRequestNotification(riderName, riderPhotoUrl);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, R.string.error_request_failed, Toast.LENGTH_SHORT).show());
    }

    private void sendJoinRequestNotification(String requesterName, String requesterPhotoUrl) {
        if (currentTrip == null || currentTrip.driverUid == null) return;

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        DatabaseReference hostNotificationsRef = FirebaseDatabase.getInstance()
                .getReference("notifications")
                .child(currentTrip.driverUid);

        String notificationId = hostNotificationsRef.push().getKey();
        if (notificationId == null) return;

        // Get adventure name (from carModel which stores the title)
        String adventureName = currentTrip.carModel != null && !currentTrip.carModel.isEmpty()
                ? currentTrip.carModel
                : currentTrip.destinationCity;

        com.travellbudy.app.models.Notification notification = new com.travellbudy.app.models.Notification();
        notification.notificationId = notificationId;
        notification.userId = currentTrip.driverUid;
        notification.type = "join_request";
        notification.title = "New Join Request";
        notification.message = requesterName + " wants to join your " + adventureName + " trip.";
        notification.tripId = tripId;
        notification.tripName = adventureName;
        notification.fromUserId = user.getUid();
        notification.fromUserName = requesterName;
        notification.fromUserPhoto = requesterPhotoUrl;
        notification.createdAt = System.currentTimeMillis();
        notification.isRead = false;
        notification.status = "pending";

        hostNotificationsRef.child(notificationId).setValue(notification);
    }

    private void shareTrip() {
        if (currentTrip == null) return;
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.getDefault());
        LocalDateTime departure = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(currentTrip.departureTime), ZoneId.systemDefault());

        String body = getString(R.string.share_trip_body,
                currentTrip.originCity, currentTrip.destinationCity, departure.format(dtf));

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_trip_subject));
        shareIntent.putExtra(Intent.EXTRA_TEXT, body);
        startActivity(Intent.createChooser(shareIntent, getString(R.string.btn_share_trip)));
    }

    private void fetchHostTripsCount(String hostUid) {
        if (hostUid == null || hostUid.isEmpty()) {
            binding.tvHostTrips.setText("• 0 trips hosted");
            return;
        }
        
        FirebaseDatabase.getInstance().getReference("trips")
                .orderByChild("driverUid")
                .equalTo(hostUid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        int tripCount = (int) snapshot.getChildrenCount();
                        String tripText = tripCount == 1 ? "• 1 trip hosted" : "• " + tripCount + " trips hosted";
                        binding.tvHostTrips.setText(tripText);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        binding.tvHostTrips.setText("• 0 trips hosted");
                    }
                });
    }

    private void fetchHostRating(String hostUid) {
        if (hostUid == null || hostUid.isEmpty()) {
            binding.tvHostRating.setText("—");
            return;
        }
        
        FirebaseDatabase.getInstance().getReference("users")
                .child(hostUid)
                .child("ratingSummary")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (binding == null) return;
                        
                        Double avgRating = snapshot.child("averageRating").getValue(Double.class);
                        Long totalRatings = snapshot.child("totalRatings").getValue(Long.class);
                        
                        if (avgRating != null && totalRatings != null && totalRatings > 0) {
                            binding.tvHostRating.setText(String.format(Locale.getDefault(), "%.1f", avgRating));
                        } else {
                            binding.tvHostRating.setText("—");
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        if (binding != null) {
                            binding.tvHostRating.setText("—");
                        }
                    }
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tripListener != null) tripRef.removeEventListener(tripListener);
        if (requestListener != null) requestsRef.removeEventListener(requestListener);
    }
}
