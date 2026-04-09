package com.travellbudy.app.ui.trip;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.travellbudy.app.R;
import com.travellbudy.app.models.Trip;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Adapter for displaying user's adventures in a horizontal list on their profile.
 */
public class UserAdventuresAdapter extends RecyclerView.Adapter<UserAdventuresAdapter.ViewHolder> {

    private final List<Trip> trips;
    private final OnTripClickListener listener;

    public interface OnTripClickListener {
        void onTripClick(Trip trip);
    }

    public UserAdventuresAdapter(List<Trip> trips, OnTripClickListener listener) {
        this.trips = trips;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_user_adventure, parent, false);
        return new ViewHolder(view);
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
        private final ImageView ivImage;
        private final TextView tvTitle;
        private final TextView tvDate;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivImage = itemView.findViewById(R.id.ivAdventureImage);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvDate = itemView.findViewById(R.id.tvDate);
        }

        void bind(Trip trip) {
            // Title
            String title = trip.carModel != null && !trip.carModel.isEmpty()
                    ? trip.carModel
                    : trip.destinationCity;
            tvTitle.setText(title);

            // Date
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault());
            LocalDateTime startDate = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(trip.departureTime), ZoneId.systemDefault());
            tvDate.setText(startDate.format(formatter));

            // Image
            if (trip.imageUrl != null && !trip.imageUrl.isEmpty()) {
                Glide.with(itemView.getContext())
                        .load(trip.imageUrl)
                        .centerCrop()
                        .error(R.drawable.placeholder_adventure_1)
                        .into(ivImage);
            } else {
                ivImage.setImageResource(R.drawable.placeholder_adventure_1);
            }

            // Click
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onTripClick(trip);
                }
            });
        }
    }
}

