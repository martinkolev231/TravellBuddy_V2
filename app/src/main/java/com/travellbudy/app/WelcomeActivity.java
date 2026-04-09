package com.travellbudy.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.travellbudy.app.databinding.ActivityWelcomeBinding;

public class WelcomeActivity extends AppCompatActivity {

    private ActivityWelcomeBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Make fullscreen with edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        );
        
        binding = ActivityWelcomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Handle window insets for proper padding
        setupWindowInsets();

        // Load background image (using a hiking/adventure image URL)
        loadBackgroundImage();

        // Set up click listeners
        binding.btnSignUp.setOnClickListener(v -> 
            startActivity(new Intent(this, SignUpActivity.class))
        );

        binding.btnLogIn.setOnClickListener(v -> 
            startActivity(new Intent(this, SignInActivity.class))
        );
    }

    private void setupWindowInsets() {
        View contentView = binding.getRoot().findViewById(
            binding.getRoot().getChildAt(2) != null ? 
            binding.getRoot().getChildAt(2).getId() : View.NO_ID
        );
        
        // Apply insets to the Log In button (bottom-most element)
        ViewCompat.setOnApplyWindowInsetsListener(binding.btnLogIn, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(
                view.getPaddingLeft(),
                view.getPaddingTop(),
                view.getPaddingRight(),
                view.getPaddingBottom()
            );
            // Set bottom margin to account for navigation bar
            if (view.getLayoutParams() instanceof androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) {
                androidx.constraintlayout.widget.ConstraintLayout.LayoutParams params = 
                    (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) view.getLayoutParams();
                params.bottomMargin = insets.bottom + 24;
                view.setLayoutParams(params);
            }
            return WindowInsetsCompat.CONSUMED;
        });
    }

    private void loadBackgroundImage() {
        // Load the local traveler background image
        Glide.with(this)
            .load(R.drawable.bg_welcome_traveler)
            .centerCrop()
            .into(binding.ivBackground);
    }
}



