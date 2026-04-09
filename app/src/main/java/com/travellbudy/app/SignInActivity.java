package com.travellbudy.app;

import android.content.Intent;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;

import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.database.FirebaseDatabase;
import com.travellbudy.app.databinding.ActivitySignInBinding;
import com.travellbudy.app.models.User;

import java.util.concurrent.Executors;

public class SignInActivity extends AppCompatActivity {

    private static final String TAG = "SignInActivity";

    private ActivitySignInBinding binding;
    private FirebaseAuth mAuth;
    private CredentialManager credentialManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySignInBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();
        credentialManager = CredentialManager.create(this);

        // Back button
        binding.btnBack.setOnClickListener(v -> finish());

        binding.btnSignIn.setOnClickListener(v -> attemptSignIn());

        binding.btnGoToSignUp.setOnClickListener(v -> {
            startActivity(new Intent(this, SignUpActivity.class));
            finish();
        });

        binding.btnForgotPassword.setOnClickListener(v -> resetPassword());

        binding.btnGoogleSignIn.setOnClickListener(v -> signInWithGoogle());
    }

    // =========================================================================
    // Email / Password sign-in
    // =========================================================================

    private void attemptSignIn() {
        binding.tilEmail.setError(null);
        binding.tilPassword.setError(null);

        String email = binding.etEmail.getText().toString().trim();
        String password = binding.etPassword.getText().toString();

        if (TextUtils.isEmpty(email)) {
            binding.tilEmail.setError(getString(R.string.error_empty_email));
            binding.etEmail.requestFocus();
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.setError(getString(R.string.error_invalid_email));
            binding.etEmail.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(password)) {
            binding.tilPassword.setError(getString(R.string.error_empty_password));
            binding.etPassword.requestFocus();
            return;
        }

        showLoading(true);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    showLoading(false);
                    if (task.isSuccessful()) {
                        navigateToHome();
                    } else {
                        Toast.makeText(this, R.string.error_auth_failed, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // =========================================================================
    // Google Sign-In via Credential Manager (modern, non-deprecated approach)
    // =========================================================================

    private void signInWithGoogle() {
        showLoading(true);

        // Build the Google ID option with the Web Client ID from Firebase Console
        String webClientId = getString(R.string.google_web_client_id);

        GetGoogleIdOption googleIdOption = new GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false) // Show all accounts, not just previously authorized
                .setServerClientId(webClientId)       // Web Client ID from Firebase
                .build();

        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build();

        // Use CancellationSignal so the request can be cancelled if activity is destroyed
        CancellationSignal cancellationSignal = new CancellationSignal();

        credentialManager.getCredentialAsync(
                this,
                request,
                cancellationSignal,
                Executors.newSingleThreadExecutor(),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse result) {
                        // This callback runs on the executor thread — switch to main thread
                        runOnUiThread(() -> handleGoogleCredential(result));
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        Log.w(TAG, "Google sign-in failed", e);
                        runOnUiThread(() -> {
                            showLoading(false);
                            Toast.makeText(SignInActivity.this,
                                    R.string.error_google_sign_in, Toast.LENGTH_SHORT).show();
                        });
                    }
                }
        );
    }

    /**
     * Extracts the Google ID token from the Credential Manager result
     * and uses it to authenticate with Firebase.
     */
    private void handleGoogleCredential(GetCredentialResponse response) {
        Credential credential = response.getCredential();

        if (credential instanceof CustomCredential) {
            CustomCredential customCredential = (CustomCredential) credential;

            if (GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                    .equals(customCredential.getType())) {

                // Extract the Google ID token
                GoogleIdTokenCredential googleIdTokenCredential =
                        GoogleIdTokenCredential.createFrom(customCredential.getData());

                String idToken = googleIdTokenCredential.getIdToken();

                // Use the ID token to create a Firebase credential and sign in
                firebaseAuthWithGoogle(idToken);
                return;
            }
        }

        // Credential type not recognized
        showLoading(false);
        Toast.makeText(this, R.string.error_google_sign_in, Toast.LENGTH_SHORT).show();
    }

    /**
     * Authenticates with Firebase using the Google ID token.
     * If this is the user's first sign-in, creates a /users/{uid} record.
     */
    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential authCredential = GoogleAuthProvider.getCredential(idToken, null);

        mAuth.signInWithCredential(authCredential)
                .addOnCompleteListener(this, task -> {
                    showLoading(false);
                    if (task.isSuccessful()) {
                        FirebaseUser firebaseUser = mAuth.getCurrentUser();
                        if (firebaseUser != null) {
                            // Check if this is a new user — create database record
                            boolean isNewUser = task.getResult()
                                    .getAdditionalUserInfo() != null
                                    && task.getResult().getAdditionalUserInfo().isNewUser();

                            if (isNewUser) {
                                createUserRecord(firebaseUser);
                            }
                        }
                        navigateToHome();
                    } else {
                        Log.e(TAG, "Firebase auth with Google failed", task.getException());
                        Toast.makeText(this, R.string.error_google_sign_in,
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * Creates a /users/{uid} node in Realtime Database for first-time Google users.
     */
    private void createUserRecord(FirebaseUser firebaseUser) {
        String displayName = firebaseUser.getDisplayName() != null
                ? firebaseUser.getDisplayName() : "User";
        String email = firebaseUser.getEmail() != null
                ? firebaseUser.getEmail() : "";

        User user = new User(firebaseUser.getUid(), displayName, email);

        // Set photo URL if available from Google profile
        if (firebaseUser.getPhotoUrl() != null) {
            user.photoUrl = firebaseUser.getPhotoUrl().toString();
        }

        FirebaseDatabase.getInstance().getReference("users")
                .child(firebaseUser.getUid())
                .setValue(user.toMap());
    }

    // =========================================================================
    // Password reset
    // =========================================================================

    private void resetPassword() {
        String email = binding.etEmail.getText().toString().trim();
        if (TextUtils.isEmpty(email)) {
            binding.tilEmail.setError(getString(R.string.error_empty_email));
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.setError(getString(R.string.error_invalid_email));
            return;
        }

        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this, R.string.msg_password_reset_sent,
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void navigateToHome() {
        startActivity(new Intent(this, HomeActivity.class));
        finish();
    }

    private void showLoading(boolean show) {
        binding.progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        binding.btnSignIn.setVisibility(show ? View.INVISIBLE : View.VISIBLE);
        binding.btnSignIn.setEnabled(!show);
        binding.btnGoogleSignIn.setEnabled(!show);
    }
}

