package com.travellbudy.app.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.travellbudy.app.model.Result;
import com.travellbudy.app.models.Trip;
import com.travellbudy.app.repository.TripRepository;

/**
 * ViewModel for the Create Trip screen.
 * Validates input via util/ValidationUtils (called by Fragment before publish).
 * Delegates trip creation to TripRepository.
 */
public class CreateTripViewModel extends AndroidViewModel {

    private final TripRepository tripRepository;

    public CreateTripViewModel(@NonNull Application application) {
        super(application);
        this.tripRepository = new TripRepository(application);
    }

    /**
     * Publishes a new trip after validation passes in the Fragment.
     * @return LiveData with the created trip ID on success, or error message.
     */
    public LiveData<Result<String>> publishTrip(@NonNull Trip trip) {
        return tripRepository.createTrip(trip);
    }
}

