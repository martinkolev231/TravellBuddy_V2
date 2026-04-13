package com.travellbudy.app.dialogs;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RatingBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.travellbudy.app.R;
import com.travellbudy.app.models.Rating;

public class RateUserDialogFragment extends DialogFragment {

    private static final String ARG_RECIPIENT_UID = "recipient_uid";
    private static final String ARG_RECIPIENT_NAME = "recipient_name";
    private static final String ARG_TRIP_ID = "trip_id";

    private OnRatingSubmittedListener onRatingSubmittedListener;

    /**
     * Listener for rating submission events.
     */
    public interface OnRatingSubmittedListener {
        void onRatingSubmitted();
    }

    /**
     * Sets the listener to be called when a rating is successfully submitted.
     */
    public void setOnRatingSubmittedListener(OnRatingSubmittedListener listener) {
        this.onRatingSubmittedListener = listener;
    }

    public static RateUserDialogFragment newInstance(String recipientUid, String recipientName, String tripId) {
        RateUserDialogFragment fragment = new RateUserDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_RECIPIENT_UID, recipientUid);
        args.putString(ARG_RECIPIENT_NAME, recipientName);
        args.putString(ARG_TRIP_ID, tripId);
        fragment.setArguments(args);
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_rate_user, null);

        RatingBar ratingBar = view.findViewById(R.id.ratingBar);
        TextInputEditText etComment = view.findViewById(R.id.etComment);
        MaterialButton btnSubmit = view.findViewById(R.id.btnSubmit);

        String recipientUid = getArguments() != null ? getArguments().getString(ARG_RECIPIENT_UID) : "";
        String recipientName = getArguments() != null ? getArguments().getString(ARG_RECIPIENT_NAME) : "";
        String tripId = getArguments() != null ? getArguments().getString(ARG_TRIP_ID) : "";


        btnSubmit.setOnClickListener(v -> {
            int score = (int) ratingBar.getRating();
            if (score < 1) {
                Toast.makeText(requireContext(), R.string.error_rating_failed, Toast.LENGTH_SHORT).show();
                return;
            }

            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser == null) return;

            String reviewerUid = currentUser.getUid();
            String reviewerName = currentUser.getDisplayName() != null ? currentUser.getDisplayName() : "User";
            String comment = etComment.getText() != null ? etComment.getText().toString().trim() : "";

            // Ratings are now keyed by tripId: /ratings/{tripId}/{ratingId}
            DatabaseReference ratingsRef = FirebaseDatabase.getInstance()
                    .getReference("ratings").child(tripId);

            // Check for duplicate rating by this reviewer in this trip
            ratingsRef.orderByChild("reviewerUid").equalTo(reviewerUid)
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            for (DataSnapshot child : snapshot.getChildren()) {
                                Rating existing = child.getValue(Rating.class);
                                if (existing != null && recipientUid.equals(existing.revieweeUid)) {
                                    Toast.makeText(requireContext(), R.string.error_already_rated,
                                            Toast.LENGTH_SHORT).show();
                                    return;
                                }
                            }

                            // Submit rating
                            String ratingId = ratingsRef.push().getKey();
                            if (ratingId == null) return;

                            Rating rating = new Rating(ratingId, tripId, reviewerUid, reviewerName,
                                    recipientUid, score, comment);

                            ratingsRef.child(ratingId).setValue(rating.toMap())
                                    .addOnSuccessListener(aVoid -> {
                                        // Update recipient's rating summary
                                        updateRatingSummary(recipientUid, score);
                                        Toast.makeText(requireContext(), R.string.success_rating_submitted,
                                                Toast.LENGTH_SHORT).show();
                                        // Notify listener
                                        if (onRatingSubmittedListener != null) {
                                            onRatingSubmittedListener.onRatingSubmitted();
                                        }
                                        dismiss();
                                    })
                                    .addOnFailureListener(e ->
                                            Toast.makeText(requireContext(), R.string.error_rating_failed,
                                                    Toast.LENGTH_SHORT).show());
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            Toast.makeText(requireContext(), R.string.error_generic,
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
        });

        return new MaterialAlertDialogBuilder(requireContext())
                .setView(view)
                .create();
    }

    /**
     * Scans all ratings across all trips for the given user and updates
     * /users/{userId}/ratingSummary atomically.
     */
    private void updateRatingSummary(String userId, int newScore) {
        DatabaseReference userRef = FirebaseDatabase.getInstance()
                .getReference("users").child(userId).child("ratingSummary");

        // For MVP, we increment via transaction to avoid race conditions
        userRef.runTransaction(new com.google.firebase.database.Transaction.Handler() {
            @NonNull
            @Override
            public com.google.firebase.database.Transaction.Result doTransaction(
                    @NonNull com.google.firebase.database.MutableData currentData) {
                Double currentAvg = currentData.child("averageRating").getValue(Double.class);
                Long currentTotal = currentData.child("totalRatings").getValue(Long.class);

                if (currentAvg == null) currentAvg = 0.0;
                if (currentTotal == null) currentTotal = 0L;

                // Recompute: (oldAvg * oldCount + newScore) / (oldCount + 1)
                long newTotal = currentTotal + 1;
                double newAvg = ((currentAvg * currentTotal) + newScore) / newTotal;

                currentData.child("averageRating").setValue(newAvg);
                currentData.child("totalRatings").setValue(newTotal);

                return com.google.firebase.database.Transaction.success(currentData);
            }

            @Override
            public void onComplete(com.google.firebase.database.DatabaseError error,
                                   boolean committed,
                                   com.google.firebase.database.DataSnapshot snapshot) {
                // No-op
            }
        });
    }
}

