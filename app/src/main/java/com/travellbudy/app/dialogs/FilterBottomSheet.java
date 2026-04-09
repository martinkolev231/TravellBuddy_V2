package com.travellbudy.app.dialogs;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.travellbudy.app.R;

public class FilterBottomSheet extends BottomSheetDialogFragment {

    public interface FilterListener {
        void onFilterApplied(String origin, String destination, double minPrice, double maxPrice);
        void onFilterCleared();
    }

    private FilterListener listener;

    public void setFilterListener(FilterListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_filter, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextInputEditText etOrigin = view.findViewById(R.id.etOrigin);
        TextInputEditText etDestination = view.findViewById(R.id.etDestination);
        TextInputEditText etMinPrice = view.findViewById(R.id.etMinPrice);
        TextInputEditText etMaxPrice = view.findViewById(R.id.etMaxPrice);
        MaterialButton btnApply = view.findViewById(R.id.btnApply);
        MaterialButton btnClear = view.findViewById(R.id.btnClear);


        btnApply.setOnClickListener(v -> {
            String origin = etOrigin.getText() != null ? etOrigin.getText().toString().trim() : "";
            String destination = etDestination.getText() != null ? etDestination.getText().toString().trim() : "";

            double minPrice = 0;
            double maxPrice = Double.MAX_VALUE;
            try {
                String minStr = etMinPrice.getText() != null ? etMinPrice.getText().toString().trim() : "";
                if (!minStr.isEmpty()) minPrice = Double.parseDouble(minStr);
            } catch (NumberFormatException ignored) {
            }
            try {
                String maxStr = etMaxPrice.getText() != null ? etMaxPrice.getText().toString().trim() : "";
                if (!maxStr.isEmpty()) maxPrice = Double.parseDouble(maxStr);
            } catch (NumberFormatException ignored) {
            }

            if (listener != null) {
                listener.onFilterApplied(origin, destination, minPrice, maxPrice);
            }
            dismiss();
        });

        btnClear.setOnClickListener(v -> {
            etOrigin.setText("");
            etDestination.setText("");
            etMinPrice.setText("");
            etMaxPrice.setText("");
            if (listener != null) {
                listener.onFilterCleared();
            }
            dismiss();
        });
    }
}

