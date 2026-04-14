package com.travellbudy.app.ui.trip;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.travellbudy.app.databinding.ItemYourAdventureCardBinding;
import com.travellbudy.app.models.Trip;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Adapter for displaying user's own adventures with "HOSTED BY YOU" badge.
 */
public class YourAdventureAdapter extends RecyclerView.Adapter<YourAdventureAdapter.ViewHolder> {

    private final List<Trip> trips = new ArrayList<>();
    private OnAdventureClickListener listener;

    public interface OnAdventureClickListener {
        void onAdventureClick(Trip trip);
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
        ItemYourAdventureCardBinding binding = ItemYourAdventureCardBinding.inflate(
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
        private final ItemYourAdventureCardBinding binding;
        private final Context context;

        ViewHolder(ItemYourAdventureCardBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            this.context = binding.getRoot().getContext();
            
            // Force white background color on the card using ColorStateList
            android.content.res.ColorStateList whiteColor = android.content.res.ColorStateList.valueOf(0xFFFFFFFF);
            binding.getRoot().setCardBackgroundColor(whiteColor);
            binding.getRoot().setBackgroundTintList(null);
        }

        void bind(Trip trip) {
            // Title
            String title = trip.carModel != null && !trip.carModel.isEmpty()
                    ? trip.carModel
                    : generateTitle(trip);
            binding.tvTitle.setText(title);

            // Price badge
            if (trip.pricePerSeat > 0) {
                binding.tvPrice.setText(String.format(Locale.getDefault(), "€ %.0f", trip.pricePerSeat));
            } else {
                binding.tvPrice.setText("Free");
            }

            // Date range - compact format
            if (trip.departureTime > 0 && trip.estimatedArrivalTime > 0) {
                DateTimeFormatter monthDayFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);
                DateTimeFormatter dayOnlyFormatter = DateTimeFormatter.ofPattern("d", Locale.ENGLISH);
                LocalDateTime startDate = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(trip.departureTime), ZoneId.systemDefault());
                LocalDateTime endDate = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(trip.estimatedArrivalTime), ZoneId.systemDefault());
                
                String dateRange;
                if (startDate.getMonth() == endDate.getMonth() && startDate.getYear() == endDate.getYear()) {
                    dateRange = startDate.format(monthDayFormatter) + " - " + endDate.format(dayOnlyFormatter);
                } else {
                    dateRange = startDate.format(monthDayFormatter) + " - " + endDate.format(monthDayFormatter);
                }
                binding.tvDate.setText(dateRange);
            } else {
                binding.tvDate.setText("Dates TBD");
            }

            // Joined count
            int joined = trip.totalSeats - trip.availableSeats;
            binding.tvJoined.setText(String.format(Locale.getDefault(), "%d/%d joined", joined, trip.totalSeats));

            // Always show "HOSTED" badge (using string resource)
            binding.tvStatusBadge.setText(context.getString(com.travellbudy.app.R.string.role_hosted));
            binding.tvStatusBadge.setVisibility(View.VISIBLE);

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
                }
            });
        }


        private String generateTitle(Trip trip) {
            String activityLabel = getActivityLabel(trip.activityType);
            String destination = trip.destinationCity != null && !trip.destinationCity.isEmpty()
                    ? trip.destinationCity : trip.originCity;

            if (activityLabel != null && destination != null && !destination.isEmpty()) {
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

