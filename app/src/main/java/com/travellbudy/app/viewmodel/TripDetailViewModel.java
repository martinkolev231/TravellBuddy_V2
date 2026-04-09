package com.travellbudy.app.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.travellbudy.app.firebase.FirebaseManager;
import com.travellbudy.app.model.Result;
import com.travellbudy.app.models.SeatRequest;
import com.travellbudy.app.models.Trip;
import com.travellbudy.app.repository.RequestRepository;
import com.travellbudy.app.repository.TripRepository;

/**
 * ViewModel for the Trip Detail screen.
 * Observes a single trip and the current user's seat request for it.
 * Provides actions: requestSeat, cancelTrip.
 */
public class TripDetailViewModel extends AndroidViewModel {

    private final TripRepository tripRepository;
    private final RequestRepository requestRepository;
    private LiveData<Result<Trip>> tripLiveData;
    private LiveData<Result<java.util.List<SeatRequest>>> requestsLiveData;
    private String tripId;

    public TripDetailViewModel(@NonNull Application application) {
        super(application);
        this.tripRepository = new TripRepository(application);
        this.requestRepository = new RequestRepository(application);
    }

    /**
     * Initializes observation for the given trip.
     */
    public void loadTrip(@NonNull String tripId) {
        this.tripId = tripId;
        tripLiveData = tripRepository.observeTrip(tripId);
        requestsLiveData = requestRepository.observeRequests(tripId);
    }

    public LiveData<Result<Trip>> getTrip() {
        return tripLiveData;
    }

    public LiveData<Result<java.util.List<SeatRequest>>> getRequests() {
        return requestsLiveData;
    }

    /**
     * Submits a seat request for the current user.
     */
    public LiveData<Result<Void>> requestSeat(@NonNull SeatRequest request) {
        return requestRepository.createRequest(request);
    }

    /**
     * Cancels the trip (driver only).
     */
    public LiveData<Result<Void>> cancelTrip() {
        if (tripId == null) {
            MutableLiveData<Result<Void>> error = new MutableLiveData<>();
            error.setValue(Result.error("No trip loaded"));
            return error;
        }
        return tripRepository.updateTripStatus(tripId,
                com.travellbudy.app.util.Constants.STATUS_CANCELED);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        tripRepository.removeListeners();
        requestRepository.removeListeners();
    }
}

