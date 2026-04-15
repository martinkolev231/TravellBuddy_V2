package com.travellbudy.app.admin.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.travellbudy.app.R;
import com.travellbudy.app.models.Category;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for displaying categories in the admin panel.
 */
public class AdminCategoriesAdapter extends RecyclerView.Adapter<AdminCategoriesAdapter.CategoryViewHolder> {

    private List<Category> categories = new ArrayList<>();
    private final OnCategoryActionListener listener;

    public interface OnCategoryActionListener {
        void onCategoryClick(Category category);
        void onCategoryMenuClick(Category category, View anchor);
    }

    public AdminCategoriesAdapter(OnCategoryActionListener listener) {
        this.listener = listener;
    }

    public void submitList(List<Category> newCategories) {
        this.categories = newCategories != null ? newCategories : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_admin_category, parent, false);
        return new CategoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        Category category = categories.get(position);
        holder.bind(category);
    }

    @Override
    public int getItemCount() {
        return categories.size();
    }

    class CategoryViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvIcon;
        private final TextView tvName;
        private final TextView tvDescription;
        private final TextView tvStatus;
        private final ImageButton btnMenu;

        CategoryViewHolder(@NonNull View itemView) {
            super(itemView);
            tvIcon = itemView.findViewById(R.id.tvCategoryIcon);
            tvName = itemView.findViewById(R.id.tvCategoryName);
            tvDescription = itemView.findViewById(R.id.tvCategoryDescription);
            tvStatus = itemView.findViewById(R.id.tvCategoryStatus);
            btnMenu = itemView.findViewById(R.id.btnCategoryMenu);
        }

        void bind(Category category) {
            tvIcon.setText(category.icon != null ? category.icon : "🏔️");
            tvName.setText(category.name != null ? category.name : "");
            tvDescription.setText(category.description != null && !category.description.isEmpty()
                ? category.description : "Без описание");

            // Status
            if (category.isActive) {
                tvStatus.setText(R.string.admin_status_active);
                tvStatus.setTextColor(itemView.getContext().getColor(R.color.success));
            } else {
                tvStatus.setText(R.string.admin_status_inactive);
                tvStatus.setTextColor(itemView.getContext().getColor(R.color.error));
            }

            // Click listeners
            itemView.setOnClickListener(v -> listener.onCategoryClick(category));
            btnMenu.setOnClickListener(v -> listener.onCategoryMenuClick(category, btnMenu));
        }
    }
}

