package com.travellbudy.app;

import android.app.DatePickerDialog;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.travellbudy.app.databinding.ActivityCreateTripBinding;
import com.travellbudy.app.models.Trip;
import com.travellbudy.app.models.TripMember;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class CreateTripActivity extends BaseActivity {

    private ActivityCreateTripBinding binding;
    private LocalDate selectedStartDate;
    private LocalDate selectedEndDate;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", new Locale("bg"));
    private Uri selectedCoverPhotoUri = null;

    private String[] activityTypes;

    private final ActivityResultLauncher<String> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    selectedCoverPhotoUri = uri;
                    showCoverPhoto(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCreateTripBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Initialize activity types from string resources
        activityTypes = new String[] {
            getString(R.string.activity_dropdown_hiking),
            getString(R.string.activity_dropdown_camping),
            getString(R.string.activity_dropdown_road_trip),
            getString(R.string.activity_dropdown_city_break),
            getString(R.string.activity_dropdown_festival),
            getString(R.string.activity_label_photography),
            getString(R.string.activity_label_outdoor_sports),
            getString(R.string.activity_label_backpacking),
            getString(R.string.activity_label_weekend),
            getString(R.string.activity_dropdown_other)
        };

        binding.btnBack.setOnClickListener(v -> finish());

        setupTypeSpinner();
        setupClickListeners();
    }

    private void setupTypeSpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, activityTypes);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerType.setAdapter(adapter);
    }

    private void setupClickListeners() {
        // Cover photo - add new
        binding.btnAddCoverPhoto.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));

        // Cover photo - change existing
        binding.btnChangeCover.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));

        // Start date picker
        binding.btnStartDate.setOnClickListener(v -> showStartDatePicker());

        // End date picker
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
        DatePickerDialog dpd = new DatePickerDialog(this,
                (view, year, month, dayOfMonth) -> {
                    selectedStartDate = LocalDate.of(year, month + 1, dayOfMonth);
                    binding.tvStartDate.setText(selectedStartDate.format(dateFormatter));
                    binding.tvStartDate.setTextColor(getResources().getColor(R.color.text_primary, null));

                    // If end date is before start date, clear it
                    if (selectedEndDate != null && selectedEndDate.isBefore(selectedStartDate)) {
                        selectedEndDate = null;
                        binding.tvEndDate.setText("");
                        binding.tvEndDate.setTextColor(getResources().getColor(R.color.text_hint, null));
                    }
                },
                now.getYear(), now.getMonthValue() - 1, now.getDayOfMonth());
        dpd.getDatePicker().setMinDate(System.currentTimeMillis());
        dpd.show();
    }

    private void showEndDatePicker() {
        LocalDate minDate = selectedStartDate != null ? selectedStartDate : LocalDate.now();
        DatePickerDialog dpd = new DatePickerDialog(this,
                (view, year, month, dayOfMonth) -> {
                    selectedEndDate = LocalDate.of(year, month + 1, dayOfMonth);
                    binding.tvEndDate.setText(selectedEndDate.format(dateFormatter));
                    binding.tvEndDate.setTextColor(getResources().getColor(R.color.text_primary, null));
                },
                minDate.getYear(), minDate.getMonthValue() - 1, minDate.getDayOfMonth());
        dpd.getDatePicker().setMinDate(minDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli());
        dpd.show();
    }

    private void publishTrip() {
        String title = binding.etTitle.getText().toString().trim();
        String destination = binding.etDestination.getText().toString().trim();
        String groupSizeStr = binding.etGroupSize.getText().toString().trim();
        String budgetStr = binding.etBudget.getText().toString().trim();
        String description = binding.etDescription.getText().toString().trim();

        // Validate cover photo is required
        if (selectedCoverPhotoUri == null) {
            Toast.makeText(this, R.string.error_empty_cover_photo, Toast.LENGTH_LONG).show();
            return;
        }
        if (TextUtils.isEmpty(title)) {
            Toast.makeText(this, R.string.error_empty_title, Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(destination)) {
            Toast.makeText(this, R.string.error_empty_destination, Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedStartDate == null) {
            Toast.makeText(this, R.string.error_empty_start_date, Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedEndDate == null) {
            Toast.makeText(this, R.string.error_empty_end_date, Toast.LENGTH_SHORT).show();
            return;
        }

        // Validate start date is in the future
        LocalDateTime departureDateTime = LocalDateTime.of(selectedStartDate, LocalTime.of(9, 0));
        if (departureDateTime.isBefore(LocalDateTime.now())) {
            Toast.makeText(this, R.string.error_past_date, Toast.LENGTH_SHORT).show();
            return;
        }

        int seats = 5; // default
        if (!TextUtils.isEmpty(groupSizeStr)) {
            try {
                seats = Integer.parseInt(groupSizeStr);
                if (seats < 2 || seats > 20) {
                    Toast.makeText(this, R.string.error_invalid_seats, Toast.LENGTH_SHORT).show();
                    return;
                }
            } catch (NumberFormatException e) {
                Toast.makeText(this, R.string.error_invalid_seats, Toast.LENGTH_SHORT).show();
                return;
            }
        }

        double budget = 0;
        if (!TextUtils.isEmpty(budgetStr)) {
            try {
                budget = Double.parseDouble(budgetStr);
                if (budget < 0) {
                    Toast.makeText(this, R.string.error_invalid_budget, Toast.LENGTH_SHORT).show();
                    return;
                }
            } catch (NumberFormatException e) {
                Toast.makeText(this, R.string.error_invalid_budget, Toast.LENGTH_SHORT).show();
                return;
            }
        }

        if (TextUtils.isEmpty(description)) {
            Toast.makeText(this, R.string.error_add_description, Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;


        showLoading(true);

        long departureMillis = departureDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        LocalDateTime arrivalDateTime = LocalDateTime.of(selectedEndDate, LocalTime.of(18, 0));
        long arrivalMillis = arrivalDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        DatabaseReference tripsRef = FirebaseDatabase.getInstance().getReference("trips");
        String tripId = tripsRef.push().getKey();
        if (tripId == null) {
            showLoading(false);
            Toast.makeText(this, R.string.error_trip_creation_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        String driverName = currentUser.getDisplayName() != null ? currentUser.getDisplayName() : "Organizer";

        // Get selected activity type
        String selectedType = binding.spinnerType.getSelectedItem() != null 
                ? binding.spinnerType.getSelectedItem().toString() : "";

        Trip trip = new Trip(tripId, currentUser.getUid(), driverName,
                destination, destination,
                destination, destination,
                departureMillis, arrivalMillis, seats, budget, "EUR", title);

        // Set the description separately
        trip.description = description;

        // Set additional fields
        trip.originLat = 0.0;
        trip.originLng = 0.0;
        trip.destLat = 0.0;
        trip.destLng = 0.0;
        trip.activityType = selectedType;

        // Set driver photo if available
        if (currentUser.getPhotoUrl() != null) {
            trip.driverPhotoUrl = currentUser.getPhotoUrl().toString();
        }

        // Upload cover photo to Firebase Storage first, then save trip
        // selectedCoverPhotoUri is guaranteed to be non-null at this point (validated above)
        uploadCoverPhotoAndSaveTrip(tripId, trip, currentUser);
    }

    private void uploadCoverPhotoAndSaveTrip(String tripId, Trip trip, FirebaseUser currentUser) {
        StorageReference storageRef = FirebaseStorage.getInstance().getReference();
        String fileName = "trip_photos/" + tripId + "/cover.jpg";
        StorageReference photoRef = storageRef.child(fileName);

        photoRef.putFile(selectedCoverPhotoUri)
                .addOnSuccessListener(taskSnapshot -> {
                    photoRef.getDownloadUrl().addOnSuccessListener(downloadUri -> {
                        trip.imageUrl = downloadUri.toString();
                        saveTripToDatabase(tripId, trip, currentUser);
                    }).addOnFailureListener(e -> {
                        showLoading(false);
                        Toast.makeText(this, R.string.error_trip_creation_failed, Toast.LENGTH_SHORT).show();
                    });
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(this, R.string.error_trip_creation_failed, Toast.LENGTH_SHORT).show();
                });
    }

    private void saveTripToDatabase(String tripId, Trip trip, FirebaseUser currentUser) {
        DatabaseReference tripsRef = FirebaseDatabase.getInstance().getReference("trips");
        tripsRef.child(tripId).setValue(trip.toMap())
                .addOnSuccessListener(aVoid -> {
                    TripMember driverMember = new TripMember(
                            currentUser.getUid(), "driver", 0);
                    FirebaseDatabase.getInstance().getReference("tripMembers")
                            .child(tripId).child(currentUser.getUid())
                            .setValue(driverMember.toMap());

                    // Create group chat for this trip
                    createGroupChat(tripId, trip, currentUser);

                    showLoading(false);
                    Toast.makeText(this, R.string.success_trip_created, Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(this, R.string.error_trip_creation_failed, Toast.LENGTH_SHORT).show();
                });
    }

    private void createGroupChat(String tripId, Trip trip, FirebaseUser currentUser) {
        DatabaseReference chatsRef = FirebaseDatabase.getInstance().getReference("chats");
        String chatId = chatsRef.push().getKey();
        if (chatId == null) return;

        // Use the trip title (stored in carModel field) for the group chat name
        String tripTitle;
        if (trip.carModel != null && !trip.carModel.isEmpty()) {
            tripTitle = trip.carModel;
        } else {
            tripTitle = trip.destinationCity;
        }

        Map<String, Object> chatData = new HashMap<>();
        chatData.put("chatId", chatId);
        chatData.put("type", "group");
        chatData.put("tripId", tripId);
        chatData.put("name", tripTitle);
        chatData.put("lastMessage", "");
        chatData.put("lastMessageTime", System.currentTimeMillis());
        chatData.put("lastMessageSenderId", "");

        // Add creator as participant
        Map<String, Object> participants = new HashMap<>();
        participants.put(currentUser.getUid(), true);
        chatData.put("participants", participants);

        chatsRef.child(chatId).setValue(chatData);
    }

    private void showLoading(boolean show) {
        binding.progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        binding.btnPublish.setEnabled(!show);
    }

    private void hideKeyboard() {
        View view = getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    /**
     * Dismiss keyboard when user taps outside of EditText fields
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            View v = getCurrentFocus();
            if (v instanceof EditText) {
                int[] location = new int[2];
                v.getLocationOnScreen(location);
                float x = ev.getRawX();
                float y = ev.getRawY();

                if (x < location[0] || x > location[0] + v.getWidth() ||
                    y < location[1] || y > location[1] + v.getHeight()) {
                    hideKeyboard();
                    v.clearFocus();
                }
            }
        }
        return super.dispatchTouchEvent(ev);
    }
}
