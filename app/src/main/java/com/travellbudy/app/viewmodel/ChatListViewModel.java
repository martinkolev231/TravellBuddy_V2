package com.travellbudy.app.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.travellbudy.app.firebase.FirebaseManager;
import com.travellbudy.app.model.Result;
import com.travellbudy.app.models.SeatRequest;
import com.travellbudy.app.models.Trip;
import com.travellbudy.app.util.Constants;
import com.travellbudy.app.util.FirebaseErrorMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * ViewModel for the Chat List screen.
 * Finds all trips where the current user is driver or approved rider,
 * which represent active chats.
 */
public class ChatListViewModel extends AndroidViewModel {

    private final FirebaseManager firebase;
    private final Application application;
    private final MutableLiveData<Result<List<Trip>>> chatTrips = new MutableLiveData<>();
    private ValueEventListener tripsListener;
    private DatabaseReference tripsRef;

    public ChatListViewModel(@NonNull Application application) {
        super(application);
        this.firebase = FirebaseManager.getInstance();
        this.application = application;
        loadChatTrips();
    }

    private void loadChatTrips() {
        String currentUid = firebase.getCurrentUid();
        if (currentUid == null) {
            chatTrips.setValue(Result.error("Not authenticated"));
            return;
        }

        chatTrips.setValue(Result.loading());
        tripsRef = firebase.getTripsRef();
        tripsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Trip> result = new ArrayList<>();
                List<DataSnapshot> tripSnapshots = new ArrayList<>();

                for (DataSnapshot child : snapshot.getChildren()) {
                    Trip trip = child.getValue(Trip.class);
                    if (trip == null) continue;

                    // Driver sees all their trips
                    if (currentUid.equals(trip.driverUid)) {
                        result.add(trip);
                        continue;
                    }
                    tripSnapshots.add(child);
                }

                // Check approved rider status for remaining trips
                if (tripSnapshots.isEmpty()) {
                    chatTrips.setValue(Result.success(result));
                    return;
                }

                final int[] remaining = {tripSnapshots.size()};
                for (DataSnapshot tripSnap : tripSnapshots) {
                    Trip trip = tripSnap.getValue(Trip.class);
                    if (trip == null) {
                        remaining[0]--;
                        if (remaining[0] <= 0) chatTrips.setValue(Result.success(result));
                        continue;
                    }

                    firebase.getRequestsRef(trip.tripId)
                            .orderByChild(Constants.FIELD_RIDER_UID)
                            .equalTo(currentUid)
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot reqSnapshot) {
                                    for (DataSnapshot reqSnap : reqSnapshot.getChildren()) {
                                        SeatRequest req = reqSnap.getValue(SeatRequest.class);
                                        if (req != null && Constants.REQUEST_APPROVED.equals(req.status)) {
                                            result.add(trip);
                                            break;
                                        }
                                    }
                                    remaining[0]--;
                                    if (remaining[0] <= 0) {
                                        chatTrips.setValue(Result.success(result));
                                    }
                                }

                                @Override
                                public void onCancelled(@NonNull DatabaseError error) {
                                    remaining[0]--;
                                    if (remaining[0] <= 0) {
                                        chatTrips.setValue(Result.success(result));
                                    }
                                }
                            });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                chatTrips.setValue(Result.error(
                        FirebaseErrorMapper.map(application, error)));
            }
        };
        tripsRef.addValueEventListener(tripsListener);
    }

    public LiveData<Result<List<Trip>>> getChatTrips() {
        return chatTrips;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (tripsRef != null && tripsListener != null) {
            tripsRef.removeEventListener(tripsListener);
        }
    }
}

