package com.travellbudy.app.admin;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.travellbudy.app.R;
import com.travellbudy.app.admin.adapter.AdminUsersAdapter;
import com.travellbudy.app.databinding.FragmentAdminUsersBinding;
import com.travellbudy.app.models.User;
import com.travellbudy.app.viewmodel.AdminViewModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Admin Users Fragment - Manage all registered users with premium UI design.
 */
public class AdminUsersFragment extends Fragment implements AdminUsersAdapter.OnUserActionListener {

    private FragmentAdminUsersBinding binding;
    private AdminViewModel viewModel;
    private AdminUsersAdapter adapter;
    private List<User> allUsers = new ArrayList<>();
    private String currentFilter = "all";
    private String currentUserId;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentAdminUsersBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(AdminViewModel.class);
        
        // Get current user ID to exclude from the list
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        currentUserId = currentUser != null ? currentUser.getUid() : null;

        setupRecyclerView();
        setupSearch();
        setupFilterButton();
        setupObservers();
    }

    private void setupRecyclerView() {
        adapter = new AdminUsersAdapter(this);
        binding.recyclerUsers.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerUsers.setAdapter(adapter);
    }

    private void setupSearch() {
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterUsers(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void setupFilterButton() {
        binding.btnFilter.setOnClickListener(v -> showFilterDialog());
    }

    private void showFilterDialog() {
        String[] filterOptions = {
            getString(R.string.admin_filter_all),
            getString(R.string.admin_filter_users),
            getString(R.string.admin_filter_admins),
            getString(R.string.admin_filter_verified),
            getString(R.string.admin_filter_active),
            getString(R.string.admin_filter_banned)
        };

        String[] filterValues = {"all", "users", "admins", "verified", "active", "banned"};

        int currentIndex = 0;
        for (int i = 0; i < filterValues.length; i++) {
            if (filterValues[i].equals(currentFilter)) {
                currentIndex = i;
                break;
            }
        }

        new AlertDialog.Builder(requireContext())
            .setTitle(R.string.admin_filter_all)
            .setSingleChoiceItems(filterOptions, currentIndex, (dialog, which) -> {
                currentFilter = filterValues[which];
                applyFilter();
                dialog.dismiss();
            })
            .setNegativeButton(R.string.action_cancel, null)
            .show();
    }

    private void applyFilter() {
        List<User> filtered = new ArrayList<>();
        String searchText = binding.etSearch.getText().toString().toLowerCase();

        for (User user : allUsers) {
            // Skip current user - don't show own profile in the list
            if (currentUserId != null && currentUserId.equals(user.uid)) {
                continue;
            }
            
            boolean matchesFilter = false;

            switch (currentFilter) {
                case "all":
                    matchesFilter = true;
                    break;
                case "users":
                    matchesFilter = !user.isAdmin();
                    break;
                case "admins":
                    matchesFilter = user.isAdmin();
                    break;
                case "verified":
                    matchesFilter = user.isVerified;
                    break;
                case "active":
                    matchesFilter = !user.isBanned;
                    break;
                case "banned":
                    matchesFilter = user.isBanned;
                    break;
            }

            if (matchesFilter) {
                // Also apply search filter
                if (searchText.isEmpty()) {
                    filtered.add(user);
                } else {
                    boolean matchesSearch = 
                        (user.displayName != null && user.displayName.toLowerCase().contains(searchText)) ||
                        (user.email != null && user.email.toLowerCase().contains(searchText));
                    if (matchesSearch) {
                        filtered.add(user);
                    }
                }
            }
        }

        adapter.submitList(filtered);
        binding.emptyView.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        binding.recyclerUsers.setVisibility(filtered.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void filterUsers(String query) {
        applyFilter();
    }

    private void setupObservers() {
        viewModel.getAllUsers().observe(getViewLifecycleOwner(), users -> {
            allUsers = users != null ? users : new ArrayList<>();
            applyFilter();
        });

        viewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        });
    }

    @Override
    public void onUserClick(User user) {
        showUserDetailsDialog(user);
    }

    @Override
    public void onUserMenuClick(User user, View anchor) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        popup.inflate(R.menu.menu_admin_user_actions);

        // Update menu items based on user state
        popup.getMenu().findItem(R.id.action_ban).setVisible(!user.isBanned);
        popup.getMenu().findItem(R.id.action_unban).setVisible(user.isBanned);
        popup.getMenu().findItem(R.id.action_promote).setVisible(!user.isAdmin());
        popup.getMenu().findItem(R.id.action_demote).setVisible(user.isAdmin());

        popup.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.action_ban) {
                confirmBanUser(user);
                return true;
            } else if (itemId == R.id.action_unban) {
                viewModel.unbanUser(user.uid);
                return true;
            } else if (itemId == R.id.action_promote) {
                confirmPromoteUser(user);
                return true;
            } else if (itemId == R.id.action_demote) {
                confirmDemoteUser(user);
                return true;
            } else if (itemId == R.id.action_delete) {
                confirmDeleteUser(user);
                return true;
            }
            return false;
        });

        popup.show();
    }

    private void showUserDetailsDialog(User user) {
        StringBuilder details = new StringBuilder();
        details.append("Name: ").append(user.displayName != null ? user.displayName : "N/A").append("\n");
        details.append("Email: ").append(user.email != null ? user.email : "N/A").append("\n");
        details.append("Role: ").append(user.isAdmin() ? "Admin" : "User").append("\n");
        details.append("Status: ").append(user.isBanned ? "Banned" : (user.isVerified ? "Verified" : "Active")).append("\n");

        if (user.ratingSummary != null) {
            details.append("Rating: ").append(String.format("%.1f", user.ratingSummary.averageRating))
                   .append(" (").append(user.ratingSummary.totalRatings).append(" reviews)\n");
        }

        if (user.tripCounters != null) {
            details.append("Trips as driver: ").append(user.tripCounters.tripsAsDriver).append("\n");
            details.append("Trips as rider: ").append(user.tripCounters.tripsAsRider).append("\n");
        }

        new AlertDialog.Builder(requireContext())
            .setTitle(R.string.admin_user_details)
            .setMessage(details.toString())
            .setPositiveButton(R.string.action_close, null)
            .show();
    }

    private void confirmBanUser(User user) {
        new AlertDialog.Builder(requireContext())
            .setTitle(R.string.admin_ban_user_title)
            .setMessage(getString(R.string.admin_ban_user_message, user.displayName))
            .setPositiveButton(R.string.action_ban, (d, w) -> viewModel.banUser(user.uid))
            .setNegativeButton(R.string.action_cancel, null)
            .show();
    }

    private void confirmPromoteUser(User user) {
        new AlertDialog.Builder(requireContext())
            .setTitle(R.string.admin_promote_user_title)
            .setMessage(getString(R.string.admin_promote_user_message, user.displayName))
            .setPositiveButton(R.string.action_promote, (d, w) -> viewModel.promoteToAdmin(user.uid))
            .setNegativeButton(R.string.action_cancel, null)
            .show();
    }

    private void confirmDemoteUser(User user) {
        new AlertDialog.Builder(requireContext())
            .setTitle(R.string.admin_demote_user_title)
            .setMessage(getString(R.string.admin_demote_user_message, user.displayName))
            .setPositiveButton(R.string.action_demote, (d, w) -> viewModel.demoteToUser(user.uid))
            .setNegativeButton(R.string.action_cancel, null)
            .show();
    }

    private void confirmDeleteUser(User user) {
        new AlertDialog.Builder(requireContext())
            .setTitle(R.string.admin_delete_user_title)
            .setMessage(getString(R.string.admin_delete_user_message, user.displayName))
            .setPositiveButton(R.string.action_delete, (d, w) -> viewModel.deleteUser(user.uid))
            .setNegativeButton(R.string.action_cancel, null)
            .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
