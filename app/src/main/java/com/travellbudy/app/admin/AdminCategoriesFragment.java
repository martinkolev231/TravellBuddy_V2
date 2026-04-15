package com.travellbudy.app.admin;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.travellbudy.app.R;
import com.travellbudy.app.admin.adapter.AdminCategoriesAdapter;
import com.travellbudy.app.databinding.FragmentAdminCategoriesBinding;
import com.travellbudy.app.models.Category;
import com.travellbudy.app.viewmodel.AdminViewModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Admin Categories Fragment - Manage adventure categories.
 */
public class AdminCategoriesFragment extends Fragment implements AdminCategoriesAdapter.OnCategoryActionListener {

    private FragmentAdminCategoriesBinding binding;
    private AdminViewModel viewModel;
    private AdminCategoriesAdapter adapter;
    private List<Category> allCategories = new ArrayList<>();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentAdminCategoriesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(AdminViewModel.class);

        setupRecyclerView();
        setupFab();
        setupObservers();
    }

    private void setupRecyclerView() {
        adapter = new AdminCategoriesAdapter(this);
        binding.recyclerCategories.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerCategories.setAdapter(adapter);
    }

    private void setupFab() {
        binding.fabAddCategory.setOnClickListener(v -> showAddCategoryDialog());
    }

    private void setupObservers() {
        viewModel.getAllCategories().observe(getViewLifecycleOwner(), categories -> {
            allCategories = categories != null ? categories : new ArrayList<>();
            adapter.submitList(new ArrayList<>(allCategories));
            binding.tvCategoryCount.setText(getString(R.string.admin_category_count, allCategories.size()));
            binding.emptyView.setVisibility(allCategories.isEmpty() ? View.VISIBLE : View.GONE);
        });

        viewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        });
    }

    private void showAddCategoryDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_category, null);
        EditText etName = dialogView.findViewById(R.id.etCategoryName);
        EditText etIcon = dialogView.findViewById(R.id.etCategoryIcon);
        EditText etDescription = dialogView.findViewById(R.id.etCategoryDescription);

        new MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.admin_add_category)
            .setView(dialogView)
            .setPositiveButton(R.string.action_create, (dialog, which) -> {
                String name = etName.getText().toString().trim();
                String icon = etIcon.getText().toString().trim();
                String description = etDescription.getText().toString().trim();

                if (TextUtils.isEmpty(name)) {
                    return;
                }

                Category category = new Category(null, name, icon.isEmpty() ? "🏔️" : icon);
                category.description = description;
                viewModel.createCategory(category);
            })
            .setNegativeButton(R.string.action_cancel, null)
            .show();
    }

    @Override
    public void onCategoryClick(Category category) {
        showEditCategoryDialog(category);
    }

    @Override
    public void onCategoryMenuClick(Category category, View anchor) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        popup.inflate(R.menu.menu_admin_category_actions);

        popup.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.action_edit) {
                showEditCategoryDialog(category);
                return true;
            } else if (itemId == R.id.action_delete) {
                confirmDeleteCategory(category);
                return true;
            }
            return false;
        });

        popup.show();
    }

    private void showEditCategoryDialog(Category category) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_category, null);
        EditText etName = dialogView.findViewById(R.id.etCategoryName);
        EditText etIcon = dialogView.findViewById(R.id.etCategoryIcon);
        EditText etDescription = dialogView.findViewById(R.id.etCategoryDescription);

        etName.setText(category.name);
        etIcon.setText(category.icon);
        etDescription.setText(category.description);

        new MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.admin_edit_category)
            .setView(dialogView)
            .setPositiveButton(R.string.action_save, (dialog, which) -> {
                String name = etName.getText().toString().trim();
                String icon = etIcon.getText().toString().trim();
                String description = etDescription.getText().toString().trim();

                if (TextUtils.isEmpty(name)) {
                    return;
                }

                category.name = name;
                category.icon = icon.isEmpty() ? "🏔️" : icon;
                category.description = description;
                viewModel.updateCategory(category);
            })
            .setNegativeButton(R.string.action_cancel, null)
            .show();
    }

    private void confirmDeleteCategory(Category category) {
        new AlertDialog.Builder(requireContext())
            .setTitle(R.string.admin_delete_category_title)
            .setMessage(getString(R.string.admin_delete_category_message, category.name))
            .setPositiveButton(R.string.action_delete, (d, w) -> viewModel.deleteCategory(category.categoryId))
            .setNegativeButton(R.string.action_cancel, null)
            .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}

