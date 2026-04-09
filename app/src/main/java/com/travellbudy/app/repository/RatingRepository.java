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
import com.travellbudy.app.util.Constants;
import com.travellbudy.app.util.FirebaseErrorMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Repository for rating operations on /ratings/{tripId}/{ratingId}.
 * Handles creating ratings (write-once) and querying ratings for a user.
 * Uses UserRepository for ratingSummary transaction after submission.
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
}

