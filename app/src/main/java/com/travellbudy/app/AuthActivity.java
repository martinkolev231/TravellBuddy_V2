package com.travellbudy.app;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.travellbudy.app.databinding.ActivityAuthBinding;

/**
 * Auth gate host - redirects to WelcomeActivity.
 */
public class AuthActivity extends AppCompatActivity {

    private ActivityAuthBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAuthBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Redirect to welcome screen
        startActivity(new Intent(this, WelcomeActivity.class));
        finish();
    }
}

