package com.travellbudy.app.admin.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.imageview.ShapeableImageView;
import com.travellbudy.app.R;
import com.travellbudy.app.models.Trip;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Adapter for displaying trips in the admin panel with premium card design.
 */
public class AdminTripsAdapter extends RecyclerView.Adapter<AdminTripsAdapter.TripViewHolder> {

    private List<Trip> trips = new ArrayList<>();
    private Set<String> featuredIds = new HashSet<>();
    private final OnTripActionListener listener;

    public interface OnTripActionListener {
        void onTripClick(Trip trip);
        void onTripMenuClick(Trip trip, View anchor);
    }

    public AdminTripsAdapter(OnTripActionListener listener) {
        this.listener = listener;
    }

    public void submitList(List<Trip> newTrips, Set<String> newFeaturedIds) {
        this.trips = newTrips != null ? newTrips : new ArrayList<>();
        this.featuredIds = newFeaturedIds != null ? newFeaturedIds : new HashSet<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TripViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_admin_trip, parent, false);
        return new TripViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TripViewHolder holder, int position) {
        Trip trip = trips.get(position);
        boolean isFeatured = featuredIds.contains(trip.tripId);
        holder.bind(trip, isFeatured);
    }

    @Override
    public int getItemCount() {
        return trips.size();
    }

    class TripViewHolder extends RecyclerView.ViewHolder {
        private final ShapeableImageView ivTripImage;
        private final TextView tvTitle;
        private final TextView tvHost;
        private final TextView tvCategory;
        private final TextView tvStatus;
        private final ImageButton btnMenu;
        private final View badgeFeatured;

        TripViewHolder(@NonNull View itemView) {
            super(itemView);
            ivTripImage = itemView.findViewById(R.id.ivTripImage);
            tvTitle = itemView.findViewById(R.id.tvTripTitle);
            tvHost = itemView.findViewById(R.id.tvTripHost);
            tvCategory = itemView.findViewById(R.id.tvTripCategory);
            tvStatus = itemView.findViewById(R.id.tvTripStatus);
            btnMenu = itemView.findViewById(R.id.btnTripMenu);
            badgeFeatured = itemView.findViewById(R.id.badgeFeatured);
        }

        void bind(Trip trip, boolean isFeatured) {
            // Title - use carModel or destinationCity as title
            String title = trip.carModel != null && !trip.carModel.isEmpty()
                ? trip.carModel
                : (trip.destinationCity != null ? trip.destinationCity : "Untitled Trip");
            tvTitle.setText(title);

            // Host name (display name, not username)
            String hostName = trip.driverName != null ? trip.driverName : "Unknown";
            tvHost.setText(hostName);

            // Category chip - use trip activityType
            String category = getActivityTypeLabel(trip.activityType);
            tvCategory.setText(category);

            // Hide the status chip
            tvStatus.setVisibility(View.GONE);

            // Featured badge
            badgeFeatured.setVisibility(isFeatured ? View.VISIBLE : View.GONE);

            // Trip image
            if (trip.imageUrl != null && !trip.imageUrl.isEmpty()) {
                Glide.with(itemView.getContext())
                    .load(trip.imageUrl)
                    .placeholder(R.drawable.placeholder_trip)
                    .centerCrop()
                    .into(ivTripImage);
            } else {
                ivTripImage.setImageResource(R.drawable.placeholder_trip);
            }

            // Click listeners
            itemView.setOnClickListener(v -> listener.onTripClick(trip));
            btnMenu.setOnClickListener(v -> listener.onTripMenuClick(trip, btnMenu));
        }

        private String getActivityTypeLabel(String activityType) {
            if (activityType == null || activityType.isEmpty()) {
                return "ПРИКЛЮЧЕНИЕ";
            }
            switch (activityType.toLowerCase()) {
                case "hiking":
                    return "ПЕШЕХОДЕН ТУРИЗЪМ";
                case "camping":
                    return "КЪМПИНГ";
                case "road_trip":
                    return "ПЪТУВАНЕ";
                case "city_explore":
                    return "ГРАДСКИ ТУРОВЕ";
                case "festival":
                    return "ФЕСТИВАЛ";
                case "photography":
                    return "ФОТОГРАФИЯ";
                case "outdoor_sports":
                    return "СПОРТ";
                case "backpacking":
                    return "РАНИЦА";
                case "weekend":
                    return "УИКЕНД";
                default:
                    return "ПРИКЛЮЧЕНИЕ";
            }
        }
    }
}
