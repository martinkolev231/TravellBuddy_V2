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
import com.google.firebase.database.DatabaseReference;
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
    
    // Firebase listener tracking
    private DatabaseReference currentTripsRef;
    private ValueEventListener currentTripsListener;

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

        // Setup back button navigation
        view.findViewById(R.id.btnBack).setOnClickListener(v -> {
            Navigation.findNavController(v).navigateUp();
        });

        adapter = new MyAdventureAdapter();
        binding.rvTrips.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvTrips.setAdapter(adapter);

        // Setup tab clicks
        setupTabListeners();
        
        // Select Joined tab by default
        selectTab(0);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Reload trips when returning to this fragment (e.g., after deleting a trip)
        loadTrips();
    }
    
    @Override
    public void onPause() {
        super.onPause();
        // Remove listener when fragment is paused
        removeCurrentListener();
    }
    
    private void removeCurrentListener() {
        if (currentTripsRef != null && currentTripsListener != null) {
            currentTripsRef.removeEventListener(currentTripsListener);
            currentTripsRef = null;
            currentTripsListener = null;
        }
    }

    private void setupTabListeners() {
        binding.tabJoined.setOnClickListener(v -> {
            selectTab(0);
            loadTrips();
        });

        binding.tabComplete.setOnClickListener(v -> {
            selectTab(1);
            loadTrips();
        });

        binding.tabFavorites.setOnClickListener(v -> {
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

        binding.tabComplete.setBackgroundResource(R.drawable.bg_tab_unselected);
        binding.tabComplete.setTextColor(getResources().getColor(R.color.text_secondary, null));
        binding.tabComplete.setTypeface(null, android.graphics.Typeface.NORMAL);

        binding.tabFavorites.setBackgroundResource(R.drawable.bg_tab_unselected);
        binding.tabFavorites.setTextColor(getResources().getColor(R.color.text_secondary, null));
        binding.tabFavorites.setTypeface(null, android.graphics.Typeface.NORMAL);

        // Highlight selected tab
        TextView selectedTab;
        switch (tabIndex) {
            case 1:
                selectedTab = binding.tabComplete;
                break;
            case 2:
                selectedTab = binding.tabFavorites;
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
        
        // Remove existing listener before starting a new one
        removeCurrentListener();

        switch (currentTab) {
            case 0: // Joined
                loadJoinedTrips();
                break;
            case 1: // Complete
                loadCompletedTrips();
                break;
            case 2: // Favorites
                loadSavedTrips();
                break;
        }
    }

    private void loadCompletedTrips() {
        // Load completed trips where user participated
        trips.clear();
        safeNotifyAdapter();
        
        currentTripsRef = FirebaseDatabase.getInstance().getReference("trips");
        currentTripsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded() || binding == null) return;
                
                List<Trip> driverTrips = new ArrayList<>();
                List<Trip> tripsToCheck = new ArrayList<>();
                
                for (DataSnapshot child : snapshot.getChildren()) {
                    Trip trip = child.getValue(Trip.class);
                    if (trip != null && "completed".equals(trip.status)) {
                        // Check if user was the driver
                        if (currentUserId.equals(trip.driverUid)) {
                            driverTrips.add(trip);
                        } else if (trip.tripId != null) {
                            tripsToCheck.add(trip);
                        }
                    }
                }
                
                if (tripsToCheck.isEmpty()) {
                    trips.clear();
                    trips.addAll(driverTrips);
                    safeNotifyAdapter();
                    updateEmptyState(R.string.label_no_completed_trips);
                    return;
                }
                
                final int[] pending = {tripsToCheck.size()};
                final List<Trip> riderTrips = new ArrayList<>();
                final List<Trip> finalDriverTrips = driverTrips;
                
                for (Trip trip : tripsToCheck) {
                    FirebaseDatabase.getInstance().getReference("tripRequests")
                            .child(trip.tripId)
                            .orderByChild("riderUid")
                            .equalTo(currentUserId)
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot reqSnapshot) {
                                    if (!isAdded() || binding == null) return;
                                    
                                    for (DataSnapshot reqSnap : reqSnapshot.getChildren()) {
                                        SeatRequest req = reqSnap.getValue(SeatRequest.class);
                                        if (req != null && "approved".equals(req.status)) {
                                            riderTrips.add(trip);
                                            break;
                                        }
                                    }
                                    pending[0]--;
                                    if (pending[0] <= 0) {
                                        trips.clear();
                                        trips.addAll(finalDriverTrips);
                                        trips.addAll(riderTrips);
                                        safeNotifyAdapter();
                                        updateEmptyState(R.string.label_no_completed_trips);
                                    }
                                }

                                @Override
                                public void onCancelled(@NonNull DatabaseError error) {
                                    if (!isAdded() || binding == null) return;
                                    
                                    pending[0]--;
                                    if (pending[0] <= 0) {
                                        trips.clear();
                                        trips.addAll(finalDriverTrips);
                                        trips.addAll(riderTrips);
                                        safeNotifyAdapter();
                                        updateEmptyState(R.string.label_no_completed_trips);
                                    }
                                }
                            });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                updateEmptyState(R.string.label_no_completed_trips);
            }
        };
        
        currentTripsRef.addValueEventListener(currentTripsListener);
    }

    private void loadJoinedTrips() {
        // Load trips where user has an approved request
        trips.clear();
        safeNotifyAdapter();
        
        currentTripsRef = FirebaseDatabase.getInstance().getReference("trips");
        currentTripsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded() || binding == null) return;
                
                List<Trip> allTrips = new ArrayList<>();
                
                for (DataSnapshot child : snapshot.getChildren()) {
                    Trip trip = child.getValue(Trip.class);
                    if (trip != null && trip.tripId != null) {
                        allTrips.add(trip);
                    }
                }
                
                if (allTrips.isEmpty()) {
                    trips.clear();
                    safeNotifyAdapter();
                    updateEmptyState(R.string.label_no_rider_trips);
                    return;
                }
                
                // Track which trips to add
                final int[] pending = {allTrips.size()};
                final List<Trip> joinedTrips = new ArrayList<>();
                
                for (Trip trip : allTrips) {
                    FirebaseDatabase.getInstance().getReference("tripRequests")
                            .child(trip.tripId)
                            .orderByChild("riderUid")
                            .equalTo(currentUserId)
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot reqSnapshot) {
                                    if (!isAdded() || binding == null) return;
                                    
                                    for (DataSnapshot reqSnap : reqSnapshot.getChildren()) {
                                        SeatRequest req = reqSnap.getValue(SeatRequest.class);
                                        if (req != null && ("approved".equals(req.status)
                                                || "pending".equals(req.status))) {
                                            joinedTrips.add(trip);
                                            break;
                                        }
                                    }
                                    
                                    pending[0]--;
                                    if (pending[0] <= 0) {
                                        // All trips checked, update UI
                                        trips.clear();
                                        trips.addAll(joinedTrips);
                                        safeNotifyAdapter();
                                        updateEmptyState(R.string.label_no_rider_trips);
                                    }
                                }

                                @Override
                                public void onCancelled(@NonNull DatabaseError error) {
                                    if (!isAdded() || binding == null) return;
                                    
                                    pending[0]--;
                                    if (pending[0] <= 0) {
                                        trips.clear();
                                        trips.addAll(joinedTrips);
                                        safeNotifyAdapter();
                                        updateEmptyState(R.string.label_no_rider_trips);
                                    }
                                }
                            });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                updateEmptyState(R.string.label_no_rider_trips);
            }
        };
        
        currentTripsRef.addValueEventListener(currentTripsListener);
    }

    private void loadSavedTrips() {
        // Load trips from user's savedTrips node
        trips.clear();
        safeNotifyAdapter();
        
        currentTripsRef = FirebaseDatabase.getInstance().getReference("users")
                .child(currentUserId)
                .child("savedTrips");
        currentTripsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded() || binding == null) return;
                
                List<String> tripIds = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    String tripId = child.getKey();
                    if (tripId != null) {
                        tripIds.add(tripId);
                    }
                }
                
                if (tripIds.isEmpty()) {
                    trips.clear();
                    safeNotifyAdapter();
                    updateEmptyState(R.string.label_no_saved_trips);
                    return;
                }
                
                final int[] pending = {tripIds.size()};
                final List<Trip> savedTrips = new ArrayList<>();
                
                for (String tripId : tripIds) {
                    FirebaseDatabase.getInstance().getReference("trips")
                            .child(tripId)
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot tripSnapshot) {
                                    if (!isAdded() || binding == null) return;
                                    
                                    Trip trip = tripSnapshot.getValue(Trip.class);
                                    if (trip != null) {
                                        savedTrips.add(trip);
                                    }
                                    pending[0]--;
                                    if (pending[0] <= 0) {
                                        trips.clear();
                                        trips.addAll(savedTrips);
                                        safeNotifyAdapter();
                                        updateEmptyState(R.string.label_no_saved_trips);
                                    }
                                }

                                @Override
                                public void onCancelled(@NonNull DatabaseError error) {
                                    if (!isAdded() || binding == null) return;
                                    
                                    pending[0]--;
                                    if (pending[0] <= 0) {
                                        trips.clear();
                                        trips.addAll(savedTrips);
                                        safeNotifyAdapter();
                                        updateEmptyState(R.string.label_no_saved_trips);
                                    }
                                }
                            });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                updateEmptyState(R.string.label_no_saved_trips);
            }
        };
        
        currentTripsRef.addValueEventListener(currentTripsListener);
    }

    private void updateEmptyState(int emptyTextResId) {
        if (binding == null) return;
        
        binding.tvEmptyTitle.setText(emptyTextResId);
        binding.tvEmpty.setVisibility(trips.isEmpty() ? View.VISIBLE : View.GONE);
        binding.rvTrips.setVisibility(trips.isEmpty() ? View.GONE : View.VISIBLE);
    }
    
    private void safeNotifyAdapter() {
        if (isAdded() && binding != null && adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        removeCurrentListener();
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

                // Status badge - UPCOMING or FULL based on availability
                long currentTime = System.currentTimeMillis();
                boolean isUpcoming = trip.departureTime > currentTime;
                int spotsLeft = trip.availableSeats;
                
                if (spotsLeft <= 0) {
                    // Trip is full
                    itemBinding.tvStatusBadge.setText("FULL");
                    itemBinding.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_full);
                } else if (isUpcoming) {
                    // Trip is upcoming and has spots
                    itemBinding.tvStatusBadge.setText("UPCOMING");
                    itemBinding.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_upcoming);
                } else {
                    // Ongoing or past trip
                    itemBinding.tvStatusBadge.setText("ONGOING");
                    itemBinding.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_upcoming);
                }
                itemBinding.tvStatusBadge.setVisibility(View.VISIBLE);

                // Load image
                if (trip.imageUrl != null && !trip.imageUrl.isEmpty()) {
                    Glide.with(itemBinding.ivAdventureImage)
                            .load(trip.imageUrl)
                            .centerCrop()
                            .placeholder(R.drawable.bg_adventure_image)
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
