package com.travellbudy.app.admin;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.travellbudy.app.R;
import com.travellbudy.app.admin.adapter.AdminAnnouncementsAdapter;
import com.travellbudy.app.databinding.FragmentAdminNotificationsBinding;
import com.travellbudy.app.models.Announcement;
import com.travellbudy.app.viewmodel.AdminViewModel;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Admin Notifications Fragment - Manage announcements and push notifications.
 */
public class AdminNotificationsFragment extends Fragment implements AdminAnnouncementsAdapter.OnAnnouncementActionListener {

    private FragmentAdminNotificationsBinding binding;
    private AdminViewModel viewModel;
    private AdminAnnouncementsAdapter adapter;
    private List<Announcement> allAnnouncements = new ArrayList<>();
    
    private long selectedExpiryDate = 0;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy", new Locale("bg"));

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentAdminNotificationsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(AdminViewModel.class);

        setupRecyclerView();
        setupButtons();
        setupObservers();
    }

    private void setupRecyclerView() {
        adapter = new AdminAnnouncementsAdapter(this);
        binding.recyclerAnnouncements.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerAnnouncements.setAdapter(adapter);
    }

    private void setupButtons() {
        binding.btnCreateAnnouncement.setOnClickListener(v -> showCreateAnnouncementDialog());
        binding.btnSendPushNotification.setOnClickListener(v -> showSendPushNotificationDialog());
    }

    private void setupObservers() {
        viewModel.getAllAnnouncements().observe(getViewLifecycleOwner(), announcements -> {
            allAnnouncements = announcements != null ? announcements : new ArrayList<>();
            adapter.submitList(new ArrayList<>(allAnnouncements));
            binding.tvAnnouncementCount.setText(getString(R.string.admin_announcement_count, allAnnouncements.size()));
            binding.emptyView.setVisibility(allAnnouncements.isEmpty() ? View.VISIBLE : View.GONE);
        });

        viewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        });
    }

    private void showCreateAnnouncementDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_create_announcement, null);
        EditText etTitle = dialogView.findViewById(R.id.etAnnouncementTitle);
        EditText etMessage = dialogView.findViewById(R.id.etAnnouncementMessage);
        Spinner spinnerType = dialogView.findViewById(R.id.spinnerAnnouncementType);
        View btnSelectExpiry = dialogView.findViewById(R.id.btnSelectExpiry);
        EditText etExpiry = dialogView.findViewById(R.id.etExpiryDate);

        // Setup type spinner
        String[] types = {"Информация", "Предупреждение", "Събитие", "Актуализация"};
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(requireContext(), 
            android.R.layout.simple_spinner_dropdown_item, types);
        spinnerType.setAdapter(typeAdapter);

        // Setup expiry date picker
        selectedExpiryDate = 0;
        btnSelectExpiry.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            new DatePickerDialog(requireContext(), (view, year, month, day) -> {
                Calendar selected = Calendar.getInstance();
                selected.set(year, month, day, 23, 59, 59);
                selectedExpiryDate = selected.getTimeInMillis();
                etExpiry.setText(dateFormat.format(selected.getTime()));
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
        });

        new MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.admin_create_announcement)
            .setView(dialogView)
            .setPositiveButton(R.string.action_create, (dialog, which) -> {
                String title = etTitle.getText().toString().trim();
                String message = etMessage.getText().toString().trim();
                
                if (TextUtils.isEmpty(title) || TextUtils.isEmpty(message)) {
                    return;
                }

                String type = getAnnouncementType(spinnerType.getSelectedItemPosition());
                viewModel.createAnnouncement(title, message, type, selectedExpiryDate);
            })
            .setNegativeButton(R.string.action_cancel, null)
            .show();
    }

    private String getAnnouncementType(int position) {
        switch (position) {
            case 1: return Announcement.TYPE_WARNING;
            case 2: return Announcement.TYPE_EVENT;
            case 3: return Announcement.TYPE_UPDATE;
            default: return Announcement.TYPE_INFO;
        }
    }

    private void showSendPushNotificationDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_send_push_notification, null);
        EditText etTitle = dialogView.findViewById(R.id.etNotificationTitle);
        EditText etMessage = dialogView.findViewById(R.id.etNotificationMessage);

        new MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.admin_send_push_notification)
            .setView(dialogView)
            .setPositiveButton(R.string.action_send, (dialog, which) -> {
                String title = etTitle.getText().toString().trim();
                String message = etMessage.getText().toString().trim();
                
                if (TextUtils.isEmpty(title) || TextUtils.isEmpty(message)) {
                    return;
                }

                viewModel.sendPushNotificationToAll(title, message);
            })
            .setNegativeButton(R.string.action_cancel, null)
            .show();
    }

    @Override
    public void onAnnouncementClick(Announcement announcement) {
        showAnnouncementDetailsDialog(announcement);
    }

    @Override
    public void onAnnouncementMenuClick(Announcement announcement, View anchor) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        popup.inflate(R.menu.menu_admin_announcement_actions);

        popup.getMenu().findItem(R.id.action_deactivate).setVisible(announcement.isActive);

        popup.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.action_deactivate) {
                viewModel.deactivateAnnouncement(announcement.announcementId);
                return true;
            } else if (itemId == R.id.action_delete) {
                confirmDeleteAnnouncement(announcement);
                return true;
            }
            return false;
        });

        popup.show();
    }

    private void showAnnouncementDetailsDialog(Announcement announcement) {
        StringBuilder details = new StringBuilder();
        details.append("Заглавие: ").append(announcement.title).append("\n\n");
        details.append("Съобщение: ").append(announcement.message).append("\n\n");
        details.append("Тип: ").append(getTypeLabel(announcement.type)).append("\n");
        details.append("Статус: ").append(announcement.isActive ? "Активно" : "Неактивно").append("\n");
        
        if (announcement.expiresAt > 0) {
            details.append("Изтича на: ").append(dateFormat.format(announcement.expiresAt)).append("\n");
        }

        new AlertDialog.Builder(requireContext())
            .setTitle(R.string.admin_announcement_details)
            .setMessage(details.toString())
            .setPositiveButton(R.string.action_close, null)
            .show();
    }

    private String getTypeLabel(String type) {
        if (type == null) return "Информация";
        switch (type) {
            case Announcement.TYPE_WARNING: return "Предупреждение";
            case Announcement.TYPE_EVENT: return "Събитие";
            case Announcement.TYPE_UPDATE: return "Актуализация";
            default: return "Информация";
        }
    }

    private void confirmDeleteAnnouncement(Announcement announcement) {
        new AlertDialog.Builder(requireContext())
            .setTitle(R.string.admin_delete_announcement_title)
            .setMessage(getString(R.string.admin_delete_announcement_message, announcement.title))
            .setPositiveButton(R.string.action_delete, (d, w) -> viewModel.deleteAnnouncement(announcement.announcementId))
            .setNegativeButton(R.string.action_cancel, null)
            .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}

