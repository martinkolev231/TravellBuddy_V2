package com.travellbudy.app.admin.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.travellbudy.app.R;
import com.travellbudy.app.models.Announcement;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Adapter for displaying announcements in the admin panel.
 */
public class AdminAnnouncementsAdapter extends RecyclerView.Adapter<AdminAnnouncementsAdapter.AnnouncementViewHolder> {

    private List<Announcement> announcements = new ArrayList<>();
    private final OnAnnouncementActionListener listener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy", new Locale("bg"));

    public interface OnAnnouncementActionListener {
        void onAnnouncementClick(Announcement announcement);
        void onAnnouncementMenuClick(Announcement announcement, View anchor);
    }

    public AdminAnnouncementsAdapter(OnAnnouncementActionListener listener) {
        this.listener = listener;
    }

    public void submitList(List<Announcement> newAnnouncements) {
        this.announcements = newAnnouncements != null ? newAnnouncements : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public AnnouncementViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_admin_announcement, parent, false);
        return new AnnouncementViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AnnouncementViewHolder holder, int position) {
        Announcement announcement = announcements.get(position);
        holder.bind(announcement);
    }

    @Override
    public int getItemCount() {
        return announcements.size();
    }

    class AnnouncementViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvTitle;
        private final TextView tvMessage;
        private final TextView tvType;
        private final TextView tvStatus;
        private final TextView tvDate;
        private final TextView tvExpiry;
        private final ImageButton btnMenu;

        AnnouncementViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvAnnouncementTitle);
            tvMessage = itemView.findViewById(R.id.tvAnnouncementMessage);
            tvType = itemView.findViewById(R.id.tvAnnouncementType);
            tvStatus = itemView.findViewById(R.id.tvAnnouncementStatus);
            tvDate = itemView.findViewById(R.id.tvAnnouncementDate);
            tvExpiry = itemView.findViewById(R.id.tvAnnouncementExpiry);
            btnMenu = itemView.findViewById(R.id.btnAnnouncementMenu);
        }

        void bind(Announcement announcement) {
            tvTitle.setText(announcement.title != null ? announcement.title : "");
            tvMessage.setText(announcement.message != null ? announcement.message : "");

            // Type
            tvType.setText(getTypeLabel(announcement.type));
            tvType.setBackgroundResource(getTypeBackground(announcement.type));

            // Status
            if (announcement.isActive && !announcement.isExpired()) {
                tvStatus.setText(R.string.admin_status_active);
                tvStatus.setTextColor(itemView.getContext().getColor(R.color.success));
            } else if (announcement.isExpired()) {
                tvStatus.setText(R.string.admin_status_expired);
                tvStatus.setTextColor(itemView.getContext().getColor(R.color.warning));
            } else {
                tvStatus.setText(R.string.admin_status_inactive);
                tvStatus.setTextColor(itemView.getContext().getColor(R.color.error));
            }

            // Created date
            if (announcement.createdAt > 0) {
                tvDate.setText(dateFormat.format(new Date(announcement.createdAt)));
            }

            // Expiry date
            if (announcement.expiresAt > 0) {
                tvExpiry.setVisibility(View.VISIBLE);
                tvExpiry.setText(itemView.getContext().getString(R.string.admin_expires_on,
                    dateFormat.format(new Date(announcement.expiresAt))));
            } else {
                tvExpiry.setVisibility(View.GONE);
            }

            // Click listeners
            itemView.setOnClickListener(v -> listener.onAnnouncementClick(announcement));
            btnMenu.setOnClickListener(v -> listener.onAnnouncementMenuClick(announcement, btnMenu));
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

        private int getTypeBackground(String type) {
            if (type == null) return R.drawable.bg_badge_info;
            switch (type) {
                case Announcement.TYPE_WARNING: return R.drawable.bg_badge_warning;
                case Announcement.TYPE_EVENT: return R.drawable.bg_badge_success;
                case Announcement.TYPE_UPDATE: return R.drawable.bg_badge_info;
                default: return R.drawable.bg_badge_info;
            }
        }
    }
}

