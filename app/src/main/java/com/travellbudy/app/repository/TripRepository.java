package com.travellbudy.app.repository;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.travellbudy.app.firebase.FirebaseManager;
import com.travellbudy.app.model.Result;
import com.travellbudy.app.models.GroupChat;
import com.travellbudy.app.models.Trip;
import com.travellbudy.app.models.TripMember;
import com.travellbudy.app.util.Constants;
import com.travellbudy.app.util.FirebaseErrorMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Repository for Trip CRUD on /trips.
 * Handles querying open trips, driver's trips, creating trips,
 * and writing the driver to /tripMembers on creation.
 */
public class TripRepository {

    private final FirebaseManager firebase;
    private final Application application;

    // Listener tracking for cleanup
    private ValueEventListener openTripsListener;
    private Query openTripsQuery;
    private ValueEventListener driverTripsListener;
    private Query driverTripsQuery;
    private ValueEventListener tripDetailListener;
    private DatabaseReference tripDetailRef;

    public TripRepository(@NonNull Application application) {
        this.application = application;
        this.firebase = FirebaseManager.getInstance();
    }

    /**
     * Observes all open trips ordered by departureTime (future only).
     * Used by Home Feed.
     */
    public LiveData<Result<List<Trip>>> observeOpenTrips() {
        MutableLiveData<Result<List<Trip>>> result = new MutableLiveData<>(Result.loading());

        openTripsQuery = firebase.getTripsRef()
                .orderByChild(Constants.FIELD_DEPARTURE_TIME)
                .startAt(System.currentTimeMillis());

        openTripsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Trip> trips = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    Trip trip = child.getValue(Trip.class);
                    if (trip != null && !Constants.STATUS_CANCELED.equals(trip.status)) {
                        trips.add(trip);
                    }
                }
                result.setValue(Result.success(trips));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                result.setValue(Result.error(
                        FirebaseErrorMapper.map(application, error)));
            }
        };
        openTripsQuery.addValueEventListener(openTripsListener);

        return result;
    }

    /**
     * Observes trips where the given user is the driver.
     */
    public LiveData<Result<List<Trip>>> observeDriverTrips(@NonNull String uid) {
        MutableLiveData<Result<List<Trip>>> result = new MutableLiveData<>(Result.loading());

        driverTripsQuery = firebase.getTripsRef()
                .orderByChild(Constants.FIELD_DRIVER_UID)
                .equalTo(uid);

        driverTripsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Trip> trips = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    Trip trip = child.getValue(Trip.class);
                    if (trip != null) trips.add(trip);
                }
                result.setValue(Result.success(trips));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                result.setValue(Result.error(
                        FirebaseErrorMapper.map(application, error)));
            }
        };
        driverTripsQuery.addValueEventListener(driverTripsListener);

        return result;
    }

    /**
     * Observes a single trip by ID.
     */
    public LiveData<Result<Trip>> observeTrip(@NonNull String tripId) {
        MutableLiveData<Result<Trip>> result = new MutableLiveData<>(Result.loading());

        tripDetailRef = firebase.getTripRef(tripId);
        tripDetailListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Trip trip = snapshot.getValue(Trip.class);
                if (trip != null) {
                    result.setValue(Result.success(trip));
                } else {
                    result.setValue(Result.error("Adventure not found"));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                result.setValue(Result.error(
                        FirebaseErrorMapper.map(application, error)));
            }
        };
        tripDetailRef.addValueEventListener(tripDetailListener);

        return result;
    }

    /**
     * Creates a new trip and adds the driver as a tripMember.
     * Also creates a group chat for the trip.
     * If trip.tripId is already set, it will use that ID instead of generating a new one.
     */
    public LiveData<Result<String>> createTrip(@NonNull Trip trip) {
        MutableLiveData<Result<String>> result = new MutableLiveData<>(Result.loading());

        DatabaseReference tripsRef = firebase.getTripsRef();
        
        // Use existing tripId if set, otherwise generate a new one
        String tripId;
        if (trip.tripId != null && !trip.tripId.isEmpty()) {
            tripId = trip.tripId;
        } else {
            tripId = tripsRef.push().getKey();
            if (tripId == null) {
                result.setValue(Result.error("Failed to generate adventure ID"));
                return result;
            }
            trip.tripId = tripId;
        }

        tripsRef.child(tripId).setValue(trip.toMap())
                .addOnSuccessListener(aVoid -> {
                    // Add driver as tripMember (required by security rules for ratings)
                    TripMember driverMember = new TripMember(trip.driverUid, "driver", 0);
                    firebase.getMembersRef(tripId).child(trip.driverUid)
                            .setValue(driverMember.toMap());

                    // Create group chat for the trip
                    createGroupChatForTrip(tripId, trip);

                    result.setValue(Result.success(tripId));
                })
                .addOnFailureListener(e ->
                        result.setValue(Result.error(e.getMessage())));

        return result;
    }

    /**
     * Creates a group chat for a trip with the trip title as the chat name.
     */
    private void createGroupChatForTrip(@NonNull String tripId, @NonNull Trip trip) {
        // Use trip title (stored in carModel) or destination as chat name
        String chatName = trip.carModel != null && !trip.carModel.isEmpty() 
                ? trip.carModel 
                : trip.destinationCity;

        // Include the trip's cover image URL in the group chat
        String imageUrl = trip.imageUrl != null ? trip.imageUrl : "";

        GroupChat groupChat = new GroupChat(tripId, tripId, chatName, imageUrl, trip.driverUid);
        
        // Save group chat info at /chats/{tripId}/info
        firebase.getChatRef(tripId).child("info").setValue(groupChat.toMap())
                .addOnSuccessListener(aVoid -> {
                    android.util.Log.d("TripRepository", "Group chat created successfully for trip: " + tripId);
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("TripRepository", "Failed to create group chat: " + e.getMessage());
                });
        
        // Add chat to driver's userChats
        Map<String, Object> userChatEntry = new HashMap<>();
        userChatEntry.put("chatId", tripId);
        userChatEntry.put("tripId", tripId);
        userChatEntry.put("otherPartyName", chatName);  // Use trip name for group chats
        userChatEntry.put("isGroupChat", true);
        userChatEntry.put("lastMessage", "");
        userChatEntry.put("lastMessageTime", System.currentTimeMillis());
        
        firebase.getUserChatsRef(trip.driverUid).child(tripId).setValue(userChatEntry)
                .addOnSuccessListener(aVoid -> {
                    android.util.Log.d("TripRepository", "UserChat entry created for user: " + trip.driverUid);
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("TripRepository", "Failed to create userChat entry: " + e.getMessage());
                });
    }

    /**
     * Updates the trip status (e.g., cancel, mark completed).
     */
    public LiveData<Result<Void>> updateTripStatus(@NonNull String tripId,
                                                     @NonNull String status) {
        MutableLiveData<Result<Void>> result = new MutableLiveData<>(Result.loading());

        firebase.getTripRef(tripId).child(Constants.FIELD_STATUS).setValue(status)
                .addOnSuccessListener(aVoid -> result.setValue(Result.success(null)))
                .addOnFailureListener(e -> result.setValue(Result.error(e.getMessage())));

        return result;
    }

    /**
     * Removes all listeners. Must be called in ViewModel.onCleared().
     */
    public void removeListeners() {
        if (openTripsQuery != null && openTripsListener != null) {
            openTripsQuery.removeEventListener(openTripsListener);
        }
        if (driverTripsQuery != null && driverTripsListener != null) {
            driverTripsQuery.removeEventListener(driverTripsListener);
        }
        if (tripDetailRef != null && tripDetailListener != null) {
            tripDetailRef.removeEventListener(tripDetailListener);
        }
    }
}

