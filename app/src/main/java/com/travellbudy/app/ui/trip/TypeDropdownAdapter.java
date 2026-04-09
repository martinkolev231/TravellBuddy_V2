package com.travellbudy.app.ui.trip;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.travellbudy.app.R;

import java.util.List;

public class TypeDropdownAdapter extends RecyclerView.Adapter<TypeDropdownAdapter.ViewHolder> {

    private final List<String> types;
    private int selectedPosition = -1;
    private final OnTypeSelectedListener listener;

    public interface OnTypeSelectedListener {
        void onTypeSelected(String type, int position);
    }

    public TypeDropdownAdapter(List<String> types, int initialSelection, OnTypeSelectedListener listener) {
        this.types = types;
        this.selectedPosition = initialSelection;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_type_dropdown, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String type = types.get(position);
        boolean isSelected = position == selectedPosition;

        holder.tvTypeName.setText(type);

        if (isSelected) {
            holder.itemView.setBackgroundResource(R.drawable.bg_type_selected);
            holder.tvTypeName.setTextColor(Color.WHITE);
            holder.ivCheckmark.setVisibility(View.VISIBLE);
            holder.ivCheckmark.setColorFilter(Color.WHITE);
        } else {
            holder.itemView.setBackground(null);
            holder.tvTypeName.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.text_primary));
            holder.ivCheckmark.setVisibility(View.INVISIBLE);
        }

        holder.itemView.setOnClickListener(v -> {
            int adapterPosition = holder.getBindingAdapterPosition();
            if (adapterPosition != RecyclerView.NO_POSITION) {
                int oldPosition = selectedPosition;
                selectedPosition = adapterPosition;
                notifyItemChanged(oldPosition);
                notifyItemChanged(selectedPosition);
                if (listener != null) {
                    listener.onTypeSelected(types.get(adapterPosition), adapterPosition);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return types.size();
    }

    public void setSelectedPosition(int position) {
        int oldPosition = selectedPosition;
        selectedPosition = position;
        notifyItemChanged(oldPosition);
        notifyItemChanged(selectedPosition);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivCheckmark;
        TextView tvTypeName;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivCheckmark = itemView.findViewById(R.id.ivCheckmark);
            tvTypeName = itemView.findViewById(R.id.tvTypeName);
        }
    }
}


