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
                binding.tvPrice.setText(context.getString(com.travellbudy.app.R.string.label_free));
            }

            // Date range - compact format
            if (trip.departureTime > 0 && trip.estimatedArrivalTime > 0) {
                DateTimeFormatter monthDayFormatter = DateTimeFormatter.ofPattern("d MMM", new Locale("bg"));
                DateTimeFormatter dayOnlyFormatter = DateTimeFormatter.ofPattern("d", new Locale("bg"));
                LocalDateTime startDate = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(trip.departureTime), ZoneId.systemDefault());
                LocalDateTime endDate = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(trip.estimatedArrivalTime), ZoneId.systemDefault());
                
                String dateRange;
                if (startDate.getMonth() == endDate.getMonth() && startDate.getYear() == endDate.getYear()) {
                    dateRange = startDate.format(dayOnlyFormatter) + " - " + endDate.format(monthDayFormatter);
                } else {
                    dateRange = startDate.format(monthDayFormatter) + " - " + endDate.format(monthDayFormatter);
                }
                binding.tvDate.setText(dateRange);
            } else {
                binding.tvDate.setText(context.getString(com.travellbudy.app.R.string.label_dates_tbd));
            }

            // Joined count
            int joined = trip.totalSeats - trip.availableSeats;
            binding.tvJoined.setText(context.getString(com.travellbudy.app.R.string.label_joined_count, joined, trip.totalSeats));

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
                return activityLabel;
            } else if (destination != null && !destination.isEmpty()) {
                return context.getString(com.travellbudy.app.R.string.activity_adventure_in, destination);
            } else {
                return trip.originCity + " → " + trip.destinationCity;
            }
        }

        private String getActivityLabel(String activityType) {
            if (activityType == null || activityType.isEmpty()) return null;
            switch (activityType) {
                case "hiking":          return context.getString(com.travellbudy.app.R.string.activity_label_hiking);
                case "camping":         return context.getString(com.travellbudy.app.R.string.activity_label_camping);
                case "road_trip":       return context.getString(com.travellbudy.app.R.string.activity_label_road_trip);
                case "city_explore":    return context.getString(com.travellbudy.app.R.string.activity_label_city_explore);
                case "festival":        return context.getString(com.travellbudy.app.R.string.activity_label_festival);
                case "photography":     return context.getString(com.travellbudy.app.R.string.activity_label_photography);
                case "outdoor_sports":  return context.getString(com.travellbudy.app.R.string.activity_label_outdoor_sports);
                case "backpacking":     return context.getString(com.travellbudy.app.R.string.activity_label_backpacking);
                case "weekend":         return context.getString(com.travellbudy.app.R.string.activity_label_weekend);
                default:                return null;
            }
        }

        private String getPrepositionForActivity(String activityType) {
            if (activityType == null) return context.getString(com.travellbudy.app.R.string.preposition_in);
            switch (activityType) {
                case "road_trip":       return context.getString(com.travellbudy.app.R.string.preposition_to);
                case "backpacking":     return context.getString(com.travellbudy.app.R.string.preposition_through);
                default:                return context.getString(com.travellbudy.app.R.string.preposition_in);
            }
        }
    }
}

