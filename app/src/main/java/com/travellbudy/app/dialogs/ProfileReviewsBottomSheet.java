package com.travellbudy.app.dialogs;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.travellbudy.app.R;
import com.travellbudy.app.models.Rating;
import com.travellbudy.app.models.Trip;

import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Bottom sheet modal displaying all reviews for a user profile.
 * Features:
 * - Filter by star rating (All, 5★, 4★, 3★, 2★, 1★)
 * - Scrollable list of review cards
 * - Shows reviewer avatar, name, date, trip name, and review text
 */
public class ProfileReviewsBottomSheet extends BottomSheetDialogFragment {

    private static final String TAG = "ProfileReviewsSheet";
    private static final String ARG_PROFILE_ID = "profile_id";
    private static final String ARG_PROFILE_NAME = "profile_name";

    private String profileId;
    private String profileName;

    // Views
    private TextView tvTitle;
    private RecyclerView rvReviews;
    private View layoutEmptyState;
    private TextView tvEmptyTitle;
    private TextView tvEmptySubtitle;

    // Filter chips
    private TextView chipAll, chip5Star, chip4Star, chip3Star, chip2Star, chip1Star;
    private TextView[] filterChips;

    // Data
    private final List<ReviewItem> allReviews = new ArrayList<>();
    private final List<ReviewItem> filteredReviews = new ArrayList<>();
    private ReviewsAdapter adapter;
    private int selectedFilter = 0; // 0 = All, 1-5 = star rating

    // Cache for trip names
    private final Map<String, String> tripNameCache = new HashMap<>();

    /**
     * Review item combining Rating data with additional fetched info.
     */
    public static class ReviewItem {
        public Rating rating;
        public String reviewerAvatarUrl;
        public String tripName;

        public ReviewItem(Rating rating) {
            this.rating = rating;
        }
    }

    public static ProfileReviewsBottomSheet newInstance(String profileId, String profileName) {
        ProfileReviewsBottomSheet fragment = new ProfileReviewsBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_PROFILE_ID, profileId);
        args.putString(ARG_PROFILE_NAME, profileName);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            profileId = getArguments().getString(ARG_PROFILE_ID, "");
            profileName = getArguments().getString(ARG_PROFILE_NAME, "User");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_profile_reviews, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        bindViews(view);
        setupUI();
        setupFilterChips();
        loadReviews();

        // Expand bottom sheet
        if (getDialog() instanceof BottomSheetDialog) {
            BottomSheetDialog dialog = (BottomSheetDialog) getDialog();
            dialog.setOnShowListener(d -> {
                BottomSheetDialog bsd = (BottomSheetDialog) d;
                View bottomSheet = bsd.findViewById(com.google.android.material.R.id.design_bottom_sheet);
                if (bottomSheet != null) {
                    BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                    behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                    behavior.setSkipCollapsed(true);
                    // Set to 85% of screen height
                    int screenHeight = getResources().getDisplayMetrics().heightPixels;
                    behavior.setPeekHeight((int) (screenHeight * 0.85));
                }
            });
        }
    }

    private void bindViews(View view) {
        tvTitle = view.findViewById(R.id.tvTitle);
        rvReviews = view.findViewById(R.id.rvReviews);
        layoutEmptyState = view.findViewById(R.id.layoutEmptyState);
        tvEmptyTitle = view.findViewById(R.id.tvEmptyTitle);
        tvEmptySubtitle = view.findViewById(R.id.tvEmptySubtitle);

        chipAll = view.findViewById(R.id.chipAll);
        chip5Star = view.findViewById(R.id.chip5Star);
        chip4Star = view.findViewById(R.id.chip4Star);
        chip3Star = view.findViewById(R.id.chip3Star);
        chip2Star = view.findViewById(R.id.chip2Star);
        chip1Star = view.findViewById(R.id.chip1Star);

        filterChips = new TextView[]{chipAll, chip5Star, chip4Star, chip3Star, chip2Star, chip1Star};

        // Close button
        view.findViewById(R.id.btnClose).setOnClickListener(v -> dismiss());
    }

    private void setupUI() {
        // Set title
        tvTitle.setText(getString(R.string.title_reviews_for, profileName));

        // Setup RecyclerView
        adapter = new ReviewsAdapter();
        rvReviews.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvReviews.setAdapter(adapter);
    }

    private void setupFilterChips() {
        // Filter indices: 0=All, 1=5★, 2=4★, 3=3★, 4=2★, 5=1★
        chipAll.setOnClickListener(v -> selectFilter(0));
        chip5Star.setOnClickListener(v -> selectFilter(1));
        chip4Star.setOnClickListener(v -> selectFilter(2));
        chip3Star.setOnClickListener(v -> selectFilter(3));
        chip2Star.setOnClickListener(v -> selectFilter(4));
        chip1Star.setOnClickListener(v -> selectFilter(5));
    }

    private void selectFilter(int filterIndex) {
        selectedFilter = filterIndex;
        updateFilterChipsUI();
        applyFilter();
    }

    private void updateFilterChipsUI() {
        for (int i = 0; i < filterChips.length; i++) {
            if (i == selectedFilter) {
                filterChips[i].setBackgroundResource(R.drawable.bg_filter_chip_active);
                filterChips[i].setTextColor(ContextCompat.getColor(requireContext(), R.color.white));
                filterChips[i].setTypeface(filterChips[i].getTypeface(), android.graphics.Typeface.BOLD);
            } else {
                filterChips[i].setBackgroundResource(R.drawable.bg_filter_chip_inactive);
                filterChips[i].setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
                filterChips[i].setTypeface(filterChips[i].getTypeface(), android.graphics.Typeface.NORMAL);
            }
        }
    }

    private void applyFilter() {
        filteredReviews.clear();

        if (selectedFilter == 0) {
            // All reviews
            filteredReviews.addAll(allReviews);
        } else {
            // Filter by star rating (1=5★, 2=4★, 3=3★, 4=2★, 5=1★)
            int targetRating = 6 - selectedFilter;
            for (ReviewItem item : allReviews) {
                if (item.rating.score == targetRating) {
                    filteredReviews.add(item);
                }
            }
        }

        adapter.notifyDataSetChanged();
        updateEmptyState();
    }

    private void updateEmptyState() {
        if (filteredReviews.isEmpty()) {
            rvReviews.setVisibility(View.GONE);
            layoutEmptyState.setVisibility(View.VISIBLE);

            if (allReviews.isEmpty()) {
                tvEmptyTitle.setText(R.string.empty_reviews_title);
                tvEmptySubtitle.setText(R.string.empty_reviews_subtitle);
            } else {
                // Filtered empty state
                int targetRating = 6 - selectedFilter;
                tvEmptyTitle.setText(getString(R.string.empty_reviews_filtered, targetRating));
                tvEmptySubtitle.setVisibility(View.GONE);
            }
        } else {
            rvReviews.setVisibility(View.VISIBLE);
            layoutEmptyState.setVisibility(View.GONE);
        }
    }

    private void loadReviews() {
        if (profileId == null || profileId.isEmpty()) {
            Log.e(TAG, "profileId is null or empty");
            updateEmptyState();
            return;
        }

        Log.d(TAG, "Loading reviews for profileId: " + profileId);

        // Query all ratings where revieweeUid equals profileId
        // Ratings are stored as /ratings/{tripId}/{ratingId}
        DatabaseReference ratingsRef = FirebaseDatabase.getInstance().getReference("ratings");

        ratingsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) return;

                Log.d(TAG, "Ratings snapshot received. Children count: " + snapshot.getChildrenCount());

                allReviews.clear();
                List<Rating> ratings = new ArrayList<>();

                // Iterate through all trips
                for (DataSnapshot tripSnapshot : snapshot.getChildren()) {
                    String tripId = tripSnapshot.getKey();
                    Log.d(TAG, "Checking trip: " + tripId + " with " + tripSnapshot.getChildrenCount() + " ratings");

                    // Iterate through ratings in this trip
                    for (DataSnapshot ratingSnapshot : tripSnapshot.getChildren()) {
                        // Log raw data for debugging
                        Log.d(TAG, "  Rating key: " + ratingSnapshot.getKey());
                        Log.d(TAG, "  Raw revieweeUid: " + ratingSnapshot.child("revieweeUid").getValue());
                        
                        Rating rating = ratingSnapshot.getValue(Rating.class);
                        if (rating != null) {
                            Log.d(TAG, "  Parsed rating - revieweeUid: " + rating.revieweeUid + 
                                    ", reviewerUid: " + rating.reviewerUid + 
                                    ", score: " + rating.score);
                            
                            if (profileId.equals(rating.revieweeUid)) {
                                Log.d(TAG, "  ✓ MATCH! Adding this rating");
                                rating.tripId = tripId; // Ensure tripId is set
                                ratings.add(rating);
                            } else {
                                Log.d(TAG, "  ✗ No match: " + profileId + " != " + rating.revieweeUid);
                            }
                        } else {
                            Log.w(TAG, "  Rating is null after parsing");
                        }
                    }
                }

                Log.d(TAG, "Total matching ratings found: " + ratings.size());

                // Sort by newest first
                Collections.sort(ratings, (a, b) -> Long.compare(b.createdAt, a.createdAt));

                // Create ReviewItems
                for (Rating rating : ratings) {
                    allReviews.add(new ReviewItem(rating));
                }

                // Load additional data (reviewer avatars and trip names)
                loadReviewerAvatars();
                loadTripNames();

                applyFilter();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load ratings: " + error.getMessage());
                if (isAdded()) {
                    updateEmptyState();
                }
            }
        });
    }

    private void loadReviewerAvatars() {
        for (ReviewItem item : allReviews) {
            if (item.rating.reviewerUid != null) {
                FirebaseDatabase.getInstance().getReference("users")
                        .child(item.rating.reviewerUid)
                        .child("photoUrl")
                        .addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot snapshot) {
                                item.reviewerAvatarUrl = snapshot.getValue(String.class);
                                if (isAdded()) {
                                    adapter.notifyDataSetChanged();
                                }
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {}
                        });
            }
        }
    }

    private void loadTripNames() {
        for (ReviewItem item : allReviews) {
            if (item.rating.tripId != null) {
                // Check cache first
                if (tripNameCache.containsKey(item.rating.tripId)) {
                    item.tripName = tripNameCache.get(item.rating.tripId);
                    continue;
                }

                FirebaseDatabase.getInstance().getReference("trips")
                        .child(item.rating.tripId)
                        .addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot snapshot) {
                                Trip trip = snapshot.getValue(Trip.class);
                                if (trip != null) {
                                    String name = trip.carModel != null && !trip.carModel.isEmpty()
                                            ? trip.carModel
                                            : trip.originCity + " → " + trip.destinationCity;
                                    item.tripName = name;
                                    tripNameCache.put(item.rating.tripId, name);
                                    if (isAdded()) {
                                        adapter.notifyDataSetChanged();
                                    }
                                }
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {}
                        });
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Adapter
    // ═══════════════════════════════════════════════════════════════════════════

    private class ReviewsAdapter extends RecyclerView.Adapter<ReviewsAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_review_card, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.bind(filteredReviews.get(position));
        }

        @Override
        public int getItemCount() {
            return filteredReviews.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            private final de.hdodenhof.circleimageview.CircleImageView ivReviewerAvatar;
            private final TextView tvReviewerName;
            private final TextView tvReviewDate;
            private final TextView tvTripTag;
            private final TextView tvReviewText;
            private final ImageView[] stars;

            ViewHolder(View itemView) {
                super(itemView);
                ivReviewerAvatar = itemView.findViewById(R.id.ivReviewerAvatar);
                tvReviewerName = itemView.findViewById(R.id.tvReviewerName);
                tvReviewDate = itemView.findViewById(R.id.tvReviewDate);
                tvTripTag = itemView.findViewById(R.id.tvTripTag);
                tvReviewText = itemView.findViewById(R.id.tvReviewText);

                stars = new ImageView[]{
                        itemView.findViewById(R.id.star1),
                        itemView.findViewById(R.id.star2),
                        itemView.findViewById(R.id.star3),
                        itemView.findViewById(R.id.star4),
                        itemView.findViewById(R.id.star5)
                };
            }

            void bind(ReviewItem item) {
                Rating rating = item.rating;

                // Reviewer name
                tvReviewerName.setText(rating.reviewerName != null ? rating.reviewerName : "User");

                // Review date - format as "Oct 2025"
                SimpleDateFormat dateFormat = new SimpleDateFormat("MMM yyyy", Locale.getDefault());
                tvReviewDate.setText(dateFormat.format(new Date(rating.createdAt)));

                // Stars
                int filledColor = ContextCompat.getColor(requireContext(), R.color.star_filled);
                int emptyColor = ContextCompat.getColor(requireContext(), R.color.star_empty);
                for (int i = 0; i < 5; i++) {
                    if (i < rating.score) {
                        stars[i].setImageResource(R.drawable.ic_star_filled);
                        stars[i].setColorFilter(filledColor);
                    } else {
                        stars[i].setImageResource(R.drawable.ic_star_empty);
                        stars[i].setColorFilter(emptyColor);
                    }
                }

                // Trip tag
                if (item.tripName != null && !item.tripName.isEmpty()) {
                    tvTripTag.setText(getString(R.string.trip_tag_format, item.tripName));
                    tvTripTag.setVisibility(View.VISIBLE);
                } else {
                    tvTripTag.setVisibility(View.GONE);
                }

                // Review text
                if (rating.comment != null && !rating.comment.isEmpty()) {
                    tvReviewText.setText("\"" + rating.comment + "\"");
                    tvReviewText.setVisibility(View.VISIBLE);
                } else {
                    tvReviewText.setVisibility(View.GONE);
                }

                // Reviewer avatar
                if (item.reviewerAvatarUrl != null && !item.reviewerAvatarUrl.isEmpty()) {
                    Glide.with(ivReviewerAvatar)
                            .load(item.reviewerAvatarUrl)
                            .placeholder(R.drawable.bg_profile_placeholder)
                            .error(R.drawable.bg_profile_placeholder)
                            .circleCrop()
                            .into(ivReviewerAvatar);
                } else {
                    ivReviewerAvatar.setImageResource(R.drawable.bg_profile_placeholder);
                }
            }
        }
    }
}

