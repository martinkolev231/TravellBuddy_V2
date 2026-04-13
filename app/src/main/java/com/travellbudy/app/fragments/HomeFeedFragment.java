package com.travellbudy.app.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.travellbudy.app.NotificationsActivity;
import com.travellbudy.app.R;
import com.travellbudy.app.databinding.FragmentHomeFeedBinding;
import com.travellbudy.app.models.Trip;
import com.travellbudy.app.models.User;
import com.travellbudy.app.ui.trip.FeaturedAdventureAdapter;
import com.travellbudy.app.ui.trip.YourAdventureAdapter;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Home Feed Fragment - displays personalized greeting, search,
 * user's adventures, and explore offers.
 */
public class HomeFeedFragment extends Fragment {

    private FragmentHomeFeedBinding binding;
    private FirebaseAuth auth;
    private DatabaseReference tripsRef;
    private DatabaseReference usersRef;
    private DatabaseReference notificationsRef;
    
    private FeaturedAdventureAdapter exploreAdapter;
    private YourAdventureAdapter yourAdventuresAdapter;
    
    private List<Trip> allTrips = new ArrayList<>();
    private String currentUserId;
    
    private ValueEventListener tripsListener;
    private ValueEventListener notificationsListener;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, 
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeFeedBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        auth = FirebaseAuth.getInstance();
        tripsRef = FirebaseDatabase.getInstance().getReference("trips");
        usersRef = FirebaseDatabase.getInstance().getReference("users");
        
        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            currentUserId = user.getUid();
            notificationsRef = FirebaseDatabase.getInstance().getReference("notifications").child(currentUserId);
        }
        
        setupGreeting();
        setupSearch();
        setupRecyclerViews();
        
        // Trips will be loaded in onResume
    }

    @Override
    public void onResume() {
        super.onResume();
        // Start listening for trip changes
        startTripsListener();
        // Start listening for notifications
        startNotificationsListener();
    }
    
    @Override
    public void onPause() {
        super.onPause();
        // Stop listening when fragment is not visible
        stopTripsListener();
        stopNotificationsListener();
    }
    
    private void stopTripsListener() {
        if (tripsListener != null) {
            tripsRef.removeEventListener(tripsListener);
            tripsListener = null;
        }
    }
    
    private void stopNotificationsListener() {
        if (notificationsListener != null && notificationsRef != null) {
            notificationsRef.removeEventListener(notificationsListener);
            notificationsListener = null;
        }
    }
    
    private void startNotificationsListener() {
        if (notificationsRef == null) return;
        
        stopNotificationsListener();
        
        notificationsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (binding == null) return;
                
                boolean hasUnread = false;
                for (DataSnapshot notifSnapshot : snapshot.getChildren()) {
                    Boolean isRead = notifSnapshot.child("isRead").getValue(Boolean.class);
                    if (isRead == null || !isRead) {
                        hasUnread = true;
                        break;
                    }
                }
                
                binding.notificationDot.setVisibility(hasUnread ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Hide dot on error
                if (binding != null) {
                    binding.notificationDot.setVisibility(View.GONE);
                }
            }
        };
        
        notificationsRef.addValueEventListener(notificationsListener);
    }
    
    private void startTripsListener() {
        // Remove existing listener first
        stopTripsListener();
        
        android.util.Log.d("HomeFeed", "Starting trips listener");
        
        tripsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                android.util.Log.d("HomeFeed", "Trips data changed, count: " + snapshot.getChildrenCount());
                
                List<String> driverUidsToCheck = new ArrayList<>();
                List<Trip> tripsToValidate = new ArrayList<>();
                
                // First, collect all trips and their driver UIDs
                for (DataSnapshot tripSnapshot : snapshot.getChildren()) {
                    Trip trip = tripSnapshot.getValue(Trip.class);
                    if (trip != null) {
                        trip.tripId = tripSnapshot.getKey();
                        android.util.Log.d("HomeFeed", "Found trip: " + trip.tripId);
                        
                        if (trip.driverUid != null && !trip.driverUid.isEmpty()) {
                            tripsToValidate.add(trip);
                            if (!driverUidsToCheck.contains(trip.driverUid)) {
                                driverUidsToCheck.add(trip.driverUid);
                            }
                        }
                        // Skip trips without driverUid
                    }
                }
                
                if (tripsToValidate.isEmpty()) {
                    allTrips.clear();
                    displayTrips();
                    return;
                }
                
                if (driverUidsToCheck.isEmpty()) {
                    allTrips.clear();
                    displayTrips();
                    return;
                }
                
                // Check which driver accounts exist
                final Map<String, Boolean> driverExists = new HashMap<>();
                final int[] checkedDrivers = {0};
                
                for (String driverUid : driverUidsToCheck) {
                    usersRef.child(driverUid).addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot userSnapshot) {
                            driverExists.put(driverUid, userSnapshot.exists());
                            checkedDrivers[0]++;
                            
                            if (checkedDrivers[0] >= driverUidsToCheck.size()) {
                                // All drivers checked, now filter trips
                                filterValidTrips(tripsToValidate, driverExists);
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            driverExists.put(driverUid, false);
                            checkedDrivers[0]++;
                            
                            if (checkedDrivers[0] >= driverUidsToCheck.size()) {
                                filterValidTrips(tripsToValidate, driverExists);
                            }
                        }
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                updateEmptyState(true);
            }
        };
        
        tripsRef.addValueEventListener(tripsListener);
    }
    
    private void filterValidTrips(List<Trip> tripsToValidate, Map<String, Boolean> driverExists) {
        allTrips.clear();
        long currentTime = System.currentTimeMillis();
        
        for (Trip trip : tripsToValidate) {
            // Only include trips where:
            // 1. Driver account exists
            // 2. Trip is in the future
            // 3. Trip is not canceled
            Boolean exists = driverExists.get(trip.driverUid);
            boolean isNotCanceled = !"canceled".equals(trip.status);
            if (exists != null && exists && trip.departureTime > currentTime && isNotCanceled) {
                allTrips.add(trip);
            }
        }
        
        displayTrips();
    }
    
    private void displayTrips() {
        if (binding == null) return;
        
        android.util.Log.d("HomeFeed", "displayTrips called, allTrips size: " + allTrips.size());
        
        List<Trip> userTrips = new ArrayList<>();
        List<Trip> otherTrips = new ArrayList<>();

        for (Trip trip : allTrips) {
            if (currentUserId != null && currentUserId.equals(trip.driverUid)) {
                android.util.Log.d("HomeFeed", "User trip: " + trip.tripId);
                userTrips.add(trip);
            } else {
                otherTrips.add(trip);
            }
        }

        android.util.Log.d("HomeFeed", "User trips count: " + userTrips.size() + ", Other trips: " + otherTrips.size());

        // Show/hide Your Adventures section
        if (!userTrips.isEmpty()) {
            binding.yourAdventuresSection.setVisibility(View.VISIBLE);
            yourAdventuresAdapter.submitList(userTrips);
        } else {
            binding.yourAdventuresSection.setVisibility(View.GONE);
            yourAdventuresAdapter.submitList(new ArrayList<>()); // Clear the adapter
        }

        // Show Explore Offers
        exploreAdapter.submitList(otherTrips);

        // Show/hide empty state
        updateEmptyState(otherTrips.isEmpty());
    }

    private void setupGreeting() {
        FirebaseUser user = auth.getCurrentUser();
        String greeting = getTimeBasedGreeting();
        
        if (user != null) {
            // Load user's name from database
            usersRef.child(user.getUid()).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    User userData = snapshot.getValue(User.class);
                    String name = "there";
                    if (userData != null && userData.displayName != null && !userData.displayName.isEmpty()) {
                        name = userData.displayName.split(" ")[0]; // First name only
                    } else if (user.getDisplayName() != null && !user.getDisplayName().isEmpty()) {
                        name = user.getDisplayName().split(" ")[0];
                    }
                    binding.tvGreeting.setText(greeting + ", " + name + " 👋");
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    binding.tvGreeting.setText(greeting + " 👋");
                }
            });
        } else {
            binding.tvGreeting.setText(greeting + " 👋");
        }
    }

    private String getTimeBasedGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour >= 5 && hour < 12) {
            return "Good morning";
        } else if (hour >= 12 && hour < 17) {
            return "Good afternoon";
        } else if (hour >= 17 && hour < 21) {
            return "Good evening";
        } else {
            return "Good night";
        }
    }

    private void setupSearch() {
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterTrips(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        binding.etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                filterTrips(binding.etSearch.getText().toString());
                return true;
            }
            return false;
        });


        binding.btnNotifications.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), NotificationsActivity.class);
            startActivity(intent);
        });
    }


    private void setupRecyclerViews() {
        // Your Adventures RecyclerView
        yourAdventuresAdapter = new YourAdventureAdapter();
        binding.rvYourAdventures.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvYourAdventures.setAdapter(yourAdventuresAdapter);
        yourAdventuresAdapter.setOnAdventureClickListener(trip -> navigateToTripDetails(trip.tripId));

        // Explore Offers RecyclerView
        exploreAdapter = new FeaturedAdventureAdapter();
        binding.rvTrips.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvTrips.setAdapter(exploreAdapter);
        exploreAdapter.setOnAdventureClickListener(new FeaturedAdventureAdapter.OnAdventureClickListener() {
            @Override
            public void onAdventureClick(Trip trip) {
                navigateToTripDetails(trip.tripId);
            }

            @Override
            public void onJoinClick(Trip trip) {
                navigateToTripDetails(trip.tripId);
            }
        });

        // Manage button
        binding.tvManage.setOnClickListener(v -> {
            // Navigate to My Trips
            Navigation.findNavController(requireView()).navigate(R.id.myTripsFragment);
        });
        
        // FAB to create new adventure
        binding.fabCreateTrip.setOnClickListener(v -> {
            Navigation.findNavController(requireView()).navigate(R.id.createTripFragment);
        });
    }

    private void navigateToTripDetails(String tripId) {
        Bundle args = new Bundle();
        args.putString("tripId", tripId);
        Navigation.findNavController(requireView()).navigate(R.id.action_home_to_tripDetail, args);
    }

    private void filterTrips(String searchQuery) {
        if (searchQuery == null || searchQuery.isEmpty()) {
            showAllTrips();
            return;
        }
        
        String query = searchQuery.toLowerCase();
        List<Trip> filtered = new ArrayList<>();
        
        for (Trip trip : allTrips) {
            // Skip user's own trips
            if (currentUserId != null && currentUserId.equals(trip.driverUid)) {
                continue;
            }
            
            // Search in title, origin, destination
            boolean matches = false;
            if (trip.carModel != null && trip.carModel.toLowerCase().contains(query)) matches = true;
            if (trip.originCity != null && trip.originCity.toLowerCase().contains(query)) matches = true;
            if (trip.destinationCity != null && trip.destinationCity.toLowerCase().contains(query)) matches = true;
            if (trip.activityType != null && trip.activityType.toLowerCase().contains(query)) matches = true;
            
            if (matches) {
                filtered.add(trip);
            }
        }
        
        exploreAdapter.submitList(filtered);
        updateEmptyState(filtered.isEmpty());
    }

    private void showAllTrips() {
        List<Trip> filtered = new ArrayList<>();
        
        for (Trip trip : allTrips) {
            // Skip user's own trips
            if (currentUserId != null && currentUserId.equals(trip.driverUid)) {
                continue;
            }
            filtered.add(trip);
        }
        
        exploreAdapter.submitList(filtered);
        updateEmptyState(filtered.isEmpty());
    }

    private void updateEmptyState(boolean isEmpty) {
        if (binding == null) return;
        
        if (isEmpty) {
            binding.emptyState.setVisibility(View.VISIBLE);
            binding.rvTrips.setVisibility(View.GONE);
        } else {
            binding.emptyState.setVisibility(View.GONE);
            binding.rvTrips.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopTripsListener();
        stopNotificationsListener();
        binding = null;
    }
}

