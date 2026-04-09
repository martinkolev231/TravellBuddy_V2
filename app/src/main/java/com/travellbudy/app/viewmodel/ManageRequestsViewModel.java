package com.travellbudy.app.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.travellbudy.app.model.Result;
import com.travellbudy.app.models.SeatRequest;
import com.travellbudy.app.repository.RequestRepository;

import java.util.List;

/**
 * ViewModel for the Manage Requests screen (driver view).
 * Observes all requests for a trip, provides approve/deny actions.
 */
public class ManageRequestsViewModel extends AndroidViewModel {

    private final RequestRepository requestRepository;
    private LiveData<Result<List<SeatRequest>>> requestsLiveData;
    private String tripId;

    public ManageRequestsViewModel(@NonNull Application application) {
        super(application);
        this.requestRepository = new RequestRepository(application);
    }

    /**
     * Initializes observation for requests on the given trip.
     */
    public void loadRequests(@NonNull String tripId) {
        this.tripId = tripId;
        requestsLiveData = requestRepository.observeRequests(tripId);
    }

    public LiveData<Result<List<SeatRequest>>> getRequests() {
        return requestsLiveData;
    }

    /**
     * Approves a seat request (atomically decrements seats, writes tripMember).
     */
    public LiveData<Result<Void>> approveRequest(@NonNull SeatRequest request) {
        return requestRepository.approveRequest(request);
    }

    /**
     * Denies a seat request.
     */
    public LiveData<Result<Void>> denyRequest(@NonNull SeatRequest request) {
        return requestRepository.denyRequest(request.tripId, request.requestId);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        requestRepository.removeListeners();
    }
}

