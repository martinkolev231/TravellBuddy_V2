package com.travellbudy.app.ui.trip;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.travellbudy.app.R;
import com.travellbudy.app.TripDetailsActivity;
import com.travellbudy.app.UserProfileActivity;
import com.travellbudy.app.databinding.ItemFeaturedAdventureBinding;
import com.travellbudy.app.models.Trip;
import com.travellbudy.app.util.DateTimeUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for displaying featured adventure cards with immersive image-based design.
 * Uses item_featured_adventure.xml layout for large, visually striking cards.
 */
public class FeaturedAdventureAdapter extends RecyclerView.Adapter<FeaturedAdventureAdapter.ViewHolder> {

    private final List<Trip> trips = new ArrayList<>();
    private OnAdventureClickListener listener;

    public interface OnAdventureClickListener {
        void onAdventureClick(Trip trip);
        void onJoinClick(Trip trip);
    }

    public void setOnAdventureClickListener(OnAdventureClickListener listener) {
        this.listener = listener;
    }

    public void submitList(List<Trip> newTrips) {
        trips.clear();
        if (newTrips != null) {
            trips.addAll(newTrips);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemFeaturedAdventureBinding binding = ItemFeaturedAdventureBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(trips.get(position));
    }

    @Override
    public int getItemCount() {
        return trips.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemFeaturedAdventureBinding binding;
        private final Context context;

        ViewHolder(ItemFeaturedAdventureBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            this.context = binding.getRoot().getContext();
            
            // Force pure white background - override any Material3 harmonization
            android.content.res.ColorStateList whiteColor = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE);
            android.content.res.ColorStateList transparent = android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT);
            binding.getRoot().setCardBackgroundColor(whiteColor);
            binding.getRoot().setBackgroundTintList(null);
            binding.getRoot().setForegroundTintList(null);
            // Disable surface tint / ripple foreground overlay
            binding.getRoot().setRippleColor(transparent);
            binding.getRoot().setCardForegroundColor(transparent);
        }

        void bind(Trip trip) {
            // Title: use carModel if available, otherwise generate from activity type + destination
            String title = trip.carModel != null && !trip.carModel.isEmpty()
                    ? trip.carModel
                    : generateTitle(trip);
            binding.tvTitle.setText(title);

            // Price badge
            if (trip.pricePerSeat > 0) {
                binding.tvPrice.setText(String.format(java.util.Locale.getDefault(), "€ %.0f", trip.pricePerSeat));
            } else {
                binding.tvPrice.setText(context.getString(R.string.label_free));
            }

            // Date - format as "Apr 28 - 30" (same month) or "Apr 28 - May 2"
            if (trip.departureTime > 0 && trip.estimatedArrivalTime > 0) {
                java.time.format.DateTimeFormatter monthDayFormatter = java.time.format.DateTimeFormatter.ofPattern("d MMM", new java.util.Locale("bg"));
                java.time.format.DateTimeFormatter dayOnlyFormatter = java.time.format.DateTimeFormatter.ofPattern("d", new java.util.Locale("bg"));
                java.time.LocalDateTime startDate = java.time.LocalDateTime.ofInstant(
                        java.time.Instant.ofEpochMilli(trip.departureTime), java.time.ZoneId.systemDefault());
                java.time.LocalDateTime endDate = java.time.LocalDateTime.ofInstant(
                        java.time.Instant.ofEpochMilli(trip.estimatedArrivalTime), java.time.ZoneId.systemDefault());
                
                String dateRange;
                if (startDate.getMonth() == endDate.getMonth() && startDate.getYear() == endDate.getYear()) {
                    dateRange = startDate.format(dayOnlyFormatter) + " - " + endDate.format(monthDayFormatter);
                } else {
                    dateRange = startDate.format(monthDayFormatter) + " - " + endDate.format(monthDayFormatter);
                }
                binding.tvDate.setText(dateRange);
            } else {
                binding.tvDate.setText(context.getString(R.string.label_dates_tbd));
            }

            // Joined count
            int joined = trip.totalSeats - trip.availableSeats;
            binding.tvJoined.setText(context.getString(R.string.label_joined_count, joined, trip.totalSeats));

            // Status badge - show X SPOTS LEFT (pink) or FULL (dark navy)
            int spotsLeft = trip.availableSeats;
            if (spotsLeft > 0) {
                // Format: "ОСТАВАТ 2 МЕСТА" or "ОСТАВА 1 МЯСТО"
                if (spotsLeft == 1) {
                    binding.tvStatusBadge.setText(context.getString(R.string.label_spot_left_badge, spotsLeft));
                } else {
                    binding.tvStatusBadge.setText(context.getString(R.string.label_spots_left_badge, spotsLeft));
                }
                binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_spots_pink);
                binding.tvStatusBadge.setVisibility(View.VISIBLE);
            } else {
                // Trip is full - show FULL badge with dark navy background
                binding.tvStatusBadge.setText(context.getString(R.string.label_full));
                binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_full_dark);
                binding.tvStatusBadge.setVisibility(View.VISIBLE);
            }

            // Hide organizer photo to match reference design (cleaner look)
            binding.ivOrganizerPhoto.setVisibility(View.GONE);
            binding.btnSave.setVisibility(View.GONE);

            // Load adventure image
            Glide.with(context)
                    .load(trip.imageUrl)
                    .centerCrop()
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(binding.ivAdventureImage);

            // Click to open details
            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) {
                    listener.onAdventureClick(trip);
                } else {
                    Intent intent = new Intent(context, TripDetailsActivity.class);
                    intent.putExtra(TripDetailsActivity.EXTRA_TRIP_ID, trip.tripId);
                    context.startActivity(intent);
                }
            });
        }


        /**
         * Generate a proper title from activity type and destination.
         * e.g., "Поход в Швейцария", "Пътуване с кола до Париж"
         */
        private String generateTitle(Trip trip) {
            String activityLabel = getActivityLabel(trip.activityType);
            String destination = trip.destinationCity != null && !trip.destinationCity.isEmpty()
                    ? trip.destinationCity : trip.originCity;

            if (activityLabel != null && destination != null && !destination.isEmpty()) {
                // Format: "Дейност в/до Дестинация"
                String preposition = getPrepositionForActivity(trip.activityType);
                return activityLabel + " " + preposition + " " + destination;
            } else if (activityLabel != null) {
                return activityLabel;
            } else if (destination != null && !destination.isEmpty()) {
                return context.getString(R.string.activity_adventure_in, destination);
            } else {
                return trip.originCity + " → " + trip.destinationCity;
            }
        }

        private String getActivityLabel(String activityType) {
            if (activityType == null || activityType.isEmpty()) return null;
            switch (activityType) {
                case "hiking":          return context.getString(R.string.activity_label_hiking);
                case "camping":         return context.getString(R.string.activity_label_camping);
                case "road_trip":       return context.getString(R.string.activity_label_road_trip);
                case "city_explore":    return context.getString(R.string.activity_label_city_explore);
                case "festival":        return context.getString(R.string.activity_label_festival);
                case "photography":     return context.getString(R.string.activity_label_photography);
                case "outdoor_sports":  return context.getString(R.string.activity_label_outdoor_sports);
                case "backpacking":     return context.getString(R.string.activity_label_backpacking);
                case "weekend":         return context.getString(R.string.activity_label_weekend);
                default:                return null;
            }
        }

        private String getPrepositionForActivity(String activityType) {
            if (activityType == null) return context.getString(R.string.preposition_in);
            switch (activityType) {
                case "road_trip":       return context.getString(R.string.preposition_to);
                case "backpacking":     return context.getString(R.string.preposition_through);
                default:                return context.getString(R.string.preposition_in);
            }
        }
    }
}


