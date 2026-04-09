package com.travellbudy.app.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.travellbudy.app.firebase.FirebaseManager;
import com.travellbudy.app.model.Result;
import com.travellbudy.app.models.User;
import com.travellbudy.app.repository.UserRepository;

import java.util.Map;

/**
 * ViewModel for Profile and Edit Profile screens.
 * Observes user data in realtime, provides update capability.
 */
public class ProfileViewModel extends AndroidViewModel {

    private final UserRepository userRepository;
    private LiveData<Result<User>> userLiveData;
    private String observedUid;

    public ProfileViewModel(@NonNull Application application) {
        super(application);
        this.userRepository = new UserRepository(application);
    }

    /**
     * Starts observing the user profile. If uid is null, observes current user.
     */
    public LiveData<Result<User>> getUser(String uid) {
        if (uid == null) {
            uid = FirebaseManager.getInstance().getCurrentUid();
        }
        if (uid == null) {
            MutableLiveData<Result<User>> error = new MutableLiveData<>();
            error.setValue(Result.error("Not authenticated"));
            return error;
        }
        if (userLiveData == null || !uid.equals(observedUid)) {
            observedUid = uid;
            userLiveData = userRepository.observeUser(uid);
        }
        return userLiveData;
    }

    /**
     * Updates the current user's profile fields.
     */
    public LiveData<Result<Void>> updateProfile(@NonNull Map<String, Object> updates) {
        String uid = FirebaseManager.getInstance().getCurrentUid();
        if (uid == null) {
            MutableLiveData<Result<Void>> error = new MutableLiveData<>();
            error.setValue(Result.error("Not authenticated"));
            return error;
        }
        return userRepository.updateProfile(uid, updates);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        userRepository.removeProfileListener();
    }
}

