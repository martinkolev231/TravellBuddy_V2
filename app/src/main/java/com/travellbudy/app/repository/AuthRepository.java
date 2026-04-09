package com.travellbudy.app.repository;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.travellbudy.app.firebase.FirebaseManager;
import com.travellbudy.app.model.Result;
import com.travellbudy.app.util.FirebaseErrorMapper;

/**
 * Repository for all Firebase Authentication operations.
 * Wraps FirebaseAuth calls and exposes results via LiveData<Result<FirebaseUser>>.
 *
 * <p>All callbacks arrive on the main thread (FirebaseAuth guarantee),
 * so we use {@code setValue()} (not {@code postValue()}).
 */
public class AuthRepository {

    private final FirebaseAuth auth;
    private final Application application;

    public AuthRepository(@NonNull Application application) {
        this.application = application;
        this.auth = FirebaseManager.getInstance().getAuth();
    }

    /**
     * Sign in with email and password.
     */
    public LiveData<Result<FirebaseUser>> signIn(@NonNull String email, @NonNull String password) {
        MutableLiveData<Result<FirebaseUser>> result = new MutableLiveData<>(Result.loading());

        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult ->
                        result.setValue(Result.success(authResult.getUser())))
                .addOnFailureListener(e ->
                        result.setValue(Result.error(
                                FirebaseErrorMapper.mapAuthException(application, e))));

        return result;
    }

    /**
     * Create a new account with email, password, and display name.
     */
    public LiveData<Result<FirebaseUser>> signUp(@NonNull String email,
                                                  @NonNull String password,
                                                  @NonNull String displayName) {
        MutableLiveData<Result<FirebaseUser>> result = new MutableLiveData<>(Result.loading());

        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser user = authResult.getUser();
                    if (user != null) {
                        // Set display name on the Firebase Auth profile
                        UserProfileChangeRequest profileUpdate =
                                new UserProfileChangeRequest.Builder()
                                        .setDisplayName(displayName)
                                        .build();
                        user.updateProfile(profileUpdate)
                                .addOnCompleteListener(task ->
                                        result.setValue(Result.success(user)));
                    } else {
                        result.setValue(Result.error("Account creation failed"));
                    }
                })
                .addOnFailureListener(e ->
                        result.setValue(Result.error(
                                FirebaseErrorMapper.mapAuthException(application, e))));

        return result;
    }

    /**
     * Sign in with a Google ID token (from Credential Manager).
     */
    public LiveData<Result<FirebaseUser>> signInWithGoogle(@NonNull String idToken) {
        MutableLiveData<Result<FirebaseUser>> result = new MutableLiveData<>(Result.loading());

        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        auth.signInWithCredential(credential)
                .addOnSuccessListener(authResult ->
                        result.setValue(Result.success(authResult.getUser())))
                .addOnFailureListener(e ->
                        result.setValue(Result.error(
                                FirebaseErrorMapper.mapAuthException(application, e))));

        return result;
    }

    /**
     * Send a password reset email.
     */
    public LiveData<Result<Void>> resetPassword(@NonNull String email) {
        MutableLiveData<Result<Void>> result = new MutableLiveData<>(Result.loading());

        auth.sendPasswordResetEmail(email)
                .addOnSuccessListener(aVoid -> result.setValue(Result.success(null)))
                .addOnFailureListener(e ->
                        result.setValue(Result.error(
                                FirebaseErrorMapper.mapAuthException(application, e))));

        return result;
    }

    /**
     * Send an email verification to the current user.
     */
    public LiveData<Result<Void>> sendEmailVerification() {
        MutableLiveData<Result<Void>> result = new MutableLiveData<>(Result.loading());

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            result.setValue(Result.error("Not signed in"));
            return result;
        }

        user.sendEmailVerification()
                .addOnSuccessListener(aVoid -> result.setValue(Result.success(null)))
                .addOnFailureListener(e ->
                        result.setValue(Result.error(
                                FirebaseErrorMapper.mapAuthException(application, e))));

        return result;
    }

    /**
     * Sign out the current user.
     */
    public void signOut() {
        auth.signOut();
    }

    /**
     * Delete the current user's Firebase Auth account.
     */
    public LiveData<Result<Void>> deleteAccount() {
        MutableLiveData<Result<Void>> result = new MutableLiveData<>(Result.loading());

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            result.setValue(Result.error("Not signed in"));
            return result;
        }

        user.delete()
                .addOnSuccessListener(aVoid -> result.setValue(Result.success(null)))
                .addOnFailureListener(e ->
                        result.setValue(Result.error(
                                FirebaseErrorMapper.mapAuthException(application, e))));

        return result;
    }

    /**
     * @return The currently signed-in user, or null.
     */
    public FirebaseUser getCurrentUser() {
        return auth.getCurrentUser();
    }

    /**
     * @return true if a user is currently authenticated.
     */
    public boolean isAuthenticated() {
        return auth.getCurrentUser() != null;
    }
}

