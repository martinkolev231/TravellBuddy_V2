package com.travellbudy.app.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.travellbudy.app.HomeActivity;
import com.travellbudy.app.NotificationsActivity;
import com.travellbudy.app.R;
import com.travellbudy.app.TripDetailsActivity;
import com.travellbudy.app.databinding.FragmentHomeFeedBinding;
import com.travellbudy.app.models.Trip;
import com.travellbudy.app.ui.trip.FeaturedAdventureAdapter;
import com.travellbudy.app.ui.trip.YourAdventureAdapter;
import com.travellbudy.app.viewmodel.HomeViewModel;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Home Feed screen with "Your Adventures" and "Explore Offers" sections.
 * Features category filtering and search functionality.
 */
public class HomeFeedFragment extends Fragment {

    private FragmentHomeFeedBinding binding;
    private HomeViewModel viewModel;
    private FeaturedAdventureAdapter exploreAdapter;
    private YourAdventureAdapter yourAdventuresAdapter;
    private final List<Trip> allTrips = new ArrayList<>();
    private final List<Trip> yourTrips = new ArrayList<>();
    private final List<Trip> exploreTrips = new ArrayList<>();
    
    // Notification listener
    private DatabaseReference notificationsRef;
    private ValueEventListener notificationListener;

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

        viewModel = new ViewModelProvider(this).get(HomeViewModel.class);

        setupWindowInsets();
        setupGreeting();
        setupRecyclerViews();
        setupClickListeners();
        setupNotificationBadge();
        setupSearch();
        observeTrips();
    }

    /**
     * Apply proper window insets for safe area handling (status bar, notch, etc.)
     * This ensures the header respects the system UI on all devices including iPhones.
     */
    private void setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.headerContainer, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars());
            // Apply status bar height as top padding to push content below the status bar
            v.setPadding(
                v.getPaddingLeft(),
                insets.top + 16, // 16dp additional padding below status bar
                v.getPaddingRight(),
                v.getPaddingBottom()
            );
            return WindowInsetsCompat.CONSUMED;
        });
    }

    private void setupGreeting() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        String userName = "Traveler";
        if (currentUser != null && currentUser.getDisplayName() != null && !currentUser.getDisplayName().isEmpty()) {
            userName = currentUser.getDisplayName().split(" ")[0];
        }
        
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String greeting;
        if (hour < 12) {
            greeting = "Good morning, " + userName + " 👋";
        } else if (hour < 18) {
            greeting = "Good afternoon, " + userName + " 👋";
        } else {
            greeting = "Good evening, " + userName + " 👋";
        }
        binding.tvGreeting.setText(greeting);
    }


    private void setupRecyclerViews() {
        // Your Adventures adapter
        yourAdventuresAdapter = new YourAdventureAdapter();
        yourAdventuresAdapter.setOnAdventureClickListener(trip -> {
            openTripDetails(trip);
        });
        binding.rvYourAdventures.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvYourAdventures.setAdapter(yourAdventuresAdapter);
        
        // Explore Offers adapter
        exploreAdapter = new FeaturedAdventureAdapter();
        exploreAdapter.setOnAdventureClickListener(new FeaturedAdventureAdapter.OnAdventureClickListener() {
            @Override
            public void onAdventureClick(Trip trip) {
                openTripDetails(trip);
            }

            @Override
            public void onJoinClick(Trip trip) {
                openTripDetails(trip);
            }
        });
        binding.rvTrips.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvTrips.setAdapter(exploreAdapter);
    }

    private void setupClickListeners() {
        // Notifications button
        binding.btnNotifications.setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), NotificationsActivity.class));
        });
        
        // Filter button
        binding.btnFilter.setOnClickListener(v -> {
            // TODO: Open filter bottom sheet
        });
        
        // Manage button - navigate to My Trips
        binding.tvManage.setOnClickListener(v -> {
            if (getActivity() instanceof HomeActivity) {
                ((HomeActivity) getActivity()).navigateToMyTrips();
            }
        });
    }

    private void setupSearch() {

        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterAndDisplayTrips();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void observeTrips() {
        viewModel.getTrips().observe(getViewLifecycleOwner(), result -> {
            switch (result.status) {
                case LOADING:
                    binding.swipeRefresh.setRefreshing(true);
                    break;
                case SUCCESS:
                    binding.swipeRefresh.setRefreshing(false);
                    allTrips.clear();
                    if (result.data != null) allTrips.addAll(result.data);
                    filterAndDisplayTrips();
                    break;
                case ERROR:
                    binding.swipeRefresh.setRefreshing(false);
                    binding.emptyState.setVisibility(View.VISIBLE);
                    binding.rvTrips.setVisibility(View.GONE);
                    binding.yourAdventuresSection.setVisibility(View.GONE);
                    break;
            }
        });
    }

    private void filterAndDisplayTrips() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        String currentUserId = currentUser != null ? currentUser.getUid() : "";
        String searchQuery = binding.etSearch.getText().toString().trim().toLowerCase();
        
        yourTrips.clear();
        exploreTrips.clear();
        
        for (Trip trip : allTrips) {

            // Apply search filter
            if (!searchQuery.isEmpty()) {
                String title = trip.carModel != null ? trip.carModel.toLowerCase() : "";
                String destination = trip.destinationCity != null ? trip.destinationCity.toLowerCase() : "";
                String origin = trip.originCity != null ? trip.originCity.toLowerCase() : "";
                
                if (!title.contains(searchQuery) && 
                    !destination.contains(searchQuery) && 
                    !origin.contains(searchQuery)) {
                    continue;
                }
            }
            
            // Separate user's own trips from others
            if (trip.driverUid != null && trip.driverUid.equals(currentUserId)) {
                yourTrips.add(trip);
            } else {
                exploreTrips.add(trip);
            }
        }
        
        // Update Your Adventures section
        if (yourTrips.isEmpty()) {
            binding.yourAdventuresSection.setVisibility(View.GONE);
        } else {
            binding.yourAdventuresSection.setVisibility(View.VISIBLE);
            yourAdventuresAdapter.submitList(new ArrayList<>(yourTrips));
        }
        
        // Update Explore Offers section
        exploreAdapter.submitList(new ArrayList<>(exploreTrips));
        
        boolean noTrips = yourTrips.isEmpty() && exploreTrips.isEmpty();
        binding.emptyState.setVisibility(noTrips ? View.VISIBLE : View.GONE);
        binding.rvTrips.setVisibility(exploreTrips.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void openTripDetails(Trip trip) {
        Intent intent = new Intent(requireContext(), TripDetailsActivity.class);
        intent.putExtra(TripDetailsActivity.EXTRA_TRIP_ID, trip.tripId);
        startActivity(intent);
    }

    private void setupNotificationBadge() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            binding.notificationDot.setVisibility(View.GONE);
            return;
        }

        notificationsRef = FirebaseDatabase.getInstance()
                .getReference("notifications")
                .child(currentUser.getUid());

        notificationListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (binding == null) return;
                
                boolean hasUnread = false;
                for (DataSnapshot child : snapshot.getChildren()) {
                    Boolean isRead = child.child("isRead").getValue(Boolean.class);
                    if (isRead == null || !isRead) {
                        hasUnread = true;
                        break;
                    }
                }
                
                binding.notificationDot.setVisibility(hasUnread ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (binding != null) {
                    binding.notificationDot.setVisibility(View.GONE);
                }
            }
        };

        notificationsRef.addValueEventListener(notificationListener);
    }

    @Override
    public void onResume() {
        super.onResume();
        setupGreeting();
        if (viewModel != null) {
            viewModel.refresh();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (notificationListener != null && notificationsRef != null) {
            notificationsRef.removeEventListener(notificationListener);
        }
        binding = null;
    }
}

