package com.travellbudy.app.viewmodel;

import android.app.Application;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import com.travellbudy.app.model.Result;
import com.travellbudy.app.models.Trip;
import com.travellbudy.app.repository.TripRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * ViewModel for the Home Feed screen.
 * Loads open trips, exposes filtered list via LiveData, handles search.
 * Removes Firebase listener in onCleared() to prevent leaks.
 */
public class HomeViewModel extends AndroidViewModel {

    private final TripRepository tripRepository;
    private final MediatorLiveData<Result<List<Trip>>> filteredTrips = new MediatorLiveData<>();
    private LiveData<Result<List<Trip>>> allTripsSource;
    private List<Trip> allTripsCache = new ArrayList<>();
    private String currentQuery = "";

    public HomeViewModel(@NonNull Application application) {
        super(application);
        this.tripRepository = new TripRepository(application);
        loadTrips();
    }

    private void loadTrips() {
        allTripsSource = tripRepository.observeOpenTrips();

        filteredTrips.addSource(allTripsSource, result -> {
            if (result.isSuccess() && result.data != null) {
                allTripsCache = result.data;
                applyFilter();
            } else {
                filteredTrips.setValue(result);
            }
        });
    }

    /**
     * Filters the cached trip list by origin/destination city.
     */
    public void filterTrips(@NonNull String query) {
        this.currentQuery = query;
        applyFilter();
    }

    private void applyFilter() {
        if (TextUtils.isEmpty(currentQuery)) {
            filteredTrips.setValue(Result.success(allTripsCache));
            return;
        }

        String lower = currentQuery.toLowerCase(Locale.getDefault());
        List<Trip> filtered = new ArrayList<>();
        for (Trip trip : allTripsCache) {
            if ((trip.originCity != null && trip.originCity.toLowerCase(Locale.getDefault()).contains(lower)) ||
                    (trip.destinationCity != null && trip.destinationCity.toLowerCase(Locale.getDefault()).contains(lower))) {
                filtered.add(trip);
            }
        }
        filteredTrips.setValue(Result.success(filtered));
    }

    /**
     * @return LiveData with filtered trips for the Home Feed RecyclerView.
     */
    public LiveData<Result<List<Trip>>> getTrips() {
        return filteredTrips;
    }

    /**
     * Forces a refresh by re-reading from cache (Firebase handles realtime updates).
     */
    public void refresh() {
        applyFilter();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        tripRepository.removeListeners();
    }
}

