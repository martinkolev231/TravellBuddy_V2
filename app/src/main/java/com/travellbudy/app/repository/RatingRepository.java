package com.travellbudy.app.repository;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.travellbudy.app.firebase.FirebaseManager;
import com.travellbudy.app.model.Result;
import com.travellbudy.app.models.Rating;
import com.travellbudy.app.models.Trip;
import com.travellbudy.app.util.Constants;
import com.travellbudy.app.util.FirebaseErrorMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Repository for rating operations on /ratings/{tripId}/{ratingId}.
 * Handles creating ratings (write-once) and querying ratings for a user.
 * Uses UserRepository for ratingSummary transaction after submission.
 *
 * Rating rules:
 * - Only participants of a completed trip can rate
 * - Users can only rate the host (not other participants)
 * - Each user can rate a host only once per trip
 * - Ratings are stored at /ratings/{tripId}/{ratingId}
 * - User's ratingSummary is updated atomically after each rating
 */
public class RatingRepository {

    private final FirebaseManager firebase;
    private final Application application;
    private final UserRepository userRepository;
    private ValueEventListener ratingsListener;
    private DatabaseReference ratingsListenerRef;

    public RatingRepository(@NonNull Application application) {
        this.application = application;
        this.firebase = FirebaseManager.getInstance();
        this.userRepository = new UserRepository(application);
    }

    /**
     * Checks if the current user can rate the host of a given trip.
     *
     * Conditions:
     * 1. Trip must be completed (based on dates, not manually set status)
     * 2. Trip must not be canceled
     * 3. User must have an approved request for this trip (i.e., they actually joined)
     * 4. User must not have already rated the host for this trip
     * 5. User cannot be the host themselves
     *
     * @param tripId        The trip to check
     * @param currentUserId The current user's ID
     * @return LiveData containing Result with CanRateResult
     */
    public LiveData<Result<CanRateResult>> canUserRateTrip(@NonNull String tripId,
                                                            @NonNull String currentUserId) {
        MutableLiveData<Result<CanRateResult>> result = new MutableLiveData<>(Result.loading());

        // First, fetch the trip to check status and host
        firebase.getTripRef(tripId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Trip trip = snapshot.getValue(Trip.class);

                if (trip == null) {
                    result.setValue(Result.error("Trip not found"));
                    return;
                }

                // Check if trip is canceled
                if (trip.isCanceled()) {
                    result.setValue(Result.success(new CanRateResult(false,
                            "Cannot rate canceled trips", null, null)));
                    return;
                }

                // Check if trip is completed (date-based)
                if (!trip.isCompleted()) {
                    result.setValue(Result.success(new CanRateResult(false,
                            "You can only rate completed trips", null, null)));
                    return;
                }

                // Check if user is the host (hosts can't rate themselves)
                if (currentUserId.equals(trip.driverUid)) {
                    result.setValue(Result.success(new CanRateResult(false,
                            "You cannot rate your own trip", null, null)));
                    return;
                }

                // Check if user was an approved participant
                checkUserParticipation(tripId, currentUserId, trip, result);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                result.setValue(Result.error("Failed to load trip: " + error.getMessage()));
            }
        });

        return result;
    }

    private void checkUserParticipation(@NonNull String tripId,
                                        @NonNull String currentUserId,
                                        @NonNull Trip trip,
                                        @NonNull MutableLiveData<Result<CanRateResult>> result) {
        // Check if user has an approved request for this trip
        firebase.getRequestsRef(tripId)
                .orderByChild(Constants.FIELD_RIDER_UID)
                .equalTo(currentUserId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        boolean hasApprovedRequest = false;

                        for (DataSnapshot requestSnap : snapshot.getChildren()) {
                            String status = requestSnap.child("status").getValue(String.class);
                            if (Constants.REQUEST_APPROVED.equals(status)) {
                                hasApprovedRequest = true;
                                break;
                            }
                        }

                        if (!hasApprovedRequest) {
                            result.setValue(Result.success(new CanRateResult(false,
                                    "Only approved participants can rate", null, null)));
                            return;
                        }

                        // Check if user has already rated the host for this trip
                        checkExistingRating(tripId, currentUserId, trip, result);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        result.setValue(Result.error("Failed to check participation: " + error.getMessage()));
                    }
                });
    }

    private void checkExistingRating(@NonNull String tripId,
                                     @NonNull String currentUserId,
                                     @NonNull Trip trip,
                                     @NonNull MutableLiveData<Result<CanRateResult>> result) {
        firebase.getRatingsRef(tripId)
                .orderByChild(Constants.FIELD_REVIEWER_UID)
                .equalTo(currentUserId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        for (DataSnapshot ratingSnap : snapshot.getChildren()) {
                            Rating existing = ratingSnap.getValue(Rating.class);
                            if (existing != null && trip.driverUid.equals(existing.revieweeUid)) {
                                result.setValue(Result.success(new CanRateResult(false,
                                        "You have already rated this trip", null, null)));
                                return;
                            }
                        }

                        // All checks passed - user can rate
                        result.setValue(Result.success(new CanRateResult(true, null,
                                trip.driverUid, trip.driverName)));
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        result.setValue(Result.error("Failed to check existing ratings: " + error.getMessage()));
                    }
                });
    }

    /**
     * Submits a rating (write-once per security rules).
     * After successful write, updates the reviewee's ratingSummary via transaction.
     */
    public LiveData<Result<Void>> submitRating(@NonNull Rating rating) {
        MutableLiveData<Result<Void>> result = new MutableLiveData<>(Result.loading());

        DatabaseReference ratingsRef = firebase.getRatingsRef(rating.tripId);
        String ratingId = ratingsRef.push().getKey();
        if (ratingId == null) {
            result.setValue(Result.error("Failed to create rating"));
            return result;
        }

        rating.ratingId = ratingId;
        ratingsRef.child(ratingId).setValue(rating.toMap())
                .addOnSuccessListener(aVoid -> {
                    // Update reviewee's ratingSummary
                    userRepository.updateRatingSummary(rating.revieweeUid, rating.score);
                    result.setValue(Result.success(null));
                })
                .addOnFailureListener(e ->
                        result.setValue(Result.error(e.getMessage())));

        return result;
    }

    /**
     * Checks if the reviewer has already rated the reviewee for a specific trip.
     */
    public LiveData<Result<Boolean>> hasAlreadyRated(@NonNull String tripId,
                                                       @NonNull String reviewerUid,
                                                       @NonNull String revieweeUid) {
        MutableLiveData<Result<Boolean>> result = new MutableLiveData<>(Result.loading());

        firebase.getRatingsRef(tripId)
                .orderByChild(Constants.FIELD_REVIEWER_UID)
                .equalTo(reviewerUid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        boolean found = false;
                        for (DataSnapshot child : snapshot.getChildren()) {
                            Rating existing = child.getValue(Rating.class);
                            if (existing != null && revieweeUid.equals(existing.revieweeUid)) {
                                found = true;
                                break;
                            }
                        }
                        result.setValue(Result.success(found));
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        result.setValue(Result.error(
                                FirebaseErrorMapper.map(application, error)));
                    }
                });

        return result;
    }

    /**
     * Observes all ratings where the given user is the reviewee.
     * Scans all /ratings/{tripId} nodes (suitable for MVP scale).
     */
    public LiveData<Result<List<Rating>>> observeRatingsForUser(@NonNull String userId) {
        MutableLiveData<Result<List<Rating>>> result = new MutableLiveData<>(Result.loading());

        ratingsListenerRef = firebase.getRef(Constants.PATH_RATINGS);
        ratingsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Rating> ratings = new ArrayList<>();
                for (DataSnapshot tripSnap : snapshot.getChildren()) {
                    for (DataSnapshot ratingSnap : tripSnap.getChildren()) {
                        Rating rating = ratingSnap.getValue(Rating.class);
                        if (rating != null && userId.equals(rating.revieweeUid)) {
                            ratings.add(rating);
                        }
                    }
                }
                result.setValue(Result.success(ratings));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                result.setValue(Result.error(
                        FirebaseErrorMapper.map(application, error)));
            }
        };
        ratingsListenerRef.addValueEventListener(ratingsListener);

        return result;
    }

    /**
     * Removes listeners. Call in ViewModel.onCleared().
     */
    public void removeListeners() {
        if (ratingsListenerRef != null && ratingsListener != null) {
            ratingsListenerRef.removeEventListener(ratingsListener);
        }
    }

    /**
     * Result class for canUserRateTrip check.
     */
    public static class CanRateResult {
        public final boolean canRate;
        public final String reason;
        public final String hostUid;
        public final String hostName;

        public CanRateResult(boolean canRate, String reason, String hostUid, String hostName) {
            this.canRate = canRate;
            this.reason = reason;
            this.hostUid = hostUid;
            this.hostName = hostName;
        }
    }
}
