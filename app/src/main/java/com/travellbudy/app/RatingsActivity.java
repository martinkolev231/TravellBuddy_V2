package com.travellbudy.app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RatingBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.travellbudy.app.databinding.ActivityRatingsBinding;
import com.travellbudy.app.models.Rating;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RatingsActivity extends BaseActivity {

    public static final String EXTRA_USER_ID = "user_id";

    private ActivityRatingsBinding binding;
    private RatingsAdapter adapter;
    private final List<Rating> ratings = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRatingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        String userId = getIntent().getStringExtra(EXTRA_USER_ID);
        if (userId == null) {
            // Default to current user
            if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            } else {
                finish();
                return;
            }
        }

        adapter = new RatingsAdapter();
        binding.rvRatings.setLayoutManager(new LinearLayoutManager(this));
        binding.rvRatings.setAdapter(adapter);

        loadRatings(userId);
    }

    private void loadRatings(String userId) {
        // Ratings are keyed by tripId: /ratings/{tripId}/{ratingId}
        // To find all ratings for a user, we scan all trip rating nodes
        FirebaseDatabase.getInstance().getReference("ratings")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        ratings.clear();
                        for (DataSnapshot tripSnap : snapshot.getChildren()) {
                            for (DataSnapshot ratingSnap : tripSnap.getChildren()) {
                                Rating rating = ratingSnap.getValue(Rating.class);
                                if (rating != null && userId.equals(rating.revieweeUid)) {
                                    ratings.add(rating);
                                }
                            }
                        }
                        adapter.notifyDataSetChanged();
                        binding.tvEmpty.setVisibility(ratings.isEmpty() ? View.VISIBLE : View.GONE);
                        binding.rvRatings.setVisibility(ratings.isEmpty() ? View.GONE : View.VISIBLE);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                    }
                });
    }

    private class RatingsAdapter extends RecyclerView.Adapter<RatingsAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_rating, parent, false);
            return new VH(view);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Rating rating = ratings.get(position);
            holder.tvAuthorName.setText(rating.reviewerName);
            holder.ratingBar.setRating(rating.score);
            holder.tvComment.setText(rating.comment);
            holder.tvComment.setVisibility(rating.comment != null && !rating.comment.isEmpty()
                    ? View.VISIBLE : View.GONE);

            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault());
            LocalDateTime date = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(rating.createdAt), ZoneId.systemDefault());
            holder.tvDate.setText(date.format(dtf));
        }

        @Override
        public int getItemCount() {
            return ratings.size();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView tvAuthorName, tvComment, tvDate;
            RatingBar ratingBar;

            VH(@NonNull View itemView) {
                super(itemView);
                tvAuthorName = itemView.findViewById(R.id.tvAuthorName);
                ratingBar = itemView.findViewById(R.id.ratingBar);
                tvComment = itemView.findViewById(R.id.tvComment);
                tvDate = itemView.findViewById(R.id.tvDate);
            }
        }
    }
}

