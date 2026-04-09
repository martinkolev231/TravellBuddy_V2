package com.travellbudy.app.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.auth.FirebaseUser;
import com.travellbudy.app.model.Result;
import com.travellbudy.app.repository.AuthRepository;
import com.travellbudy.app.repository.UserRepository;
import com.travellbudy.app.models.User;

/**
 * ViewModel for authentication screens (SignIn, SignUp).
 * Exposes sign-in/up results via LiveData<Result<FirebaseUser>>.
 * Creates user profile in Realtime DB on successful sign-up.
 */
public class AuthViewModel extends AndroidViewModel {

    private final AuthRepository authRepository;
    private final UserRepository userRepository;

    private MutableLiveData<Result<FirebaseUser>> signInResult;
    private MutableLiveData<Result<FirebaseUser>> signUpResult;
    private MutableLiveData<Result<Void>> resetPasswordResult;

    public AuthViewModel(@NonNull Application application) {
        super(application);
        this.authRepository = new AuthRepository(application);
        this.userRepository = new UserRepository(application);
    }

    /**
     * Sign in with email and password.
     */
    public LiveData<Result<FirebaseUser>> signIn(@NonNull String email,
                                                   @NonNull String password) {
        signInResult = (MutableLiveData<Result<FirebaseUser>>) authRepository.signIn(email, password);
        return signInResult;
    }

    /**
     * Sign up with email, password, and display name.
     * On success, creates a user profile in /users/{uid}.
     */
    public LiveData<Result<FirebaseUser>> signUp(@NonNull String email,
                                                   @NonNull String password,
                                                   @NonNull String displayName) {
        MutableLiveData<Result<FirebaseUser>> result = new MutableLiveData<>(Result.loading());

        authRepository.signUp(email, password, displayName).observeForever(authResult -> {
            if (authResult.isSuccess() && authResult.data != null) {
                // Create user profile in Realtime DB
                User user = new User(authResult.data.getUid(), displayName, email);
                userRepository.createUser(user).observeForever(dbResult -> {
                    if (dbResult.isSuccess()) {
                        result.setValue(Result.success(authResult.data));
                    } else {
                        // Auth succeeded but DB write failed — still return user
                        result.setValue(Result.success(authResult.data));
                    }
                });
            } else if (authResult.isError()) {
                result.setValue(Result.error(authResult.message));
            }
            // LOADING state: no-op, already set
        });

        signUpResult = result;
        return result;
    }

    /**
     * Sign in with Google ID token (from Credential Manager).
     * Creates user profile if it's a new user.
     */
    public LiveData<Result<FirebaseUser>> signInWithGoogle(@NonNull String idToken) {
        MutableLiveData<Result<FirebaseUser>> result = new MutableLiveData<>(Result.loading());

        authRepository.signInWithGoogle(idToken).observeForever(authResult -> {
            if (authResult.isSuccess() && authResult.data != null) {
                FirebaseUser fbUser = authResult.data;
                String displayName = fbUser.getDisplayName() != null
                        ? fbUser.getDisplayName() : "User";
                String email = fbUser.getEmail() != null ? fbUser.getEmail() : "";

                // Check if user profile already exists; create if not
                User user = new User(fbUser.getUid(), displayName, email);
                if (fbUser.getPhotoUrl() != null) {
                    user.photoUrl = fbUser.getPhotoUrl().toString();
                }
                // setValue will overwrite only if node doesn't exist (handled by rules)
                userRepository.createUser(user);
                result.setValue(Result.success(fbUser));
            } else if (authResult.isError()) {
                result.setValue(Result.error(authResult.message));
            }
        });

        return result;
    }

    /**
     * Send password reset email.
     */
    public LiveData<Result<Void>> resetPassword(@NonNull String email) {
        resetPasswordResult = (MutableLiveData<Result<Void>>) authRepository.resetPassword(email);
        return resetPasswordResult;
    }

    /**
     * Sign out the current user.
     */
    public void signOut() {
        authRepository.signOut();
    }

    /**
     * @return true if a user is currently signed in.
     */
    public boolean isAuthenticated() {
        return authRepository.isAuthenticated();
    }

    /**
     * @return The current FirebaseUser, or null.
     */
    public FirebaseUser getCurrentUser() {
        return authRepository.getCurrentUser();
    }
}

