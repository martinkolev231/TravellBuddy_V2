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

import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * My Adventures screen with status-based tabs: Upcoming, Ongoing, Completed.
 * Shows both hosted and joined trips for the current user.
 */
public class MyTripsFragment extends Fragment {

    private static final String TAG = "MyTripsFragment";
    
    private FragmentMyTripsBinding binding;
    private MyAdventureAdapter adapter;
    private final List<TripWithRole> trips = new ArrayList<>();
    private String currentUserId;
    
    // Tab states: 0 = Upcoming, 1 = Ongoing, 2 = Completed
    private int currentTab = 0;
    
    // Firebase references
    private DatabaseReference tripsRef;
    private DatabaseReference tripRequestsRef;
    private DatabaseReference tripMembersRef;

    /**
     * Wrapper class to hold a trip with its role (hosted or joined).
     */
    private static class TripWithRole {
        Trip trip;
        boolean isHosted;
        
        TripWithRole(Trip trip, boolean isHosted) {
            this.trip = trip;
            this.isHosted = isHosted;
        }
    }

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

        tripsRef = FirebaseDatabase.getInstance().getReference("trips");
        tripRequestsRef = FirebaseDatabase.getInstance().getReference("tripRequests");
        tripMembersRef = FirebaseDatabase.getInstance().getReference("tripMembers");

        // Setup back button navigation
        view.findViewById(R.id.btnBack).setOnClickListener(v -> 
            Navigation.findNavController(v).navigateUp()
        );

        adapter = new MyAdventureAdapter();
        binding.rvTrips.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvTrips.setAdapter(adapter);

        // Setup tab clicks
        setupTabListeners();
        
        // Select Upcoming tab by default
        selectTab(0);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Reload trips when returning to this fragment
        loadTrips();
    }

    private void setupTabListeners() {
        binding.tabUpcoming.setOnClickListener(v -> {
            selectTab(0);
            loadTrips();
        });

        binding.tabOngoing.setOnClickListener(v -> {
            selectTab(1);
            loadTrips();
        });

        binding.tabCompleted.setOnClickListener(v -> {
            selectTab(2);
            loadTrips();
        });
    }

    private void selectTab(int tabIndex) {
        currentTab = tabIndex;

        // Reset all tabs to unselected state
        binding.tabUpcoming.setBackgroundResource(R.drawable.bg_tab_unselected);
        binding.tabUpcoming.setTextColor(getResources().getColor(R.color.text_secondary, null));
        binding.tabUpcoming.setTypeface(null, android.graphics.Typeface.NORMAL);

        binding.tabOngoing.setBackgroundResource(R.drawable.bg_tab_unselected);
        binding.tabOngoing.setTextColor(getResources().getColor(R.color.text_secondary, null));
        binding.tabOngoing.setTypeface(null, android.graphics.Typeface.NORMAL);

        binding.tabCompleted.setBackgroundResource(R.drawable.bg_tab_unselected);
        binding.tabCompleted.setTextColor(getResources().getColor(R.color.text_secondary, null));
        binding.tabCompleted.setTypeface(null, android.graphics.Typeface.NORMAL);

        // Highlight selected tab
        TextView selectedTab;
        switch (tabIndex) {
            case 1:
                selectedTab = binding.tabOngoing;
                break;
            case 2:
                selectedTab = binding.tabCompleted;
                break;
            default:
                selectedTab = binding.tabUpcoming;
                break;
        }
        selectedTab.setBackgroundResource(R.drawable.bg_tab_selected);
        selectedTab.setTextColor(getResources().getColor(R.color.text_primary, null));
        selectedTab.setTypeface(null, android.graphics.Typeface.BOLD);
    }

    private void loadTrips() {
        if (currentUserId == null) {
            Log.e(TAG, "currentUserId is null, cannot load trips");
            return;
        }
        
        Log.d(TAG, "loadTrips() called for tab: " + currentTab + " (0=Upcoming, 1=Ongoing, 2=Completed)");
        Log.d(TAG, "currentUserId: " + currentUserId);
        
        trips.clear();
        safeNotifyAdapter();

        // Load all trips and filter by status
        tripsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded() || binding == null) return;
                
                Log.d(TAG, "Total trips in database: " + snapshot.getChildrenCount());
                
                List<Trip> hostedTrips = new ArrayList<>();
                List<Trip> potentialJoinedTrips = new ArrayList<>();
                
                for (DataSnapshot child : snapshot.getChildren()) {
                    Trip trip = child.getValue(Trip.class);
                    if (trip == null || trip.tripId == null) {
                        Log.w(TAG, "Skipping null trip or trip with null ID");
                        continue;
                    }
                    
                    // Skip canceled trips
                    if (trip.isCanceled()) {
                        Log.d(TAG, "Skipping canceled trip: " + trip.tripId);
                        continue;
                    }
                    
                    // Log trip status info
                    String effectiveStatus = trip.getEffectiveStatus();
                    String wouldAppearIn = trip.isUpcoming() ? "UPCOMING" : (trip.isOngoing() ? "ONGOING" : "COMPLETED");
                    Log.d(TAG, "Trip: " + trip.tripId + " (" + (trip.carModel != null ? trip.carModel : trip.destinationCity) + ")");
                    Log.d(TAG, "  - driverUid: " + trip.driverUid + " (current user: " + currentUserId + ")");
                    Log.d(TAG, "  - effectiveStatus: " + effectiveStatus + " -> Would appear in: " + wouldAppearIn);
                    Log.d(TAG, "  - departureTime: " + trip.departureTime + " (" + new java.util.Date(trip.departureTime) + ")");
                    Log.d(TAG, "  - estimatedArrivalTime: " + trip.estimatedArrivalTime + " (" + new java.util.Date(trip.estimatedArrivalTime) + ")");
                    Log.d(TAG, "  - Current time: " + System.currentTimeMillis() + " (" + new java.util.Date() + ")");
                    Log.d(TAG, "  - isUpcoming: " + trip.isUpcoming() + ", isOngoing: " + trip.isOngoing() + ", isCompleted: " + trip.isCompleted());
                    
                    // Check if trip matches current tab's status
                    if (!matchesCurrentTabStatus(trip)) {
                        Log.d(TAG, "  - SKIPPED: doesn't match current tab status");
                        continue;
                    }
                    
                    // Check if user is the host
                    if (currentUserId.equals(trip.driverUid)) {
                        Log.d(TAG, "  - ADDED as hosted trip");
                        hostedTrips.add(trip);
                    } else {
                        // Potential joined trip - need to verify request
                        Log.d(TAG, "  - Added to potential joined trips");
                        potentialJoinedTrips.add(trip);
                    }
                }
                
                Log.d(TAG, "Found " + hostedTrips.size() + " hosted trips and " + potentialJoinedTrips.size() + " potential joined trips for this tab");
                
                // Debug: Also count how many trips would be potential joined if we ignore status
                int totalPotentialJoined = 0;
                for (DataSnapshot child : snapshot.getChildren()) {
                    Trip trip = child.getValue(Trip.class);
                    if (trip != null && trip.tripId != null && !trip.isCanceled() && !currentUserId.equals(trip.driverUid)) {
                        totalPotentialJoined++;
                    }
                }
                Log.d(TAG, "Total potential joined trips (ignoring status): " + totalPotentialJoined);
                
                // If no potential joined trips, just show hosted trips
                if (potentialJoinedTrips.isEmpty()) {
                    displayTrips(hostedTrips, new ArrayList<>());
                    return;
                }
                
                // Verify join requests for potential joined trips
                checkDriversAndRequests(hostedTrips, potentialJoinedTrips, null);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load trips: " + error.getMessage());
                updateEmptyState();
            }
        });
    }
    
    /**
     * Check if a trip matches the currently selected tab status.
     * Uses date-only logic from Trip model methods.
     */
    private boolean matchesCurrentTabStatus(Trip trip) {
        switch (currentTab) {
            case 0: // Upcoming
                return trip.isUpcoming();
            case 1: // Ongoing
                return trip.isOngoing();
            case 2: // Completed
                return trip.isCompleted();
            default:
                return false;
        }
    }
    
    private void checkDriversAndRequests(List<Trip> hostedTrips, 
                                          List<Trip> potentialJoinedTrips,
                                          List<String> unused) {
        if (!isAdded() || binding == null) return;
        
        // Skip driver existence check - if user has approved request, show the trip
        // This allows users to see trips they joined even if host deleted their account
        verifyJoinRequests(hostedTrips, potentialJoinedTrips);
    }
    
    private void verifyJoinRequests(List<Trip> hostedTrips,
                                     List<Trip> potentialJoinedTrips) {
        if (!isAdded() || binding == null) return;
        
        Log.d(TAG, "verifyJoinRequests: checking " + potentialJoinedTrips.size() + " potential joined trips");
        
        if (potentialJoinedTrips.isEmpty()) {
            displayTrips(hostedTrips, new ArrayList<>());
            return;
        }
        
        final int[] pending = {potentialJoinedTrips.size()};
        final List<Trip> joinedTrips = java.util.Collections.synchronizedList(new ArrayList<>());
        
        for (Trip trip : potentialJoinedTrips) {
            Log.d(TAG, "Checking membership for trip: " + trip.tripId + " (" + trip.carModel + ")");
            
            // Read ALL requests for this trip and filter locally
            // This avoids index query issues
            tripRequestsRef.child(trip.tripId)
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            if (!isAdded() || binding == null) return;
                            
                            Log.d(TAG, "  Trip " + trip.tripId + ": total requests = " + snapshot.getChildrenCount());
                            
                            boolean foundApproved = false;
                            for (DataSnapshot reqSnap : snapshot.getChildren()) {
                                SeatRequest req = reqSnap.getValue(SeatRequest.class);
                                if (req != null) {
                                    Log.d(TAG, "    Request " + reqSnap.getKey() + ": riderUid=" + req.riderUid + ", status=" + req.status);
                                    // Check if this request belongs to current user AND is approved
                                    if (currentUserId.equals(req.riderUid) && "approved".equals(req.status)) {
                                        Log.d(TAG, "    ✓ Found APPROVED request for current user!");
                                        foundApproved = true;
                                        break;
                                    }
                                }
                            }
                            
                            if (foundApproved) {
                                joinedTrips.add(trip);
                            } else {
                                // Also check tripMembers as backup
                                checkTripMembership(trip, joinedTrips, pending, hostedTrips);
                                return; // Don't decrement pending here, it's done in checkTripMembership
                            }
                            
                            checkPendingComplete(pending, hostedTrips, joinedTrips);
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            Log.e(TAG, "Error reading requests for trip " + trip.tripId + ": " + error.getMessage());
                            // Try tripMembers as fallback
                            checkTripMembership(trip, joinedTrips, pending, hostedTrips);
                        }
                    });
        }
    }
    
    private void checkTripMembership(Trip trip, List<Trip> joinedTrips, int[] pending, List<Trip> hostedTrips) {
        tripMembersRef.child(trip.tripId).child(currentUserId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot memberSnapshot) {
                        if (!isAdded() || binding == null) return;
                        
                        if (memberSnapshot.exists()) {
                            Log.d(TAG, "  ✓ Found user in tripMembers for trip: " + trip.tripId);
                            joinedTrips.add(trip);
                        } else {
                            Log.d(TAG, "  ✗ User NOT found in tripMembers for trip: " + trip.tripId);
                        }
                        
                        checkPendingComplete(pending, hostedTrips, joinedTrips);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Error checking tripMembers for trip " + trip.tripId + ": " + error.getMessage());
                        checkPendingComplete(pending, hostedTrips, joinedTrips);
                    }
                });
    }
    
    private void checkPendingComplete(int[] pending, List<Trip> hostedTrips, List<Trip> joinedTrips) {
        synchronized (pending) {
            pending[0]--;
            Log.d(TAG, "  Pending trips to check: " + pending[0]);
            if (pending[0] <= 0) {
                Log.d(TAG, "All trips checked. Found " + joinedTrips.size() + " joined trips");
                displayTrips(hostedTrips, joinedTrips);
            }
        }
    }
    
    private void displayTrips(List<Trip> hostedTrips, List<Trip> joinedTrips) {
        if (!isAdded() || binding == null) return;
        
        Log.d(TAG, "displayTrips: " + hostedTrips.size() + " hosted, " + joinedTrips.size() + " joined");
        
        trips.clear();
        
        // Add hosted trips with role
        for (Trip trip : hostedTrips) {
            trips.add(new TripWithRole(trip, true));
        }
        
        // Add joined trips with role
        for (Trip trip : joinedTrips) {
            trips.add(new TripWithRole(trip, false));
        }
        
        // Sort by departure time (most recent first for completed, soonest first for others)
        trips.sort((a, b) -> {
            if (currentTab == 2) {
                // Completed: most recently ended first
                return Long.compare(b.trip.estimatedArrivalTime, a.trip.estimatedArrivalTime);
            } else {
                // Upcoming/Ongoing: soonest first
                return Long.compare(a.trip.departureTime, b.trip.departureTime);
            }
        });
        
        safeNotifyAdapter();
        updateEmptyState();
    }

    private void updateEmptyState() {
        if (binding == null) return;
        
        int emptyTextResId;
        switch (currentTab) {
            case 1:
                emptyTextResId = R.string.label_no_ongoing_trips;
                break;
            case 2:
                emptyTextResId = R.string.label_no_completed_trips;
                break;
            default:
                emptyTextResId = R.string.label_no_upcoming_trips;
                break;
        }
        
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
        binding = null;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Adapter
    // ═══════════════════════════════════════════════════════════════════════════

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
                
                // Force white background color on the card using ColorStateList
                android.content.res.ColorStateList whiteColor = android.content.res.ColorStateList.valueOf(0xFFFFFFFF);
                itemBinding.getRoot().setCardBackgroundColor(whiteColor);
                itemBinding.getRoot().setBackgroundTintList(null);
            }

            void bind(TripWithRole tripWithRole) {
                Trip trip = tripWithRole.trip;
                boolean isHosted = tripWithRole.isHosted;
                
                // Title
                String title = trip.carModel != null && !trip.carModel.isEmpty()
                        ? trip.carModel
                        : trip.originCity + " → " + trip.destinationCity;
                itemBinding.tvTitle.setText(title);

                // Price badge
                if (trip.pricePerSeat > 0) {
                    itemBinding.tvPrice.setText(String.format(java.util.Locale.getDefault(), "€ %.0f", trip.pricePerSeat));
                } else {
                    itemBinding.tvPrice.setText(R.string.label_free);
                }

                // Date - format as "Apr 28 - 30" (same month) or "Apr 28 - May 2"
                if (trip.departureTime > 0 && trip.estimatedArrivalTime > 0) {
                    java.time.format.DateTimeFormatter monthDayFormatter = 
                            java.time.format.DateTimeFormatter.ofPattern("MMM d", java.util.Locale.ENGLISH);
                    java.time.format.DateTimeFormatter dayOnlyFormatter = 
                            java.time.format.DateTimeFormatter.ofPattern("d", java.util.Locale.ENGLISH);
                    java.time.LocalDateTime startDate = java.time.LocalDateTime.ofInstant(
                            java.time.Instant.ofEpochMilli(trip.departureTime), java.time.ZoneId.systemDefault());
                    java.time.LocalDateTime endDate = java.time.LocalDateTime.ofInstant(
                            java.time.Instant.ofEpochMilli(trip.estimatedArrivalTime), java.time.ZoneId.systemDefault());
                    
                    String dateRange;
                    if (startDate.getMonth() == endDate.getMonth() && startDate.getYear() == endDate.getYear()) {
                        dateRange = startDate.format(monthDayFormatter) + " - " + endDate.format(dayOnlyFormatter);
                    } else {
                        dateRange = startDate.format(monthDayFormatter) + " - " + endDate.format(monthDayFormatter);
                    }
                    itemBinding.tvDate.setText(dateRange);
                } else {
                    itemBinding.tvDate.setText("Dates TBD");
                }

                // Joined count
                int joined = trip.totalSeats - trip.availableSeats;
                itemBinding.tvJoined.setText(String.format(java.util.Locale.getDefault(), "%d/%d joined", joined, trip.totalSeats));

                // Determine trip status
                String effectiveStatus = trip.getEffectiveStatus();
                boolean isUpcoming = Trip.STATUS_UPCOMING.equals(effectiveStatus);
                boolean isUpcomingHosted = isHosted && isUpcoming;
                
                // ═══════════════════════════════════════════════════════════════════
                // Badge logic: All trips show status badge (left) + role badge (right)
                // ═══════════════════════════════════════════════════════════════════
                
                // Hide the old single "HOSTED BY YOU" badge - we now use two separate badges
                itemBinding.tvHostedByYouBadge.setVisibility(View.GONE);
                
                // Status badge styling based on status
                switch (effectiveStatus) {
                    case Trip.STATUS_UPCOMING:
                        itemBinding.tvStatusBadge.setText(R.string.status_upcoming);
                        // Light/cream background with dark text for upcoming
                        itemBinding.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_upcoming_light);
                        itemBinding.tvStatusBadge.setTextColor(0xFF1E293B); // Dark navy text
                        itemBinding.tvStatusBadge.setVisibility(View.VISIBLE);
                        break;
                    case Trip.STATUS_ONGOING:
                        itemBinding.tvStatusBadge.setText(R.string.status_ongoing);
                        itemBinding.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_ongoing_teal);
                        itemBinding.tvStatusBadge.setTextColor(0xFFFFFFFF); // White text
                        itemBinding.tvStatusBadge.setVisibility(View.VISIBLE);
                        break;
                    case Trip.STATUS_COMPLETED:
                        itemBinding.tvStatusBadge.setText(R.string.status_completed);
                        itemBinding.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_dark_pill);
                        itemBinding.tvStatusBadge.setTextColor(0xFFFFFFFF); // White text
                        itemBinding.tvStatusBadge.setVisibility(View.VISIBLE);
                        break;
                    default:
                        itemBinding.tvStatusBadge.setVisibility(View.GONE);
                        break;
                }
                
                // Role badge (Hosted / Joined) - always show for all trips with dark pill style
                itemBinding.tvRoleBadge.setBackgroundResource(R.drawable.bg_badge_dark_pill);
                if (isHosted) {
                    itemBinding.tvRoleBadge.setText(R.string.role_hosted);
                } else {
                    itemBinding.tvRoleBadge.setText(R.string.role_joined);
                }
                itemBinding.tvRoleBadge.setVisibility(View.VISIBLE);
                
                // Determine if this is a completed or ongoing trip
                boolean isCompleted = Trip.STATUS_COMPLETED.equals(effectiveStatus);
                boolean isOngoing = Trip.STATUS_ONGOING.equals(effectiveStatus);
                boolean isCompletedJoined = !isHosted && isCompleted;
                
                // Divider - show for completed, ongoing, and upcoming trips
                itemBinding.divider.setVisibility((isCompleted || isOngoing || isUpcoming) ? View.VISIBLE : View.GONE);
                
                // View Summary button - show only for completed trips
                itemBinding.btnViewSummary.setVisibility(isCompleted ? View.VISIBLE : View.GONE);
                if (isCompleted) {
                    itemBinding.btnViewSummary.setOnClickListener(v -> {
                        // Open trip details for now (could be a summary screen later)
                        Intent intent = new Intent(requireContext(), TripDetailsActivity.class);
                        intent.putExtra(TripDetailsActivity.EXTRA_TRIP_ID, trip.tripId);
                        startActivity(intent);
                    });
                }
                
                // Open Trip button - show only for ongoing trips
                itemBinding.btnOpenTrip.setVisibility(isOngoing ? View.VISIBLE : View.GONE);
                if (isOngoing) {
                    itemBinding.btnOpenTrip.setOnClickListener(v -> {
                        // Open the live trip / trip details screen
                        Intent intent = new Intent(requireContext(), TripDetailsActivity.class);
                        intent.putExtra(TripDetailsActivity.EXTRA_TRIP_ID, trip.tripId);
                        startActivity(intent);
                    });
                }
                
                // View Details button - show only for upcoming trips
                itemBinding.btnViewDetails.setVisibility(isUpcoming ? View.VISIBLE : View.GONE);
                if (isUpcoming) {
                    itemBinding.btnViewDetails.setOnClickListener(v -> {
                        // Open the trip details screen
                        Intent intent = new Intent(requireContext(), TripDetailsActivity.class);
                        intent.putExtra(TripDetailsActivity.EXTRA_TRIP_ID, trip.tripId);
                        startActivity(intent);
                    });
                }
                
                // Rating section - show only for completed joined trips
                if (isCompletedJoined) {
                    // For joined trips, the driver is the host we need to rate
                    fetchHostNameAndCheckRating(trip.tripId, trip.driverUid, itemBinding);
                } else {
                    // Hide rating section for hosted trips or non-completed trips
                    itemBinding.layoutPendingRating.setVisibility(View.GONE);
                    itemBinding.layoutSubmittedRating.setVisibility(View.GONE);
                }

                // Load image
                if (trip.imageUrl != null && !trip.imageUrl.isEmpty()) {
                    Glide.with(itemBinding.ivAdventureImage)
                            .load(trip.imageUrl)
                            .centerCrop()
                            .placeholder(R.drawable.bg_adventure_image)
                            .into(itemBinding.ivAdventureImage);
                } else {
                    setImageByActivityType(trip.activityType);
                }

                // Click to open details
                itemBinding.getRoot().setOnClickListener(v -> {
                    Intent intent = new Intent(requireContext(), TripDetailsActivity.class);
                    intent.putExtra(TripDetailsActivity.EXTRA_TRIP_ID, trip.tripId);
                    startActivity(intent);
                });
            }
            
            private void setImageByActivityType(String activityType) {
                if (activityType == null) {
                    itemBinding.ivAdventureImage.setBackgroundResource(R.drawable.bg_adventure_image);
                    return;
                }
                
                switch (activityType) {
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
            }
            
            /**
             * Fetch the host's display name and then check if the user has rated them.
             */
            private void fetchHostNameAndCheckRating(String tripId, String hostUid, 
                                                      ItemMyAdventureCardBinding binding) {
                if (hostUid == null) {
                    binding.layoutPendingRating.setVisibility(View.GONE);
                    binding.layoutSubmittedRating.setVisibility(View.GONE);
                    return;
                }
                
                // Fetch host's display name from users node
                FirebaseDatabase.getInstance().getReference("users")
                        .child(hostUid)
                        .child("displayName")
                        .addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot snapshot) {
                                if (!isAdded()) return;
                                
                                String hostName = snapshot.getValue(String.class);
                                if (hostName == null || hostName.isEmpty()) {
                                    hostName = "the host";
                                }
                                
                                // Now check if user has rated this host
                                checkUserRating(tripId, hostUid, hostName, binding);
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {
                                Log.e(TAG, "Error fetching host name: " + error.getMessage());
                                // Still try to show rating section with default name
                                checkUserRating(tripId, hostUid, "the host", binding);
                            }
                        });
            }
            
            /**
             * Check if the current user has rated the host for this trip.
             * Shows appropriate rating state (pending or submitted).
             */
            private void checkUserRating(String tripId, String hostUid, String hostName, 
                                          ItemMyAdventureCardBinding binding) {
                if (currentUserId == null || tripId == null || hostUid == null) {
                    Log.w(TAG, "checkUserRating: currentUserId, tripId, or hostUid is null");
                    binding.layoutPendingRating.setVisibility(View.GONE);
                    binding.layoutSubmittedRating.setVisibility(View.GONE);
                    return;
                }
                
                // Use deterministic rating ID: reviewerUid_revieweeUid
                String ratingId = currentUserId + "_" + hostUid;
                Log.d(TAG, "checkUserRating: checking for rating with ID=" + ratingId);
                
                // Direct lookup by deterministic key - much faster and more reliable
                FirebaseDatabase.getInstance().getReference("ratings")
                        .child(tripId)
                        .child(ratingId)
                        .addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot snapshot) {
                                if (!isAdded()) return;
                                
                                Log.d(TAG, "checkUserRating: rating exists = " + snapshot.exists());
                                
                                if (snapshot.exists()) {
                                    // State 2: Show submitted rating
                                    Long score = snapshot.child("score").getValue(Long.class);
                                    int ratingScore = score != null ? score.intValue() : 5;
                                    Log.d(TAG, "checkUserRating: found existing rating with score=" + ratingScore);
                                    
                                    binding.layoutPendingRating.setVisibility(View.GONE);
                                    binding.layoutSubmittedRating.setVisibility(View.VISIBLE);
                                    displayStars(binding, ratingScore);
                                } else {
                                    // State 1: Show pending rating prompt
                                    Log.d(TAG, "checkUserRating: no rating found, showing Rate Host button");
                                    binding.layoutPendingRating.setVisibility(View.VISIBLE);
                                    binding.layoutSubmittedRating.setVisibility(View.GONE);
                                    
                                    // Set dynamic host name
                                    String displayName = hostName != null && !hostName.isEmpty() 
                                            ? hostName : "the host";
                                    binding.tvRatingPrompt.setText(
                                            requireContext().getString(R.string.label_how_was_host, displayName));
                                    
                                    // Rate Host button click
                                    binding.btnRateHost.setOnClickListener(v -> {
                                        showRateHostDialog(tripId, hostUid, displayName);
                                    });
                                }
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {
                                Log.e(TAG, "Error checking rating: " + error.getMessage());
                                binding.layoutPendingRating.setVisibility(View.GONE);
                                binding.layoutSubmittedRating.setVisibility(View.GONE);
                            }
                        });
            }
            
            /**
             * Display star rating (1-5 stars).
             */
            private void displayStars(ItemMyAdventureCardBinding binding, int rating) {
                int filledColor = requireContext().getColor(R.color.star_filled);
                int emptyColor = requireContext().getColor(R.color.text_hint);
                
                binding.star1.setColorFilter(rating >= 1 ? filledColor : emptyColor);
                binding.star2.setColorFilter(rating >= 2 ? filledColor : emptyColor);
                binding.star3.setColorFilter(rating >= 3 ? filledColor : emptyColor);
                binding.star4.setColorFilter(rating >= 4 ? filledColor : emptyColor);
                binding.star5.setColorFilter(rating >= 5 ? filledColor : emptyColor);
            }
            
            /**
             * Show the rate host bottom sheet.
             * First checks if user has already rated this host for this trip.
             */
            private void showRateHostDialog(String tripId, String hostUid, String hostName) {
                if (currentUserId == null || hostUid == null) {
                    Log.w(TAG, "showRateHostDialog: currentUserId or hostUid is null");
                    return;
                }
                
                // Use deterministic rating ID to check for existing rating
                String ratingId = currentUserId + "_" + hostUid;
                Log.d(TAG, "showRateHostDialog: checking for existing rating with ID=" + ratingId);
                
                FirebaseDatabase.getInstance().getReference("ratings")
                        .child(tripId)
                        .child(ratingId)
                        .addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot snapshot) {
                                if (!isAdded()) return;
                                
                                if (snapshot.exists()) {
                                    // User already rated this host - update UI to State 2
                                    Log.d(TAG, "User already rated this host, updating UI to State 2");
                                    Long score = snapshot.child("score").getValue(Long.class);
                                    int ratingScore = score != null ? score.intValue() : 5;
                                    
                                    itemBinding.layoutPendingRating.setVisibility(View.GONE);
                                    itemBinding.layoutSubmittedRating.setVisibility(View.VISIBLE);
                                    displayStars(itemBinding, ratingScore);
                                    return;
                                }
                                
                                // User hasn't rated yet, proceed to show the dialog
                                fetchHostDataAndShowDialog(tripId, hostUid, hostName);
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {
                                Log.e(TAG, "Error checking existing rating: " + error.getMessage());
                                // Proceed anyway - the submit will catch duplicates
                                fetchHostDataAndShowDialog(tripId, hostUid, hostName);
                            }
                        });
            }
            
            /**
             * Fetch host avatar and trip title, then show the bottom sheet.
             */
            private void fetchHostDataAndShowDialog(String tripId, String hostUid, String hostName) {
                // Fetch the host's avatar URL
                FirebaseDatabase.getInstance().getReference("users")
                        .child(hostUid)
                        .addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot snapshot) {
                                if (!isAdded()) return;
                                
                                String avatarUrl = snapshot.child("photoUrl").getValue(String.class);
                                
                                // Fetch trip title
                                tripsRef.child(tripId).addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override
                                    public void onDataChange(@NonNull DataSnapshot tripSnapshot) {
                                        if (!isAdded()) return;
                                        
                                        Trip trip = tripSnapshot.getValue(Trip.class);
                                        String title = "";
                                        if (trip != null) {
                                            title = trip.carModel != null && !trip.carModel.isEmpty() 
                                                    ? trip.carModel 
                                                    : trip.originCity + " → " + trip.destinationCity;
                                        }
                                        
                                        // Show the bottom sheet with all data
                                        com.travellbudy.app.dialogs.RateHostBottomSheet bottomSheet = 
                                                com.travellbudy.app.dialogs.RateHostBottomSheet.newInstance(
                                                        hostUid, hostName, avatarUrl, tripId, title);
                                        final String finalTripId = tripId;
                                        final String finalHostUid = hostUid;
                                        bottomSheet.setOnRatingSubmittedListener(() -> {
                                            // Small delay to ensure Firebase has synced the new rating
                                            itemBinding.getRoot().postDelayed(() -> {
                                                // Directly update this card's UI to State 2
                                                // without waiting for full loadTrips() refresh
                                                itemBinding.layoutPendingRating.setVisibility(View.GONE);
                                                itemBinding.layoutSubmittedRating.setVisibility(View.VISIBLE);
                                                // Fetch and display the rating score
                                                FirebaseDatabase.getInstance().getReference("ratings")
                                                        .child(finalTripId)
                                                        .orderByChild("reviewerUid")
                                                        .equalTo(currentUserId)
                                                        .addListenerForSingleValueEvent(new ValueEventListener() {
                                                            @Override
                                                            public void onDataChange(@NonNull DataSnapshot snapshot) {
                                                                for (DataSnapshot ratingSnap : snapshot.getChildren()) {
                                                                    String revieweeUid = ratingSnap.child("revieweeUid").getValue(String.class);
                                                                    if (finalHostUid.equals(revieweeUid)) {
                                                                        Long score = ratingSnap.child("score").getValue(Long.class);
                                                                        int ratingScore = score != null ? score.intValue() : 5;
                                                                        displayStars(itemBinding, ratingScore);
                                                                        break;
                                                                    }
                                                                }
                                                            }

                                                            @Override
                                                            public void onCancelled(@NonNull DatabaseError error) {
                                                                // Default to 5 stars if fetch fails
                                                                displayStars(itemBinding, 5);
                                                            }
                                                        });
                                            }, 300);
                                        });
                                        bottomSheet.show(getParentFragmentManager(), "RateHostBottomSheet");
                                    }

                                    @Override
                                    public void onCancelled(@NonNull DatabaseError error) {
                                        if (!isAdded()) return;
                                        // Show bottom sheet even without trip title
                                        showBottomSheetWithData(hostUid, hostName, avatarUrl, tripId, "");
                                    }
                                });
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {
                                if (!isAdded()) return;
                                // Show bottom sheet even without avatar
                                showBottomSheetWithData(hostUid, hostName, null, tripId, "");
                            }
                        });
            }
            
            private void showBottomSheetWithData(String hostUid, String hostName, 
                                                  String avatarUrl, String tripId, String tripTitle) {
                com.travellbudy.app.dialogs.RateHostBottomSheet bottomSheet = 
                        com.travellbudy.app.dialogs.RateHostBottomSheet.newInstance(
                                hostUid, hostName, avatarUrl, tripId, tripTitle);
                final String finalTripId = tripId;
                final String finalHostUid = hostUid;
                bottomSheet.setOnRatingSubmittedListener(() -> {
                    // Small delay then update UI
                    itemBinding.getRoot().postDelayed(() -> {
                        itemBinding.layoutPendingRating.setVisibility(View.GONE);
                        itemBinding.layoutSubmittedRating.setVisibility(View.VISIBLE);
                        // Fetch and display the rating score
                        FirebaseDatabase.getInstance().getReference("ratings")
                                .child(finalTripId)
                                .orderByChild("reviewerUid")
                                .equalTo(currentUserId)
                                .addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override
                                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                                        for (DataSnapshot ratingSnap : snapshot.getChildren()) {
                                            String revieweeUid = ratingSnap.child("revieweeUid").getValue(String.class);
                                            if (finalHostUid.equals(revieweeUid)) {
                                                Long score = ratingSnap.child("score").getValue(Long.class);
                                                int ratingScore = score != null ? score.intValue() : 5;
                                                displayStars(itemBinding, ratingScore);
                                                break;
                                            }
                                        }
                                    }

                                    @Override
                                    public void onCancelled(@NonNull DatabaseError error) {
                                        displayStars(itemBinding, 5);
                                    }
                                });
                    }, 300);
                });
                bottomSheet.show(getParentFragmentManager(), "RateHostBottomSheet");
            }
        }
    }
}
