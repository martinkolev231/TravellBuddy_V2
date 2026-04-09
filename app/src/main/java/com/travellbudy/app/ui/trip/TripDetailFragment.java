package com.travellbudy.app.ui.trip;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.travellbudy.app.TripDetailsActivity;

/**
 * Navigation Component wrapper for TripDetailsActivity.
 * Launches the existing Activity with the tripId argument.
 */
public class TripDetailFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // Delegate to the existing Activity
        String tripId = null;
        if (getArguments() != null) {
            tripId = getArguments().getString("tripId");
        }
        Intent intent = new Intent(requireContext(), TripDetailsActivity.class);
        if (tripId != null) {
            intent.putExtra(TripDetailsActivity.EXTRA_TRIP_ID, tripId);
        }
        startActivity(intent);

        // Pop this fragment immediately so back-press returns to previous screen
        requireActivity().getSupportFragmentManager().popBackStack();

        // Return an empty view (fragment will be removed immediately)
        return new View(requireContext());
    }
}

