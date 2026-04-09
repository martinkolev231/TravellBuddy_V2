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
import com.travellbudy.app.repository.TripRepository;
import com.travellbudy.app.util.Constants;
import com.travellbudy.app.util.FirebaseErrorMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * ViewModel for My Trips screen.
 * Loads trips where user is driver or approved/pending rider.
 * Supports tab switching between "As Driver" and "As Rider".
 */
public class MyTripsViewModel extends AndroidViewModel {

    private final TripRepository tripRepository;
    private final FirebaseManager firebase;
    private final Application application;

    private LiveData<Result<List<Trip>>> driverTrips;
    private final MutableLiveData<Result<List<Trip>>> riderTrips = new MutableLiveData<>();
    private ValueEventListener riderTripsListener;
    private DatabaseReference riderTripsRef;

    public MyTripsViewModel(@NonNull Application application) {
        super(application);
        this.application = application;
        this.tripRepository = new TripRepository(application);
        this.firebase = FirebaseManager.getInstance();
    }

    /**
     * Loads trips where the user is the driver.
     */
    public LiveData<Result<List<Trip>>> getDriverTrips() {
        String uid = firebase.getCurrentUid();
        if (uid == null) {
            MutableLiveData<Result<List<Trip>>> error = new MutableLiveData<>();
            error.setValue(Result.error("Not authenticated"));
            return error;
        }
        if (driverTrips == null) {
            driverTrips = tripRepository.observeDriverTrips(uid);
        }
        return driverTrips;
    }

    /**
     * Loads trips where the user has an approved or pending request.
     */
    public LiveData<Result<List<Trip>>> getRiderTrips() {
        String currentUid = firebase.getCurrentUid();
        if (currentUid == null) {
            riderTrips.setValue(Result.error("Not authenticated"));
            return riderTrips;
        }

        riderTrips.setValue(Result.loading());
        riderTripsRef = firebase.getTripsRef();
        riderTripsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Trip> trips = new ArrayList<>();
                List<DataSnapshot> tripSnapshots = new ArrayList<>();

                for (DataSnapshot child : snapshot.getChildren()) {
                    tripSnapshots.add(child);
                }

                if (tripSnapshots.isEmpty()) {
                    riderTrips.setValue(Result.success(trips));
                    return;
                }

                final int[] remaining = {tripSnapshots.size()};
                for (DataSnapshot tripSnap : tripSnapshots) {
                    Trip trip = tripSnap.getValue(Trip.class);
                    if (trip == null) {
                        remaining[0]--;
                        if (remaining[0] <= 0) riderTrips.setValue(Result.success(trips));
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
                                        if (req != null && (Constants.REQUEST_APPROVED.equals(req.status)
                                                || Constants.REQUEST_PENDING.equals(req.status))) {
                                            trips.add(trip);
                                            break;
                                        }
                                    }
                                    remaining[0]--;
                                    if (remaining[0] <= 0) {
                                        riderTrips.setValue(Result.success(trips));
                                    }
                                }

                                @Override
                                public void onCancelled(@NonNull DatabaseError error) {
                                    remaining[0]--;
                                    if (remaining[0] <= 0) {
                                        riderTrips.setValue(Result.success(trips));
                                    }
                                }
                            });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                riderTrips.setValue(Result.error(
                        FirebaseErrorMapper.map(application, error)));
            }
        };
        riderTripsRef.addValueEventListener(riderTripsListener);

        return riderTrips;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        tripRepository.removeListeners();
        if (riderTripsRef != null && riderTripsListener != null) {
            riderTripsRef.removeEventListener(riderTripsListener);
        }
    }
}

