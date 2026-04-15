package com.travellbudy.app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.snackbar.Snackbar;
import com.travellbudy.app.admin.AdminDashboardFragment;
import com.travellbudy.app.admin.AdminPostsFragment;
import com.travellbudy.app.admin.AdminUsersFragment;
import com.travellbudy.app.databinding.ActivityAdminPanelBinding;
import com.travellbudy.app.viewmodel.AdminViewModel;

/**
 * Admin Panel Activity - Only accessible to users with admin role.
 * Provides user management, content moderation, and dashboard statistics.
 */
public class AdminPanelActivity extends AppCompatActivity {

    private ActivityAdminPanelBinding binding;
    private AdminViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAdminPanelBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(AdminViewModel.class);

        // Verify admin access
        verifyAdminAccess();

        setupBottomNavigation();
        setupObservers();

        // Load default fragment
        if (savedInstanceState == null) {
            loadFragment(new AdminDashboardFragment());
            binding.bottomNavigation.setSelectedItemId(R.id.nav_admin_dashboard);
        }
    }

    private void verifyAdminAccess() {
        viewModel.isCurrentUserAdmin().observe(this, isAdmin -> {
            if (isAdmin == null || !isAdmin) {
                Toast.makeText(this, R.string.admin_access_denied, Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            Fragment fragment = null;
            int itemId = item.getItemId();

            if (itemId == R.id.nav_admin_dashboard) {
                fragment = new AdminDashboardFragment();
            } else if (itemId == R.id.nav_admin_users) {
                fragment = new AdminUsersFragment();
            } else if (itemId == R.id.nav_admin_posts) {
                fragment = new AdminPostsFragment();
            }

            if (fragment != null) {
                loadFragment(fragment);
                return true;
            }
            return false;
        });
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager()
            .beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit();
    }

    private void setupObservers() {
        viewModel.getErrorMessage().observe(this, message -> {
            if (message != null && !message.isEmpty()) {
                Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_SHORT).show();
                viewModel.clearMessages();
            }
        });

        viewModel.getSuccessMessage().observe(this, message -> {
            if (message != null && !message.isEmpty()) {
                Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_SHORT).show();
                viewModel.clearMessages();
            }
        });
    }

    @Override
    public void onBackPressed() {
        // Go to home when back pressed instead of exiting
        startActivity(new Intent(this, HomeActivity.class));
        finish();
    }
}

