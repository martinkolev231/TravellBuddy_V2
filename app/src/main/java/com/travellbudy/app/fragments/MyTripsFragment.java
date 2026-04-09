package com.travellbudy.app.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.travellbudy.app.R;
import com.travellbudy.app.TripDetailsActivity;
import com.travellbudy.app.databinding.FragmentMyTripsBinding;
import com.travellbudy.app.databinding.ItemMyAdventureCardBinding;
import com.travellbudy.app.models.SeatRequest;
import com.travellbudy.app.models.Trip;
import com.travellbudy.app.util.DateTimeUtils;

import java.util.ArrayList;
import java.util.List;

public class MyTripsFragment extends Fragment {

    private FragmentMyTripsBinding binding;
    private MyAdventureAdapter adapter;
    private final List<Trip> trips = new ArrayList<>();
    private String currentUserId;
    
    // Tab states: 0 = Joined, 1 = Hosted, 2 = Saved
    private int currentTab = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentMyTripsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        }

        // Setup toolbar back navigation
        binding.toolbar.setNavigationOnClickListener(v -> {
            Navigation.findNavController(v).navigateUp();
        });

        adapter = new MyAdventureAdapter();
        binding.rvTrips.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvTrips.setAdapter(adapter);

        // Setup tab clicks
        setupTabListeners();
        
        // Select Joined tab by default
        selectTab(0);
        loadTrips();
    }

    private void setupTabListeners() {
        binding.tabJoined.setOnClickListener(v -> {
            selectTab(0);
            loadTrips();
        });

        binding.tabHosted.setOnClickListener(v -> {
            selectTab(1);
            loadTrips();
        });

        binding.tabSaved.setOnClickListener(v -> {
            selectTab(2);
            loadTrips();
        });
    }

    private void selectTab(int tabIndex) {
        currentTab = tabIndex;

        // Reset all tabs to unselected state
        binding.tabJoined.setBackgroundResource(R.drawable.bg_tab_unselected);
        binding.tabJoined.setTextColor(getResources().getColor(R.color.text_secondary, null));
        binding.tabJoined.setTypeface(null, android.graphics.Typeface.NORMAL);

        binding.tabHosted.setBackgroundResource(R.drawable.bg_tab_unselected);
        binding.tabHosted.setTextColor(getResources().getColor(R.color.text_secondary, null));
        binding.tabHosted.setTypeface(null, android.graphics.Typeface.NORMAL);

        binding.tabSaved.setBackgroundResource(R.drawable.bg_tab_unselected);
        binding.tabSaved.setTextColor(getResources().getColor(R.color.text_secondary, null));
        binding.tabSaved.setTypeface(null, android.graphics.Typeface.NORMAL);

        // Highlight selected tab
        TextView selectedTab;
        switch (tabIndex) {
            case 1:
                selectedTab = binding.tabHosted;
                break;
            case 2:
                selectedTab = binding.tabSaved;
                break;
            default:
                selectedTab = binding.tabJoined;
                break;
        }
        selectedTab.setBackgroundResource(R.drawable.bg_tab_selected);
        selectedTab.setTextColor(getResources().getColor(R.color.text_primary, null));
        selectedTab.setTypeface(null, android.graphics.Typeface.BOLD);
    }

    private void loadTrips() {
        if (currentUserId == null) return;

        switch (currentTab) {
            case 0: // Joined
                loadJoinedTrips();
                break;
            case 1: // Hosted
                loadHostedTrips();
                break;
            case 2: // Saved
                loadSavedTrips();
                break;
        }
    }

    private void loadHostedTrips() {
        // Load trips where user is the driver/host
        FirebaseDatabase.getInstance().getReference("trips")
                .orderByChild("driverUid")
                .equalTo(currentUserId)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        trips.clear();
                        for (DataSnapshot child : snapshot.getChildren()) {
                            Trip trip = child.getValue(Trip.class);
                            if (trip != null) trips.add(trip);
                        }
                        adapter.notifyDataSetChanged();
                        updateEmptyState(R.string.label_no_driver_trips);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                    }
                });
    }

    private void loadJoinedTrips() {
        // Load trips where user has an approved request
        FirebaseDatabase.getInstance().getReference("trips")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        trips.clear();
                        for (DataSnapshot child : snapshot.getChildren()) {
                            Trip trip = child.getValue(Trip.class);
                            if (trip == null) continue;

                            // Check tripRequests for this user
                            FirebaseDatabase.getInstance().getReference("tripRequests")
                                    .child(trip.tripId)
                                    .orderByChild("riderUid")
                                    .equalTo(currentUserId)
                                    .addListenerForSingleValueEvent(new ValueEventListener() {
                                        @Override
                                        public void onDataChange(@NonNull DataSnapshot reqSnapshot) {
                                            for (DataSnapshot reqSnap : reqSnapshot.getChildren()) {
                                                SeatRequest req = reqSnap.getValue(SeatRequest.class);
                                                if (req != null && ("approved".equals(req.status)
                                                        || "pending".equals(req.status))) {
                                                    trips.add(trip);
                                                    adapter.notifyDataSetChanged();
                                                    updateEmptyState(R.string.label_no_rider_trips);
                                                    break;
                                                }
                                            }
                                        }

                                        @Override
                                        public void onCancelled(@NonNull DatabaseError error) {
                                        }
                                    });
                        }
                        adapter.notifyDataSetChanged();
                        updateEmptyState(R.string.label_no_rider_trips);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                    }
                });
    }

    private void loadSavedTrips() {
        // Load trips from user's savedTrips node
        FirebaseDatabase.getInstance().getReference("users")
                .child(currentUserId)
                .child("savedTrips")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        trips.clear();
                        for (DataSnapshot child : snapshot.getChildren()) {
                            String tripId = child.getKey();
                            if (tripId != null) {
                                // Fetch trip details
                                FirebaseDatabase.getInstance().getReference("trips")
                                        .child(tripId)
                                        .addListenerForSingleValueEvent(new ValueEventListener() {
                                            @Override
                                            public void onDataChange(@NonNull DataSnapshot tripSnapshot) {
                                                Trip trip = tripSnapshot.getValue(Trip.class);
                                                if (trip != null) {
                                                    trips.add(trip);
                                                    adapter.notifyDataSetChanged();
                                                    updateEmptyState(R.string.label_no_saved_trips);
                                                }
                                            }

                                            @Override
                                            public void onCancelled(@NonNull DatabaseError error) {
                                            }
                                        });
                            }
                        }
                        adapter.notifyDataSetChanged();
                        updateEmptyState(R.string.label_no_saved_trips);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                    }
                });
    }

    private void updateEmptyState(int emptyTextResId) {
        binding.tvEmptyTitle.setText(emptyTextResId);
        binding.tvEmpty.setVisibility(trips.isEmpty() ? View.VISIBLE : View.GONE);
        binding.rvTrips.setVisibility(trips.isEmpty() ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private class MyAdventureAdapter extends RecyclerView.Adapter<MyAdventureAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemMyAdventureCardBinding itemBinding = ItemMyAdventureCardBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new VH(itemBinding);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            holder.bind(trips.get(position));
        }

        @Override
        public int getItemCount() {
            return trips.size();
        }

        class VH extends RecyclerView.ViewHolder {
            private final ItemMyAdventureCardBinding itemBinding;

            VH(ItemMyAdventureCardBinding itemBinding) {
                super(itemBinding.getRoot());
                this.itemBinding = itemBinding;
            }

            void bind(Trip trip) {
                // Title
                String title = trip.carModel != null && !trip.carModel.isEmpty()
                        ? trip.carModel
                        : trip.originCity + " → " + trip.destinationCity;
                itemBinding.tvTitle.setText(title);

                // Price badge
                if (trip.pricePerSeat > 0) {
                    itemBinding.tvPrice.setText(String.format(java.util.Locale.getDefault(), "€ %.0f", trip.pricePerSeat));
                } else {
                    itemBinding.tvPrice.setText("Free");
                }

                // Date - format as "Oct 10 - Oct 14" style with both months
                java.time.format.DateTimeFormatter dateFormatter = java.time.format.DateTimeFormatter.ofPattern("MMM d", java.util.Locale.getDefault());
                java.time.LocalDateTime startDate = java.time.LocalDateTime.ofInstant(
                        java.time.Instant.ofEpochMilli(trip.departureTime), java.time.ZoneId.systemDefault());
                java.time.LocalDateTime endDate = java.time.LocalDateTime.ofInstant(
                        java.time.Instant.ofEpochMilli(trip.estimatedArrivalTime), java.time.ZoneId.systemDefault());
                String dateRange = startDate.format(dateFormatter) + " - " + endDate.format(dateFormatter);
                itemBinding.tvDate.setText(dateRange);

                // Joined count
                int joined = trip.totalSeats - trip.availableSeats;
                itemBinding.tvJoined.setText(String.format(java.util.Locale.getDefault(), "%d/%d joined", joined, trip.totalSeats));

                // Status badge - different for hosted vs joined/saved trips
                boolean isHostedByMe = currentUserId != null && currentUserId.equals(trip.driverUid);
                
                if (isHostedByMe) {
                    // User's own adventure
                    itemBinding.tvStatusBadge.setText("HOSTED BY YOU");
                    itemBinding.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_hosted);
                } else {
                    // Someone else's adventure (joined or saved)
                    int spotsLeft = trip.availableSeats;
                    if (spotsLeft > 0) {
                        itemBinding.tvStatusBadge.setText(String.format(java.util.Locale.getDefault(), 
                                "ONLY %d SPOT%s LEFT", spotsLeft, spotsLeft == 1 ? "" : "S"));
                        itemBinding.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_spots_left);
                    } else {
                        itemBinding.tvStatusBadge.setText("FULLY BOOKED");
                        itemBinding.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_spots_left);
                    }
                }
                itemBinding.tvStatusBadge.setVisibility(View.VISIBLE);

                // Load image
                if (trip.imageUrl != null && !trip.imageUrl.isEmpty()) {
                    Glide.with(itemBinding.ivAdventureImage)
                            .load(trip.imageUrl)
                            .centerCrop()
                            .error(R.drawable.bg_adventure_image)
                            .into(itemBinding.ivAdventureImage);
                } else {
                    // Set background by activity type
                    if (trip.activityType != null) {
                        switch (trip.activityType) {
                            case "hiking":
                                itemBinding.ivAdventureImage.setBackgroundResource(R.drawable.bg_adventure_hiking);
                                break;
                            case "camping":
                                itemBinding.ivAdventureImage.setBackgroundResource(R.drawable.bg_adventure_camping);
                                break;
                            case "road_trip":
                                itemBinding.ivAdventureImage.setBackgroundResource(R.drawable.bg_adventure_road);
                                break;
                            case "city_explore":
                                itemBinding.ivAdventureImage.setBackgroundResource(R.drawable.bg_adventure_city);
                                break;
                            case "festival":
                                itemBinding.ivAdventureImage.setBackgroundResource(R.drawable.bg_adventure_festival);
                                break;
                            default:
                                itemBinding.ivAdventureImage.setBackgroundResource(R.drawable.bg_adventure_image);
                                break;
                        }
                    } else {
                        itemBinding.ivAdventureImage.setBackgroundResource(R.drawable.bg_adventure_image);
                    }
                }

                // Click to open details
                itemBinding.getRoot().setOnClickListener(v -> {
                    Intent intent = new Intent(requireContext(), TripDetailsActivity.class);
                    intent.putExtra(TripDetailsActivity.EXTRA_TRIP_ID, trip.tripId);
                    startActivity(intent);
                });
            }
        }
    }
}
