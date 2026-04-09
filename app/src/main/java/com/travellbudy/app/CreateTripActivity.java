package com.travellbudy.app;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
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
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.widget.Autocomplete;
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CreateTripActivity extends AppCompatActivity {

    private ActivityCreateTripBinding binding;
    private LocalDate selectedStartDate;
    private LocalDate selectedEndDate;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault());

    private String selectedDestination = "";
    private Double destLat;
    private Double destLng;
    private String selectedType = "";
    private Uri selectedCoverPhotoUri = null;

    private final String[] activityTypes = {
            "Hiking", "Camping", "Road Trip", "City Explore",
            "Festival / Event", "Photography", "Outdoor Sports",
            "Backpacking", "Weekend Getaway", "Other"
    };

    private final ActivityResultLauncher<Intent> placesLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                    return;
                }
                Place place = Autocomplete.getPlaceFromIntent(result.getData());
                String address = place.getAddress();
                if (TextUtils.isEmpty(address)) {
                    address = place.getName();
                }
                if (TextUtils.isEmpty(address) || place.getLatLng() == null) {
                    return;
                }

                selectedDestination = address;
                destLat = place.getLatLng().latitude;
                destLng = place.getLatLng().longitude;
                binding.tvDestination.setText(address);
                binding.tvDestination.setTextColor(getResources().getColor(R.color.text_primary, null));
            });

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

        setupWindowInsets();
        binding.btnBack.setOnClickListener(v -> finish());

        // Setup touch listener to dismiss keyboard when tapping outside EditText
        setupTouchToDismissKeyboard();

        // Force software layer type on button to eliminate shadow artifacts
        binding.btnPublish.setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        setupTypeSpinner();
        setupClickListeners();
    }

    @SuppressWarnings("ClickableViewAccessibility")
    private void setupTouchToDismissKeyboard() {
        binding.createTripRoot.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                View focusedView = getCurrentFocus();
                if (focusedView instanceof EditText) {
                    hideKeyboard();
                    focusedView.clearFocus();
                }
            }
            return false; // Don't consume the event, let scrolling work
        });
    }

    /**
     * Set up window insets for proper safe area handling on notched devices
     */
    private void setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.headerSection, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars());
            v.setPadding(v.getPaddingLeft(), insets.top, v.getPaddingRight(), v.getPaddingBottom());
            return windowInsets;
        });
    }

    private void setupTypeSpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, activityTypes);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerType.setAdapter(adapter);
    }

    private void setupClickListeners() {
        // Cover photo
        binding.btnAddCoverPhoto.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));

        binding.btnRemoveCover.setOnClickListener(v -> {
            selectedCoverPhotoUri = null;
            binding.coverPhotoContainer.setVisibility(View.GONE);
            binding.btnAddCoverPhoto.setVisibility(View.VISIBLE);
        });

        // Publish button - dismiss keyboard first, then publish
        binding.btnPublish.setOnClickListener(v -> {
            hideKeyboard();
            publishTrip();
        });

        // Date pickers also dismiss keyboard
        binding.btnStartDate.setOnClickListener(v -> {
            hideKeyboard();
            showStartDatePicker();
        });

        binding.btnEndDate.setOnClickListener(v -> {
            hideKeyboard();
            showEndDatePicker();
        });

        // Destination picker dismisses keyboard
        binding.btnDestination.setOnClickListener(v -> {
            hideKeyboard();
            openPlacePicker();
        });
    }

    private void showCoverPhoto(Uri uri) {
        binding.btnAddCoverPhoto.setVisibility(View.GONE);
        binding.coverPhotoContainer.setVisibility(View.VISIBLE);
        Glide.with(this)
                .load(uri)
                .centerCrop()
                .into(binding.ivCoverPhoto);
    }

    private void openPlacePicker() {
        List<Place.Field> fields = Arrays.asList(
                Place.Field.ID,
                Place.Field.NAME,
                Place.Field.ADDRESS,
                Place.Field.LAT_LNG
        );
        Intent intent = new Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, fields)
                .build(this);
        placesLauncher.launch(intent);
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
        String groupSizeStr = binding.etGroupSize.getText().toString().trim();
        String budgetStr = binding.etBudget.getText().toString().trim();
        String description = binding.etDescription.getText().toString().trim();
        selectedType = activityTypes[binding.spinnerType.getSelectedItemPosition()];

        // Validate
        if (TextUtils.isEmpty(title)) {
            Toast.makeText(this, "Please enter a title", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(selectedDestination)) {
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
            Toast.makeText(this, "Please add a description", Toast.LENGTH_SHORT).show();
            return;
        }

        // Check email verification
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;

        if (!currentUser.isEmailVerified()) {
            Toast.makeText(this, R.string.error_email_not_verified_trip, Toast.LENGTH_LONG).show();
            return;
        }

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

        Trip trip = new Trip(tripId, currentUser.getUid(), driverName,
                selectedDestination, selectedDestination,
                selectedDestination, selectedDestination,
                departureMillis, arrivalMillis, seats, budget, "EUR", description);

        // Set additional fields
        trip.originLat = destLat != null ? destLat : 0.0;
        trip.originLng = destLng != null ? destLng : 0.0;
        trip.destLat = destLat != null ? destLat : 0.0;
        trip.destLng = destLng != null ? destLng : 0.0;
        trip.activityType = selectedType;

        // Set driver photo if available
        if (currentUser.getPhotoUrl() != null) {
            trip.driverPhotoUrl = currentUser.getPhotoUrl().toString();
        }

        // Upload cover photo to Firebase Storage first, then save trip
        if (selectedCoverPhotoUri != null) {
            uploadCoverPhotoAndSaveTrip(tripId, trip, currentUser);
        } else {
            saveTripToDatabase(tripId, trip, currentUser);
        }
    }

    private void showLoading(boolean show) {
        binding.progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        binding.btnPublish.setEnabled(!show);
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
        String chatId = "group_" + tripId;
        
        Map<String, Object> chatData = new HashMap<>();
        chatData.put("chatId", chatId);
        chatData.put("type", "group");
        chatData.put("tripId", tripId);
        chatData.put("name", trip.destinationCity);
        chatData.put("imageUrl", trip.imageUrl != null ? trip.imageUrl : "");
        chatData.put("createdAt", System.currentTimeMillis());
        chatData.put("lastMessage", "");
        chatData.put("lastMessageTime", System.currentTimeMillis());
        chatData.put("lastMessageSenderId", "");
        
        // Add creator as participant
        Map<String, Object> participants = new HashMap<>();
        participants.put(currentUser.getUid(), true);
        chatData.put("participants", participants);
        
        chatsRef.child(chatId).setValue(chatData);
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

    private void hideKeyboard() {
        View view = getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }
}
