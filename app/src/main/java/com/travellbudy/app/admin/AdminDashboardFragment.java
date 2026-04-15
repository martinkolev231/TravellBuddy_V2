package com.travellbudy.app.admin;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.travellbudy.app.R;
import com.travellbudy.app.SettingsActivity;
import com.travellbudy.app.databinding.FragmentAdminDashboardBinding;
import com.travellbudy.app.models.User;
import com.travellbudy.app.repository.AdminRepository;
import com.travellbudy.app.viewmodel.AdminViewModel;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Admin Dashboard Fragment - Shows app statistics with premium design.
 */
public class AdminDashboardFragment extends Fragment {

    private FragmentAdminDashboardBinding binding;
    private AdminViewModel viewModel;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentAdminDashboardBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(AdminViewModel.class);

        loadAdminProfile();
        setupClickListeners();
        setupObservers();
    }

    private void loadAdminProfile() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;

        FirebaseDatabase.getInstance().getReference("users")
            .child(currentUser.getUid())
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (binding == null) return;
                    
                    User user = snapshot.getValue(User.class);
                    if (user != null) {
                        // Set admin name
                        binding.tvAdminName.setText(user.displayName != null ? user.displayName : "Admin");
                        
                        // Set role subtitle
                        binding.tvAdminRole.setText("Системен администратор");
                        
                        // Load avatar
                        if (user.photoUrl != null && !user.photoUrl.isEmpty()) {
                            Glide.with(requireContext())
                                .load(user.photoUrl)
                                .placeholder(R.drawable.ic_default_avatar)
                                .error(R.drawable.ic_default_avatar)
                                .circleCrop()
                                .into(binding.ivAdminAvatar);
                        }
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    // Handle error silently
                }
            });
    }

    private void setupClickListeners() {
        binding.btnSettings.setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), SettingsActivity.class));
        });
    }

    private void setupObservers() {
        viewModel.getDashboardStats().observe(getViewLifecycleOwner(), this::updateStats);
        
        viewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        });
    }

    private void updateStats(AdminRepository.DashboardStats stats) {
        if (stats == null || binding == null) return;
        
        NumberFormat numberFormat = NumberFormat.getNumberInstance(new Locale("bg", "BG"));
        
        binding.tvTotalUsers.setText(numberFormat.format(stats.totalUsers));
        binding.tvTotalTrips.setText(numberFormat.format(stats.totalTrips));
        binding.tvNewUsers.setText("+" + stats.newUsersLast7Days);
        binding.tvBannedUsers.setText(String.valueOf(stats.bannedUsers));
        binding.tvAdminCount.setText(String.valueOf(stats.adminCount));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}

