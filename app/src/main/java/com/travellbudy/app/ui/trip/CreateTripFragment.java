package com.travellbudy.app.ui.trip;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.travellbudy.app.R;
import com.travellbudy.app.databinding.FragmentCreateTripBinding;
import com.travellbudy.app.models.Trip;
import com.travellbudy.app.viewmodel.CreateTripViewModel;

import java.util.ArrayList;
import java.util.List;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;

public class CreateTripFragment extends Fragment {

    private FragmentCreateTripBinding binding;
    private CreateTripViewModel viewModel;

    private LocalDate startDate;
    private LocalDate endDate;
    private final DateTimeFormatter dateFormatter =
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault());

    private String selectedActivityType = "road_trip";
    private int selectedTypePosition = 0;
    private Uri selectedCoverPhotoUri = null;

    // Activity types list for dropdown
    private static final String[] ACTIVITY_LABELS = {
            "Road Trip", "Hiking", "Beach", "City Break", "Camping", "Festival", "Other..."
    };
    private static final String[] ACTIVITY_KEYS = {
            "road_trip", "hiking", "beach", "city_break", "camping", "festival", "other"
    };

    // Map: display label → DB key (kept for backward compatibility)
    private static final LinkedHashMap<String, String> ACTIVITY_TYPE_MAP = new LinkedHashMap<>();
    static {
        ACTIVITY_TYPE_MAP.put("Road Trip", "road_trip");
        ACTIVITY_TYPE_MAP.put("Hiking", "hiking");
        ACTIVITY_TYPE_MAP.put("Beach", "beach");
        ACTIVITY_TYPE_MAP.put("City Break", "city_break");
        ACTIVITY_TYPE_MAP.put("Camping", "camping");
        ACTIVITY_TYPE_MAP.put("Festival", "festival");
        ACTIVITY_TYPE_MAP.put("Other...", "other");
    }

    private final ActivityResultLauncher<String> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    selectedCoverPhotoUri = uri;
                    showCoverPhoto(uri);
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentCreateTripBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(CreateTripViewModel.class);

        binding.toolbar.setNavigationOnClickListener(v ->
                Navigation.findNavController(v).popBackStack());

        setupActivityTypeDropdown();
        setupClickListeners();
    }

    private void setupActivityTypeDropdown() {
        // Set up the spinner with an ArrayAdapter
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                ACTIVITY_LABELS
        );
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerActivityType.setAdapter(spinnerAdapter);
        binding.spinnerActivityType.setSelection(selectedTypePosition);
        
        // Listen for selection changes
        binding.spinnerActivityType.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                selectedTypePosition = position;
                selectedActivityType = ACTIVITY_KEYS[position];
                
                // Show/hide the "Other" input field
                if ("Other...".equals(ACTIVITY_LABELS[position])) {
                    binding.otherTypeContainer.setVisibility(View.VISIBLE);
                    binding.etOtherType.requestFocus();
                } else {
                    binding.otherTypeContainer.setVisibility(View.GONE);
                    binding.etOtherType.setText("");
                }
            }
            
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    }
    
    private void showTypeDropdownDialog() {
        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_type_dropdown);
        
        RecyclerView rvTypeList = dialog.findViewById(R.id.rvTypeList);
        rvTypeList.setLayoutManager(new LinearLayoutManager(requireContext()));
        
        List<String> typesList = new ArrayList<>();
        for (String label : ACTIVITY_LABELS) {
            typesList.add(label);
        }
        
        TypeDropdownAdapter adapter = new TypeDropdownAdapter(typesList, selectedTypePosition, (type, position) -> {
            selectedTypePosition = position;
            selectedActivityType = ACTIVITY_KEYS[position];
            binding.spinnerActivityType.setSelection(position);
            
            // Show/hide the "Other" input field
            if ("Other...".equals(type)) {
                binding.otherTypeContainer.setVisibility(View.VISIBLE);
                binding.etOtherType.requestFocus();
            } else {
                binding.otherTypeContainer.setVisibility(View.GONE);
                binding.etOtherType.setText("");
            }
            
            dialog.dismiss();
        });
        
        rvTypeList.setAdapter(adapter);
        
        // Configure dialog appearance
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.85);
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            params.gravity = Gravity.CENTER;
            window.setAttributes(params);
        }
        
        dialog.show();
    }


    private void setupClickListeners() {
        // Cover photo - add new
        binding.btnAddCoverPhoto.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));

        // Cover photo - change existing (can't remove, only change)
        binding.btnRemoveCover.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));

        // Start Date picker
        binding.btnStartDate.setOnClickListener(v -> showStartDatePicker());
        
        // End Date picker
        binding.btnEndDate.setOnClickListener(v -> showEndDatePicker());

        // Publish button
        binding.btnPublish.setOnClickListener(v -> publishTrip());
    }

    private void showCoverPhoto(Uri uri) {
        binding.btnAddCoverPhoto.setVisibility(View.GONE);
        binding.coverPhotoContainer.setVisibility(View.VISIBLE);
        Glide.with(this)
                .load(uri)
                .centerCrop()
                .into(binding.ivCoverPhoto);
    }

    private void showStartDatePicker() {
        LocalDate now = LocalDate.now();
        DatePickerDialog dpd = new DatePickerDialog(requireContext(),
                (pickerView, year, month, dayOfMonth) -> {
                    startDate = LocalDate.of(year, month + 1, dayOfMonth);
                    binding.tvStartDate.setText(startDate.format(dateFormatter));
                    binding.tvStartDate.setTextColor(requireContext().getResources().getColor(R.color.text_primary, null));
                    
                    // If end date is before start date, reset it
                    if (endDate != null && endDate.isBefore(startDate)) {
                        endDate = null;
                        binding.tvEndDate.setText("");
                    }
                },
                now.getYear(), now.getMonthValue() - 1, now.getDayOfMonth());
        dpd.getDatePicker().setMinDate(System.currentTimeMillis());
        dpd.show();
    }
    
    private void showEndDatePicker() {
        LocalDate minDate = startDate != null ? startDate : LocalDate.now();
        DatePickerDialog dpd = new DatePickerDialog(requireContext(),
                (pickerView, year, month, dayOfMonth) -> {
                    endDate = LocalDate.of(year, month + 1, dayOfMonth);
                    binding.tvEndDate.setText(endDate.format(dateFormatter));
                    binding.tvEndDate.setTextColor(requireContext().getResources().getColor(R.color.text_primary, null));
                },
                minDate.getYear(), minDate.getMonthValue() - 1, minDate.getDayOfMonth());
        dpd.getDatePicker().setMinDate(minDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli());
        dpd.show();
    }

    private void publishTrip() {
        String title = binding.etTitle.getText().toString().trim();
        String destination = binding.etDestination.getText().toString().trim();
        String seatsStr = binding.etSeats.getText().toString().trim();
        String budgetStr = binding.etBudget.getText().toString().trim();
        String description = binding.etVehicle.getText().toString().trim();

        // Validate cover photo is required
        if (selectedCoverPhotoUri == null) {
            Toast.makeText(requireContext(), "Please add a cover photo to create the trip", Toast.LENGTH_LONG).show();
            return;
        }
        // Validate title
        if (TextUtils.isEmpty(title)) {
            Toast.makeText(requireContext(), "Please enter a title", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(destination)) {
            Toast.makeText(requireContext(), R.string.error_empty_destination, Toast.LENGTH_SHORT).show();
            return;
        }
        if (startDate == null) {
            Toast.makeText(requireContext(), R.string.error_empty_start_date, Toast.LENGTH_SHORT).show();
            return;
        }
        if (endDate == null) {
            Toast.makeText(requireContext(), R.string.error_empty_end_date, Toast.LENGTH_SHORT).show();
            return;
        }

        // Validate date is in the future
        LocalDateTime departureDateTime = LocalDateTime.of(startDate, LocalTime.of(9, 0));
        if (departureDateTime.isBefore(LocalDateTime.now())) {
            Toast.makeText(requireContext(), R.string.error_past_date, Toast.LENGTH_SHORT).show();
            return;
        }

        // Validate group size is required
        if (TextUtils.isEmpty(seatsStr)) {
            Toast.makeText(requireContext(), "Please enter the group size", Toast.LENGTH_SHORT).show();
            return;
        }
        int seats;
        try {
            seats = Integer.parseInt(seatsStr);
            if (seats < 2 || seats > 20) {
                Toast.makeText(requireContext(), R.string.error_invalid_seats, Toast.LENGTH_SHORT).show();
                return;
            }
        } catch (NumberFormatException e) {
            Toast.makeText(requireContext(), R.string.error_invalid_seats, Toast.LENGTH_SHORT).show();
            return;
        }

        // Validate budget is required
        if (TextUtils.isEmpty(budgetStr)) {
            Toast.makeText(requireContext(), "Please enter a budget", Toast.LENGTH_SHORT).show();
            return;
        }
        double budget;
        try {
            budget = Double.parseDouble(budgetStr);
            if (budget < 0) {
                Toast.makeText(requireContext(), R.string.error_invalid_budget, Toast.LENGTH_SHORT).show();
                return;
            }
        } catch (NumberFormatException e) {
            Toast.makeText(requireContext(), R.string.error_invalid_budget, Toast.LENGTH_SHORT).show();
            return;
        }

        if (TextUtils.isEmpty(description)) {
            Toast.makeText(requireContext(), "Please add a description", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(requireContext(), R.string.error_not_logged_in, Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading(true);
        
        // Proceed directly without reloading user (reload can cause auth issues)
        proceedWithPublish(currentUser, title, destination, seats, budget, description, departureDateTime, endDate);
    }

    private void proceedWithPublish(FirebaseUser currentUser, String title, String destination,
                                    int seats, double budget, String description, LocalDateTime departureDateTime, LocalDate tripEndDate) {
        long departureMillis = departureDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        // Use the actual end date selected by user instead of hardcoded 8 hours offset
        LocalDateTime endDateTime = LocalDateTime.of(tripEndDate, LocalTime.of(18, 0));
        long arrivalEstimate = endDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        String driverName = currentUser.getDisplayName() != null ? currentUser.getDisplayName() : "Organizer";

        // Generate trip ID first for the photo upload path
        String tripId = com.google.firebase.database.FirebaseDatabase.getInstance()
                .getReference("trips").push().getKey();
        if (tripId == null) {
            showLoading(false);
            Toast.makeText(requireContext(), R.string.error_trip_creation_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        Trip trip = new Trip(tripId, currentUser.getUid(), driverName,
                destination, destination,
                destination, destination,
                departureMillis, arrivalEstimate, seats, budget, "EUR", title);

        // Set the description separately (carModel is used for title)
        trip.description = description;

        // Set coordinates to 0 since we're not using Places API
        trip.originLat = 0.0;
        trip.originLng = 0.0;
        trip.destLat = 0.0;
        trip.destLng = 0.0;

        if (currentUser.getPhotoUrl() != null) {
            trip.driverPhotoUrl = currentUser.getPhotoUrl().toString();
        }

        // Use custom "Other" type if entered, otherwise use selected type
        if ("other".equals(selectedActivityType)) {
            String customType = binding.etOtherType.getText().toString().trim();
            trip.activityType = !TextUtils.isEmpty(customType) ? customType : "other";
        } else {
            trip.activityType = selectedActivityType != null ? selectedActivityType : "other";
        }
        
        // Set budget level based on amount
        String budgetLevel = budget <= 100 ? "low" : (budget <= 500 ? "medium" : "high");
        trip.luggageSize = budgetLevel;
        trip.difficultyLevel = budgetLevel.equals("low") ? "easy" : (budgetLevel.equals("high") ? "hard" : "moderate");

        // Upload cover photo first, then save trip
        uploadCoverPhotoAndSaveTrip(tripId, trip);
    }

    private void uploadCoverPhotoAndSaveTrip(String tripId, Trip trip) {
        StorageReference storageRef = FirebaseStorage.getInstance().getReference();
        String fileName = "trip_photos/" + tripId + "/cover.jpg";
        StorageReference photoRef = storageRef.child(fileName);

        photoRef.putFile(selectedCoverPhotoUri)
                .addOnSuccessListener(taskSnapshot -> {
                    photoRef.getDownloadUrl().addOnSuccessListener(downloadUri -> {
                        // Check if fragment is still attached
                        if (!isAdded() || getContext() == null) return;
                        
                        trip.imageUrl = downloadUri.toString();
                        saveTripToDatabase(trip);
                    }).addOnFailureListener(e -> {
                        // Check if fragment is still attached
                        if (!isAdded() || getContext() == null) return;
                        
                        showLoading(false);
                        Toast.makeText(requireContext(), R.string.error_trip_creation_failed, Toast.LENGTH_SHORT).show();
                    });
                })
                .addOnFailureListener(e -> {
                    // Check if fragment is still attached
                    if (!isAdded() || getContext() == null) return;
                    
                    showLoading(false);
                    Toast.makeText(requireContext(), R.string.error_trip_creation_failed, Toast.LENGTH_SHORT).show();
                });
    }

    private void saveTripToDatabase(Trip trip) {
        viewModel.publishTrip(trip).observe(getViewLifecycleOwner(), result -> {
            // Check if fragment is still attached and view is available
            if (!isAdded() || getView() == null || binding == null) return;
            
            showLoading(false);
            if (result == null || result.isLoading()) return;

            if (result.isSuccess()) {
                Toast.makeText(requireContext(), R.string.success_trip_created, Toast.LENGTH_SHORT).show();
                // Simply pop back to previous fragment instead of navigating
                try {
                    Navigation.findNavController(requireView()).popBackStack();
                } catch (Exception e) {
                    // Fragment was detached, ignore
                }
            } else if (result.isError()) {
                String errMsg = result.message != null ? result.message : getString(R.string.error_trip_creation_failed);
                Toast.makeText(requireContext(), errMsg, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showLoading(boolean show) {
        if (binding == null) return;
        binding.progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        binding.btnPublish.setClickable(!show);
        binding.btnPublish.setAlpha(show ? 0.6f : 1.0f);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
