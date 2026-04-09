package com.travellbudy.app.repository;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;
import com.travellbudy.app.firebase.FirebaseManager;
import com.travellbudy.app.model.Result;
import com.travellbudy.app.models.SeatRequest;
import com.travellbudy.app.models.TripMember;
import com.travellbudy.app.util.Constants;
import com.travellbudy.app.util.FirebaseErrorMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Repository for seat request operations on /tripRequests/{tripId}.
 * Handles creating, approving, denying, and canceling requests.
 * On approve, writes rider to /tripMembers and atomically decrements availableSeats.
 */
public class RequestRepository {

    private final FirebaseManager firebase;
    private final Application application;
    private ValueEventListener requestsListener;
    private DatabaseReference requestsRef;

    public RequestRepository(@NonNull Application application) {
        this.application = application;
        this.firebase = FirebaseManager.getInstance();
    }

    /**
     * Observes all requests for a trip.
     */
    public LiveData<Result<List<SeatRequest>>> observeRequests(@NonNull String tripId) {
        MutableLiveData<Result<List<SeatRequest>>> result = new MutableLiveData<>(Result.loading());

        requestsRef = firebase.getRequestsRef(tripId);
        requestsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<SeatRequest> requests = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    SeatRequest req = child.getValue(SeatRequest.class);
                    if (req != null) requests.add(req);
                }
                result.setValue(Result.success(requests));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                result.setValue(Result.error(
                        FirebaseErrorMapper.map(application, error)));
            }
        };
        requestsRef.addValueEventListener(requestsListener);

        return result;
    }

    /**
     * Creates a new seat request.
     */
    public LiveData<Result<Void>> createRequest(@NonNull SeatRequest request) {
        MutableLiveData<Result<Void>> result = new MutableLiveData<>(Result.loading());

        DatabaseReference ref = firebase.getRequestsRef(request.tripId);
        String requestId = ref.push().getKey();
        if (requestId == null) {
            result.setValue(Result.error("Failed to create request"));
            return result;
        }

        request.requestId = requestId;
        ref.child(requestId).setValue(request.toMap())
                .addOnSuccessListener(aVoid -> result.setValue(Result.success(null)))
                .addOnFailureListener(e -> result.setValue(Result.error(e.getMessage())));

        return result;
    }

    /**
     * Approves a request: atomically decrements availableSeats, updates status,
     * and writes rider to /tripMembers.
     */
    public LiveData<Result<Void>> approveRequest(@NonNull SeatRequest request) {
        MutableLiveData<Result<Void>> result = new MutableLiveData<>(Result.loading());

        DatabaseReference seatsRef = firebase.getTripRef(request.tripId)
                .child(Constants.FIELD_AVAILABLE_SEATS);

        seatsRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                Integer seats = currentData.getValue(Integer.class);
                if (seats == null || seats <= 0) return Transaction.abort();
                currentData.setValue(seats - request.seatsRequested);
                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot snapshot) {
                if (committed) {
                    // Update request status
                    DatabaseReference reqRef = firebase.getRequestsRef(request.tripId)
                            .child(request.requestId);
                    reqRef.child(Constants.FIELD_STATUS).setValue(Constants.REQUEST_APPROVED);
                    reqRef.child(Constants.FIELD_UPDATED_AT).setValue(System.currentTimeMillis());

                    // Add rider to tripMembers
                    TripMember member = new TripMember(
                            request.riderUid, "rider", request.seatsRequested);
                    firebase.getMembersRef(request.tripId)
                            .child(request.riderUid)
                            .setValue(member.toMap());

                    // Check if trip is now full
                    Integer remaining = snapshot.getValue(Integer.class);
                    if (remaining != null && remaining <= 0) {
                        firebase.getTripRef(request.tripId)
                                .child(Constants.FIELD_STATUS)
                                .setValue(Constants.STATUS_FULL);
                    }

                    result.setValue(Result.success(null));
                } else {
                    result.setValue(Result.error("No available seats"));
                }
            }
        });

        return result;
    }

    /**
     * Denies a request.
     */
    public LiveData<Result<Void>> denyRequest(@NonNull String tripId,
                                                @NonNull String requestId) {
        MutableLiveData<Result<Void>> result = new MutableLiveData<>(Result.loading());

        DatabaseReference reqRef = firebase.getRequestsRef(tripId).child(requestId);
        reqRef.child(Constants.FIELD_STATUS).setValue(Constants.REQUEST_DENIED);
        reqRef.child(Constants.FIELD_UPDATED_AT).setValue(System.currentTimeMillis())
                .addOnSuccessListener(aVoid -> result.setValue(Result.success(null)))
                .addOnFailureListener(e -> result.setValue(Result.error(e.getMessage())));

        return result;
    }

    /**
     * Cancels a request (by rider).
     */
    public LiveData<Result<Void>> cancelRequest(@NonNull String tripId,
                                                  @NonNull String requestId) {
        MutableLiveData<Result<Void>> result = new MutableLiveData<>(Result.loading());

        DatabaseReference reqRef = firebase.getRequestsRef(tripId).child(requestId);
        reqRef.child(Constants.FIELD_STATUS).setValue(Constants.REQUEST_CANCELED_BY_RIDER);
        reqRef.child(Constants.FIELD_UPDATED_AT).setValue(System.currentTimeMillis())
                .addOnSuccessListener(aVoid -> result.setValue(Result.success(null)))
                .addOnFailureListener(e -> result.setValue(Result.error(e.getMessage())));

        return result;
    }

    /**
     * Removes the requests listener. Call in ViewModel.onCleared().
     */
    public void removeListeners() {
        if (requestsRef != null && requestsListener != null) {
            requestsRef.removeEventListener(requestsListener);
        }
    }
}

