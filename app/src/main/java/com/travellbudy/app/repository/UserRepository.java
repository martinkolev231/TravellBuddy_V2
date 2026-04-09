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
import com.travellbudy.app.models.User;
import com.travellbudy.app.util.Constants;
import com.travellbudy.app.util.FirebaseErrorMapper;

import java.util.Map;

/**
 * Repository for user profile CRUD on /users/{uid}.
 * Also handles ratingSummary transactions (cross-user writes after rating).
 */
public class UserRepository {

    private final FirebaseManager firebase;
    private final Application application;
    private ValueEventListener profileListener;
    private DatabaseReference profileRef;

    public UserRepository(@NonNull Application application) {
        this.application = application;
        this.firebase = FirebaseManager.getInstance();
    }

    /**
     * Creates a new user profile in /users/{uid}.
     */
    public LiveData<Result<Void>> createUser(@NonNull User user) {
        MutableLiveData<Result<Void>> result = new MutableLiveData<>(Result.loading());

        firebase.getUserRef(user.uid).setValue(user.toMap())
                .addOnSuccessListener(aVoid -> result.setValue(Result.success(null)))
                .addOnFailureListener(e -> result.setValue(Result.error(e.getMessage())));

        return result;
    }

    /**
     * Observes user profile at /users/{uid} in realtime.
     * Call {@link #removeProfileListener()} in ViewModel.onCleared().
     */
    public LiveData<Result<User>> observeUser(@NonNull String uid) {
        MutableLiveData<Result<User>> result = new MutableLiveData<>(Result.loading());

        profileRef = firebase.getUserRef(uid);
        profileListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                User user = snapshot.getValue(User.class);
                if (user != null) {
                    result.setValue(Result.success(user));
                } else {
                    result.setValue(Result.error("User not found"));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                result.setValue(Result.error(
                        FirebaseErrorMapper.map(application, error)));
            }
        };
        profileRef.addValueEventListener(profileListener);

        return result;
    }

    /**
     * Updates specific fields on the user profile.
     */
    public LiveData<Result<Void>> updateProfile(@NonNull String uid,
                                                  @NonNull Map<String, Object> updates) {
        MutableLiveData<Result<Void>> result = new MutableLiveData<>(Result.loading());

        firebase.getUserRef(uid).updateChildren(updates)
                .addOnSuccessListener(aVoid -> result.setValue(Result.success(null)))
                .addOnFailureListener(e -> result.setValue(Result.error(e.getMessage())));

        return result;
    }

    /**
     * Updates ratingSummary on another user's profile via transaction.
     * Called after submitting a rating — runs under the reviewer's auth context.
     */
    public void updateRatingSummary(@NonNull String targetUid, int newScore) {
        DatabaseReference summaryRef = firebase.getUserRef(targetUid)
                .child(Constants.FIELD_RATING_SUMMARY);

        summaryRef.runTransaction(new com.google.firebase.database.Transaction.Handler() {
            @NonNull
            @Override
            public com.google.firebase.database.Transaction.Result doTransaction(
                    @NonNull com.google.firebase.database.MutableData currentData) {
                Double currentAvg = currentData.child(Constants.FIELD_AVG_RATING)
                        .getValue(Double.class);
                Long currentTotal = currentData.child(Constants.FIELD_TOTAL_RATINGS)
                        .getValue(Long.class);

                if (currentAvg == null) currentAvg = 0.0;
                if (currentTotal == null) currentTotal = 0L;

                long newTotal = currentTotal + 1;
                double newAvg = ((currentAvg * currentTotal) + newScore) / newTotal;

                currentData.child(Constants.FIELD_AVG_RATING).setValue(newAvg);
                currentData.child(Constants.FIELD_TOTAL_RATINGS).setValue(newTotal);

                return com.google.firebase.database.Transaction.success(currentData);
            }

            @Override
            public void onComplete(com.google.firebase.database.DatabaseError error,
                                   boolean committed,
                                   com.google.firebase.database.DataSnapshot snapshot) {
                // Fire-and-forget for MVP
            }
        });
    }

    /**
     * Removes the profile ValueEventListener. Must be called in ViewModel.onCleared().
     */
    public void removeProfileListener() {
        if (profileRef != null && profileListener != null) {
            profileRef.removeEventListener(profileListener);
            profileListener = null;
            profileRef = null;
        }
    }
}

