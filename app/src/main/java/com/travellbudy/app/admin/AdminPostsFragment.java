package com.travellbudy.app.admin;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.travellbudy.app.R;
import com.travellbudy.app.admin.adapter.AdminTripsAdapter;
import com.travellbudy.app.databinding.FragmentAdminPostsBinding;
import com.travellbudy.app.models.Trip;
import com.travellbudy.app.viewmodel.AdminViewModel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Admin Posts Fragment - Manage all adventure posts/trips with premium UI design.
 */
public class AdminPostsFragment extends Fragment implements AdminTripsAdapter.OnTripActionListener {

    private FragmentAdminPostsBinding binding;
    private AdminViewModel viewModel;
    private AdminTripsAdapter adapter;
    private List<Trip> allTrips = new ArrayList<>();
    private Set<String> featuredTripIds = new HashSet<>();
    private String currentFilter = "all";

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentAdminPostsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(AdminViewModel.class);

        setupRecyclerView();
        setupSearch();
        setupFilterButton();
        setupObservers();
    }

    private void setupRecyclerView() {
        adapter = new AdminTripsAdapter(this);
        binding.recyclerTrips.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerTrips.setAdapter(adapter);
    }

    private void setupSearch() {
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilters();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }


    private void setupFilterButton() {
        binding.btnFilter.setOnClickListener(v -> showFilterDialog());
    }

    private void showFilterDialog() {
        String[] filterOptions = {
            getString(R.string.admin_tab_all),
            getString(R.string.admin_tab_pending),
            getString(R.string.admin_tab_reported),
            getString(R.string.admin_tab_active),
            getString(R.string.admin_chip_full)
        };

        String[] filterValues = {"all", "pending", "reported", "active", "full"};

        int currentIndex = 0;
        for (int i = 0; i < filterValues.length; i++) {
            if (filterValues[i].equals(currentFilter)) {
                currentIndex = i;
                break;
            }
        }

        new AlertDialog.Builder(requireContext())
            .setTitle(R.string.admin_filter_all)
            .setSingleChoiceItems(filterOptions, currentIndex, (dialog, which) -> {
                currentFilter = filterValues[which];
                applyFilters();
                dialog.dismiss();
            })
            .setNegativeButton(R.string.action_cancel, null)
            .show();
    }

    private void applyFilters() {
        List<Trip> filtered = new ArrayList<>();
        String searchText = binding.etSearch.getText().toString().toLowerCase();

        for (Trip trip : allTrips) {
            boolean matchesFilter = false;

            switch (currentFilter) {
                case "all":
                    matchesFilter = true;
                    break;
                case "pending":
                    matchesFilter = "pending".equals(trip.status) || "open".equals(trip.status);
                    break;
                case "reported":
                    // Assuming trips have a reported flag or status
                    matchesFilter = "reported".equals(trip.status);
                    break;
                case "active":
                    matchesFilter = "open".equals(trip.status) || "in_progress".equals(trip.status);
                    break;
                case "full":
                    matchesFilter = "full".equals(trip.status) || trip.availableSeats <= 0;
                    break;
            }

            if (matchesFilter) {
                // Also apply search filter
                if (searchText.isEmpty()) {
                    filtered.add(trip);
                } else {
                    boolean matchesSearch = 
                        (trip.carModel != null && trip.carModel.toLowerCase().contains(searchText)) ||
                        (trip.destinationCity != null && trip.destinationCity.toLowerCase().contains(searchText)) ||
                        (trip.driverName != null && trip.driverName.toLowerCase().contains(searchText));
                    if (matchesSearch) {
                        filtered.add(trip);
                    }
                }
            }
        }

        adapter.submitList(filtered, featuredTripIds);
        binding.emptyView.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        binding.recyclerTrips.setVisibility(filtered.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void setupObservers() {
        viewModel.getAllTrips().observe(getViewLifecycleOwner(), trips -> {
            allTrips = trips != null ? trips : new ArrayList<>();
            applyFilters();
        });

        viewModel.getFeaturedTripIds().observe(getViewLifecycleOwner(), ids -> {
            featuredTripIds = ids != null ? new HashSet<>(ids) : new HashSet<>();
            applyFilters();
        });

        viewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> 
            binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE));
    }

    @Override
    public void onTripClick(Trip trip) {
        showTripDetailsDialog(trip);
    }

    @Override
    public void onTripMenuClick(Trip trip, View anchor) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        popup.inflate(R.menu.menu_admin_trip_actions);

        popup.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.action_delete) {
                confirmDeleteTrip(trip);
                return true;
            }
            return false;
        });

        popup.show();
    }

    private void showTripDetailsDialog(Trip trip) {
        StringBuilder details = new StringBuilder();
        details.append("Title: ").append(trip.carModel != null ? trip.carModel : "N/A").append("\n");
        details.append("Destination: ").append(trip.destinationCity != null ? trip.destinationCity : "N/A").append("\n");
        details.append("Host: ").append(trip.driverName != null ? trip.driverName : "N/A").append("\n");
        details.append("Status: ").append(trip.status != null ? trip.status : "N/A").append("\n");
        details.append("Seats: ").append(trip.availableSeats).append("/").append(trip.totalSeats).append("\n");
        details.append("Price: ").append(trip.pricePerSeat > 0 ? trip.pricePerSeat + " лв." : "Free").append("\n");

        if (featuredTripIds.contains(trip.tripId)) {
            details.append("\n⭐ Featured Trip");
        }

        new AlertDialog.Builder(requireContext())
            .setTitle(R.string.admin_trip_details)
            .setMessage(details.toString())
            .setPositiveButton(R.string.action_close, null)
            .show();
    }

    private void confirmDeleteTrip(Trip trip) {
        new AlertDialog.Builder(requireContext())
            .setTitle(R.string.admin_delete_trip_title)
            .setMessage(getString(R.string.admin_delete_trip_message, trip.carModel))
            .setPositiveButton(R.string.action_delete, (d, w) -> viewModel.deleteTrip(trip.tripId))
            .setNegativeButton(R.string.action_cancel, null)
            .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
