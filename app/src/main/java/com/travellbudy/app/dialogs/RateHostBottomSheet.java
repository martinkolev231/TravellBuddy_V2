package com.travellbudy.app.dialogs;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;
import com.travellbudy.app.R;
import com.travellbudy.app.models.Rating;

import android.util.Log;

/**
 * A polished bottom sheet modal for rating a trip host.
 * Features:
 * - Host avatar with star badge overlay
 * - Dynamic host name and trip name
 * - Interactive 5-star rating
 * - Optional review text
 * - Submit button enabled only after selecting rating
 */
public class RateHostBottomSheet extends BottomSheetDialogFragment {

    private static final String TAG = "RateHostBottomSheet";

    private static final String ARG_HOST_UID = "host_uid";
    private static final String ARG_HOST_NAME = "host_name";
    private static final String ARG_HOST_PHOTO_URL = "host_photo_url";
    private static final String ARG_TRIP_ID = "trip_id";
    private static final String ARG_TRIP_TITLE = "trip_title";

    // Views
    private ImageView ivHostAvatar;
    private ImageView star1, star2, star3, star4, star5;
    private ImageView[] stars;
    private TextInputEditText etReview;
    private MaterialButton btnSubmitRating;

    // State
    private int selectedRating = 0;
    private String hostUid;
    private String hostName;
    private String hostPhotoUrl;
    private String tripId;
    private String tripTitle;

    // Listener
    private OnRatingSubmittedListener onRatingSubmittedListener;

    public interface OnRatingSubmittedListener {
        void onRatingSubmitted();
    }

    public void setOnRatingSubmittedListener(OnRatingSubmittedListener listener) {
        this.onRatingSubmittedListener = listener;
    }

    public static RateHostBottomSheet newInstance(
            String hostUid,
            String hostName,
            String hostPhotoUrl,
            String tripId,
            String tripTitle
    ) {
        RateHostBottomSheet fragment = new RateHostBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_HOST_UID, hostUid);
        args.putString(ARG_HOST_NAME, hostName);
        args.putString(ARG_HOST_PHOTO_URL, hostPhotoUrl);
        args.putString(ARG_TRIP_ID, tripId);
        args.putString(ARG_TRIP_TITLE, tripTitle);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            hostUid = getArguments().getString(ARG_HOST_UID, "");
            hostName = getArguments().getString(ARG_HOST_NAME, "Host");
            hostPhotoUrl = getArguments().getString(ARG_HOST_PHOTO_URL, "");
            tripId = getArguments().getString(ARG_TRIP_ID, "");
            tripTitle = getArguments().getString(ARG_TRIP_TITLE, "");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_rate_host_bottom_sheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Bind views
        ivHostAvatar = view.findViewById(R.id.ivHostAvatar);
        star1 = view.findViewById(R.id.star1);
        star2 = view.findViewById(R.id.star2);
        star3 = view.findViewById(R.id.star3);
        star4 = view.findViewById(R.id.star4);
        star5 = view.findViewById(R.id.star5);
        stars = new ImageView[]{star1, star2, star3, star4, star5};
        etReview = view.findViewById(R.id.etReview);
        btnSubmitRating = view.findViewById(R.id.btnSubmitRating);

        // Close button
        view.findViewById(R.id.btnClose).setOnClickListener(v -> dismiss());

        // Set title and subtitle
        android.widget.TextView tvTitle = view.findViewById(R.id.tvTitle);
        android.widget.TextView tvTripName = view.findViewById(R.id.tvTripName);
        tvTitle.setText(getString(R.string.title_rate_host_name, hostName));
        tvTripName.setText(getString(R.string.label_trip_subtitle, tripTitle));

        // Load host avatar
        loadHostAvatar();

        // Setup star click listeners
        setupStarListeners();

        // Submit button
        btnSubmitRating.setOnClickListener(v -> submitRating());

        // Expand bottom sheet fully
        if (getDialog() instanceof BottomSheetDialog) {
            BottomSheetDialog bottomSheetDialog = (BottomSheetDialog) getDialog();
            bottomSheetDialog.setOnShowListener(dialog -> {
                BottomSheetDialog d = (BottomSheetDialog) dialog;
                View bottomSheet = d.findViewById(com.google.android.material.R.id.design_bottom_sheet);
                if (bottomSheet != null) {
                    BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                    behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                    behavior.setSkipCollapsed(true);
                }
            });
        }
    }

    private void loadHostAvatar() {
        if (hostPhotoUrl != null && !hostPhotoUrl.isEmpty()) {
            Glide.with(this)
                    .load(hostPhotoUrl)
                    .placeholder(R.drawable.bg_profile_placeholder)
                    .error(R.drawable.bg_profile_placeholder)
                    .circleCrop()
                    .into(ivHostAvatar);
        } else {
            ivHostAvatar.setImageResource(R.drawable.bg_profile_placeholder);
        }
    }

    private void setupStarListeners() {
        for (int i = 0; i < stars.length; i++) {
            final int rating = i + 1;
            stars[i].setOnClickListener(v -> setRating(rating));
        }
    }

    private void setRating(int rating) {
        selectedRating = rating;
        updateStarDisplay();
        updateSubmitButton();
    }

    private void updateStarDisplay() {
        int filledColor = ContextCompat.getColor(requireContext(), R.color.star_filled);
        int emptyColor = ContextCompat.getColor(requireContext(), R.color.star_empty_rating);

        for (int i = 0; i < stars.length; i++) {
            if (i < selectedRating) {
                stars[i].setImageResource(R.drawable.ic_star_filled);
                stars[i].setColorFilter(filledColor);
            } else {
                stars[i].setImageResource(R.drawable.ic_star_empty);
                stars[i].setColorFilter(emptyColor);
            }
        }
    }

    private void updateSubmitButton() {
        boolean enabled = selectedRating > 0;
        btnSubmitRating.setEnabled(enabled);

        if (enabled) {
            btnSubmitRating.setBackgroundTintList(
                    ContextCompat.getColorStateList(requireContext(), R.color.primary));
            btnSubmitRating.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.white));
        } else {
            btnSubmitRating.setBackgroundTintList(
                    ContextCompat.getColorStateList(requireContext(), R.color.button_disabled_bg));
            btnSubmitRating.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.button_disabled_text));
        }
    }

    private void submitRating() {
        if (selectedRating < 1) {
            Toast.makeText(requireContext(), R.string.error_rating_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;

        String reviewerUid = currentUser.getUid();
        String reviewerName = currentUser.getDisplayName() != null ? currentUser.getDisplayName() : "User";
        String comment = etReview.getText() != null ? etReview.getText().toString().trim() : "";

        // Disable button to prevent double submission
        btnSubmitRating.setEnabled(false);

        // Use a DETERMINISTIC rating ID: reviewerUid_revieweeUid
        // This prevents duplicates at the database level - same reviewer+reviewee = same key
        String ratingId = reviewerUid + "_" + hostUid;
        
        DatabaseReference ratingRef = FirebaseDatabase.getInstance()
                .getReference("ratings").child(tripId).child(ratingId);

        Log.d(TAG, "Checking if rating already exists: " + ratingId);
        
        // Check if this specific rating already exists
        ratingRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    // Rating already exists - user already rated this host
                    Log.d(TAG, "DUPLICATE FOUND - rating key already exists: " + ratingId);
                    Toast.makeText(requireContext(), R.string.error_already_rated,
                            Toast.LENGTH_SHORT).show();
                    btnSubmitRating.setEnabled(true);
                    dismiss();
                    return;
                }
                
                // No existing rating, proceed to submit
                Rating rating = new Rating(ratingId, tripId, reviewerUid, reviewerName,
                        hostUid, selectedRating, comment);

                Log.d(TAG, "Submitting rating: tripId=" + tripId + ", hostUid=" + hostUid + 
                        ", reviewerUid=" + reviewerUid + ", score=" + selectedRating);
                Log.d(TAG, "Rating ID (deterministic): " + ratingId);

                ratingRef.setValue(rating.toMap())
                        .addOnSuccessListener(aVoid -> {
                            Log.d(TAG, "Rating submitted successfully!");
                            // Update host's rating summary
                            updateRatingSummary(hostUid, selectedRating);
                            Toast.makeText(requireContext(), R.string.success_rating_submitted,
                                    Toast.LENGTH_SHORT).show();
                            // Notify listener
                            if (onRatingSubmittedListener != null) {
                                onRatingSubmittedListener.onRatingSubmitted();
                            }
                            dismiss();
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Failed to submit rating: " + e.getMessage(), e);
                            Toast.makeText(requireContext(), R.string.error_rating_failed,
                                    Toast.LENGTH_SHORT).show();
                            btnSubmitRating.setEnabled(true);
                        });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error checking existing rating: " + error.getMessage());
                Toast.makeText(requireContext(), R.string.error_generic,
                        Toast.LENGTH_SHORT).show();
                btnSubmitRating.setEnabled(true);
            }
        });
    }

    /**
     * Updates /users/{userId}/ratingSummary atomically using a transaction.
     */
    private void updateRatingSummary(String userId, int newScore) {
        DatabaseReference userRef = FirebaseDatabase.getInstance()
                .getReference("users").child(userId).child("ratingSummary");

        userRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                Double currentAvg = currentData.child("averageRating").getValue(Double.class);
                Long currentTotal = currentData.child("totalRatings").getValue(Long.class);

                if (currentAvg == null) currentAvg = 0.0;
                if (currentTotal == null) currentTotal = 0L;

                // Recompute: (oldAvg * oldCount + newScore) / (oldCount + 1)
                long newTotal = currentTotal + 1;
                double newAvg = ((currentAvg * currentTotal) + newScore) / newTotal;

                currentData.child("averageRating").setValue(newAvg);
                currentData.child("totalRatings").setValue(newTotal);

                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot snapshot) {
                // No-op
            }
        });
    }
}



