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
                binding.tvPrice.setText("Free");
            }

            // Date - format as "Aug 12 - Aug 16" style with both months
            java.time.format.DateTimeFormatter dateFormatter = java.time.format.DateTimeFormatter.ofPattern("MMM d", java.util.Locale.getDefault());
            java.time.LocalDateTime startDate = java.time.LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(trip.departureTime), java.time.ZoneId.systemDefault());
            java.time.LocalDateTime endDate = java.time.LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(trip.estimatedArrivalTime), java.time.ZoneId.systemDefault());
            String dateRange = startDate.format(dateFormatter) + " - " + endDate.format(dateFormatter);
            binding.tvDate.setText(dateRange);

            // Joined count
            int joined = trip.totalSeats - trip.availableSeats;
            binding.tvJoined.setText(String.format(java.util.Locale.getDefault(), "%d/%d joined", joined, trip.totalSeats));

            // Status badge - show ONLY X SPOTS LEFT or WAITLIST
            int spotsLeft = trip.availableSeats;
            if (spotsLeft > 0) {
                binding.tvStatusBadge.setText(String.format(java.util.Locale.getDefault(), 
                        "ONLY %d SPOT%s LEFT", spotsLeft, spotsLeft == 1 ? "" : "S"));
                binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_spots_left);
                binding.tvStatusBadge.setVisibility(View.VISIBLE);
            } else {
                binding.tvStatusBadge.setText("WAITLIST");
                binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_waitlist);
                binding.tvStatusBadge.setVisibility(View.VISIBLE);
            }

            // Load organizer profile photo - always show when available
            if (trip.driverPhotoUrl != null && !trip.driverPhotoUrl.isEmpty()) {
                binding.ivOrganizerPhoto.setVisibility(View.VISIBLE);
                binding.btnSave.setVisibility(View.GONE);
                Glide.with(context)
                        .load(trip.driverPhotoUrl)
                        .placeholder(R.drawable.bg_profile_placeholder)
                        .error(R.drawable.bg_profile_placeholder)
                        .circleCrop()
                        .into(binding.ivOrganizerPhoto);
            } else {
                binding.ivOrganizerPhoto.setVisibility(View.GONE);
                binding.btnSave.setVisibility(View.GONE);
            }

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
         * e.g., "Hiking in Switzerland", "Road Trip to Paris"
         */
        private String generateTitle(Trip trip) {
            String activityLabel = getActivityLabel(trip.activityType);
            String destination = trip.destinationCity != null && !trip.destinationCity.isEmpty()
                    ? trip.destinationCity : trip.originCity;

            if (activityLabel != null && destination != null && !destination.isEmpty()) {
                // Format: "Activity in/to Destination"
                String preposition = getPrepositionForActivity(trip.activityType);
                return activityLabel + " " + preposition + " " + destination;
            } else if (activityLabel != null) {
                return activityLabel + " Adventure";
            } else if (destination != null && !destination.isEmpty()) {
                return "Adventure in " + destination;
            } else {
                return trip.originCity + " → " + trip.destinationCity;
            }
        }

        private String getActivityLabel(String activityType) {
            if (activityType == null || activityType.isEmpty()) return null;
            switch (activityType) {
                case "hiking":          return "Hiking";
                case "camping":         return "Camping";
                case "road_trip":       return "Road Trip";
                case "city_explore":    return "City Exploration";
                case "festival":        return "Festival";
                case "photography":     return "Photography Trip";
                case "outdoor_sports":  return "Outdoor Sports";
                case "backpacking":     return "Backpacking";
                case "weekend":         return "Weekend Getaway";
                default:                return null;
            }
        }

        private String getPrepositionForActivity(String activityType) {
            if (activityType == null) return "in";
            switch (activityType) {
                case "road_trip":       return "to";
                case "backpacking":     return "through";
                default:                return "in";
            }
        }
    }
}


