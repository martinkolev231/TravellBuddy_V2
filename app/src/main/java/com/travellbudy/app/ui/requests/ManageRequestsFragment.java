package com.travellbudy.app.ui.requests;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.travellbudy.app.ManageRequestsActivity;

/**
 * Navigation Component wrapper for ManageRequestsActivity.
 * Launches the existing Activity with the tripId argument.
 */
public class ManageRequestsFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        String tripId = null;
        if (getArguments() != null) {
            tripId = getArguments().getString("tripId");
        }
        Intent intent = new Intent(requireContext(), ManageRequestsActivity.class);
        if (tripId != null) {
            intent.putExtra(ManageRequestsActivity.EXTRA_TRIP_ID, tripId);
        }
        startActivity(intent);
        requireActivity().getSupportFragmentManager().popBackStack();
        return new View(requireContext());
    }
}

