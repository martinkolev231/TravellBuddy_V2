package com.travellbudy.app;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.database.FirebaseDatabase;
import com.travellbudy.app.databinding.ActivitySignUpBinding;
import com.travellbudy.app.models.User;

public class SignUpActivity extends AppCompatActivity {

    private ActivitySignUpBinding binding;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySignUpBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();

        // Back button
        binding.btnBack.setOnClickListener(v -> finish());

        binding.btnSignUp.setOnClickListener(v -> attemptSignUp());
        binding.btnGoToSignIn.setOnClickListener(v -> {
            startActivity(new Intent(this, SignInActivity.class));
            finish();
        });
    }

    private void attemptSignUp() {
        // Clear previous errors
        binding.tilName.setError(null);
        binding.tilEmail.setError(null);
        binding.tilPassword.setError(null);

        String name = binding.etName.getText().toString().trim();
        String email = binding.etEmail.getText().toString().trim();
        String password = binding.etPassword.getText().toString();

        // Validate
        if (TextUtils.isEmpty(name)) {
            binding.tilName.setError(getString(R.string.error_empty_name));
            binding.etName.requestFocus();
            return;
        }
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
        if (password.length() < 6) {
            binding.tilPassword.setError(getString(R.string.error_short_password));
            binding.etPassword.requestFocus();
            return;
        }

        showLoading(true);

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser firebaseUser = mAuth.getCurrentUser();
                        if (firebaseUser != null) {
                            // Set display name
                            UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                                    .setDisplayName(name)
                                    .build();
                            firebaseUser.updateProfile(profileUpdates);

                            // Create user record in database
                            User user = new User(firebaseUser.getUid(), name, email);
                            FirebaseDatabase.getInstance().getReference("users")
                                    .child(firebaseUser.getUid())
                                    .setValue(user.toMap());

                            showLoading(false);
                            Toast.makeText(this, R.string.success_account_created, Toast.LENGTH_LONG).show();
                            startActivity(new Intent(this, HomeActivity.class));
                            finish();
                        }
                    } else {
                        showLoading(false);
                        String errorMsg = task.getException() != null
                                ? task.getException().getMessage()
                                : getString(R.string.error_sign_up_failed);
                        Log.e("SignUpActivity", "Sign up failed", task.getException());
                        Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void showLoading(boolean show) {
        binding.progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        binding.btnSignUp.setVisibility(show ? View.INVISIBLE : View.VISIBLE);
        binding.btnSignUp.setEnabled(!show);
    }
}

