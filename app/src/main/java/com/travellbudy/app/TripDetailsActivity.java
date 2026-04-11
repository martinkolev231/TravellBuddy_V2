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

        setupListeners();
        setupButtons();
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

        // Spots left badge
        int spotsLeft = currentTrip.availableSeats;
        if (spotsLeft == 1) {
            binding.tvSpotsLeft.setText("1 SPOT LEFT");
        } else {
            binding.tvSpotsLeft.setText(spotsLeft + " SPOTS LEFT");
        }
        binding.tvSpotsLeft.setVisibility(spotsLeft > 0 ? View.VISIBLE : View.GONE);

        // Title - use carModel as title, fallback to destination
        String title = currentTrip.carModel != null && !currentTrip.carModel.isEmpty() 
                ? currentTrip.carModel 
                : currentTrip.destinationCity;
        binding.tvTitle.setText(title);

        // Location
        binding.tvLocation.setText(currentTrip.destinationCity);

        // Dates - Display in single line format like "Oct 10 - Oct 14"
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault());
        LocalDateTime startDate = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(currentTrip.departureTime), ZoneId.systemDefault());
        LocalDateTime endDate = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(currentTrip.estimatedArrivalTime), ZoneId.systemDefault());
        String dateRange = startDate.format(dateFormatter) + " - " + endDate.format(dateFormatter);
        binding.tvDates.setText(dateRange);

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
        boolean isHost = currentUserId != null && currentUserId.equals(currentTrip.driverUid);
        android.util.Log.d("TripDetails", "currentUserId: " + currentUserId + ", driverUid: " + currentTrip.driverUid + ", isHost: " + isHost);
        if (isHost) {
            binding.tvHostName.setText(currentTrip.driverName + " (You)");
        } else {
            binding.tvHostName.setText(currentTrip.driverName);
        }
        
        if (currentTrip.driverPhotoUrl != null && !currentTrip.driverPhotoUrl.isEmpty()) {
            Glide.with(this)
                    .load(currentTrip.driverPhotoUrl)
                    .placeholder(R.drawable.ic_profile)
                    .circleCrop()
                    .into(binding.ivHostPhoto);
        }

        // Host rating (placeholder for now)
        binding.tvHostRating.setText("5");
        
        // Fetch actual trips hosted count
        fetchHostTripsCount(currentTrip.driverUid);

        // Participants
        int totalParticipants = currentTrip.totalSeats - currentTrip.availableSeats;
        binding.tvParticipantsTitle.setText(String.format("Participants (%d/%d)", totalParticipants, currentTrip.totalSeats));

        // Show/hide Delete button based on whether user is host
        if (isHost) {
            android.util.Log.d("TripDetails", "Showing delete button - user is host");
            binding.btnDeleteAdventure.setVisibility(View.VISIBLE);
            binding.btnJoinActivity.setVisibility(View.GONE);
        } else {
            android.util.Log.d("TripDetails", "Hiding delete button - user is NOT host");
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

        // Favorite button (placeholder)
        binding.btnFavorite.setOnClickListener(v -> {
            Toast.makeText(this, "Added to favorites", Toast.LENGTH_SHORT).show();
        });


        // Join Activity button
        binding.btnJoinActivity.setOnClickListener(v -> sendSeatRequest());

        // Delete Adventure button
        binding.btnDeleteAdventure.setOnClickListener(v -> showDeleteConfirmation());
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tripListener != null) tripRef.removeEventListener(tripListener);
        if (requestListener != null) requestsRef.removeEventListener(requestListener);
    }
}
