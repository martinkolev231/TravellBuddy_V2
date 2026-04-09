package com.travellbudy.app.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.travellbudy.app.model.Result;
import com.travellbudy.app.models.Rating;
import com.travellbudy.app.repository.RatingRepository;

import java.util.List;

/**
 * ViewModel for the Ratings screen.
 * Observes all ratings for a given user and provides them via LiveData.
 */
public class RatingsViewModel extends AndroidViewModel {

    private final RatingRepository ratingRepository;
    private LiveData<Result<List<Rating>>> ratingsLiveData;

    public RatingsViewModel(@NonNull Application application) {
        super(application);
        this.ratingRepository = new RatingRepository(application);
    }

    /**
     * Loads all ratings where the given user is the reviewee.
     */
    public LiveData<Result<List<Rating>>> getRatings(@NonNull String userId) {
        if (ratingsLiveData == null) {
            ratingsLiveData = ratingRepository.observeRatingsForUser(userId);
        }
        return ratingsLiveData;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        ratingRepository.removeListeners();
    }
}

