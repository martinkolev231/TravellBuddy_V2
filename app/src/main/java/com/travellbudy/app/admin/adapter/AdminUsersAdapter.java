package com.travellbudy.app.admin.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.imageview.ShapeableImageView;
import com.travellbudy.app.R;
import com.travellbudy.app.models.User;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for displaying users in the admin panel with premium card design.
 */
public class AdminUsersAdapter extends RecyclerView.Adapter<AdminUsersAdapter.UserViewHolder> {

    private List<User> users = new ArrayList<>();
    private final OnUserActionListener listener;

    public interface OnUserActionListener {
        void onUserClick(User user);
        void onUserMenuClick(User user, View anchor);
    }

    public AdminUsersAdapter(OnUserActionListener listener) {
        this.listener = listener;
    }

    public void submitList(List<User> newUsers) {
        this.users = newUsers != null ? newUsers : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_admin_user, parent, false);
        return new UserViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        User user = users.get(position);
        holder.bind(user);
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    class UserViewHolder extends RecyclerView.ViewHolder {
        private final ShapeableImageView ivAvatar;
        private final TextView tvName;
        private final TextView tvEmail;
        private final TextView tvRole;
        private final TextView tvStatus;
        private final ImageButton btnMenu;

        UserViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.ivUserAvatar);
            tvName = itemView.findViewById(R.id.tvUserName);
            tvEmail = itemView.findViewById(R.id.tvUserEmail);
            tvRole = itemView.findViewById(R.id.tvUserRole);
            tvStatus = itemView.findViewById(R.id.tvUserStatus);
            btnMenu = itemView.findViewById(R.id.btnUserMenu);
        }

        void bind(User user) {
            // Name
            tvName.setText(user.displayName != null ? user.displayName : "Unknown");
            
            // Email
            tvEmail.setText(user.email != null ? user.email : "");

            // Role chip
            if (user.isAdmin()) {
                tvRole.setText(R.string.admin_role_admin);
                tvRole.setBackgroundResource(R.drawable.bg_chip_admin);
                tvRole.setTextColor(itemView.getContext().getColor(R.color.chip_admin_text));
            } else {
                tvRole.setText(R.string.admin_role_user);
                tvRole.setBackgroundResource(R.drawable.bg_chip_user);
                tvRole.setTextColor(itemView.getContext().getColor(R.color.chip_user_text));
            }

            // Status chip
            if (user.isBanned) {
                tvStatus.setText(R.string.admin_status_banned);
                tvStatus.setBackgroundResource(R.drawable.bg_chip_banned);
                tvStatus.setTextColor(itemView.getContext().getColor(R.color.chip_banned_text));
                tvStatus.setVisibility(View.VISIBLE);
            } else if (user.isVerified) {
                tvStatus.setText(R.string.admin_status_verified);
                tvStatus.setBackgroundResource(R.drawable.bg_chip_verified);
                tvStatus.setTextColor(itemView.getContext().getColor(R.color.chip_verified_text));
                tvStatus.setVisibility(View.VISIBLE);
            } else {
                tvStatus.setText(R.string.admin_status_active);
                tvStatus.setBackgroundResource(R.drawable.bg_chip_active);
                tvStatus.setTextColor(itemView.getContext().getColor(R.color.chip_active_text));
                tvStatus.setVisibility(View.VISIBLE);
            }

            // Avatar
            // Clear any previous Glide request because ViewHolders are recycled.
            // Without this, a late image load from an old row can overwrite the avatar on a different user.
            Glide.with(itemView.getContext()).clear(ivAvatar);
            // Use the same placeholder as the profile screen for consistent UI.
            ivAvatar.setImageResource(R.drawable.bg_profile_placeholder);

            if (user.photoUrl != null && !user.photoUrl.isEmpty()) {
                Glide.with(itemView.getContext())
                    .load(user.photoUrl)
                    .placeholder(R.drawable.bg_profile_placeholder)
                    .error(R.drawable.bg_profile_placeholder)
                    .fallback(R.drawable.bg_profile_placeholder)
                    .circleCrop()
                    .into(ivAvatar);
            }

            // Click listeners
            itemView.setOnClickListener(v -> listener.onUserClick(user));
            btnMenu.setOnClickListener(v -> listener.onUserMenuClick(user, btnMenu));
        }
    }
}
