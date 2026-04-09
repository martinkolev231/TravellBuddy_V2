package com.travellbudy.app.ui.chat;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.travellbudy.app.ChatActivity;

/**
 * Navigation Component wrapper for ChatActivity.
 * Launches the existing Activity with tripId and tripRoute arguments.
 */
public class ChatFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        String tripId = null;
        String tripRoute = "";
        if (getArguments() != null) {
            tripId = getArguments().getString("tripId");
            tripRoute = getArguments().getString("tripRoute", "");
        }
        Intent intent = new Intent(requireContext(), ChatActivity.class);
        if (tripId != null) {
            intent.putExtra(ChatActivity.EXTRA_TRIP_ID, tripId);
        }
        intent.putExtra(ChatActivity.EXTRA_TRIP_ROUTE, tripRoute);
        startActivity(intent);
        requireActivity().getSupportFragmentManager().popBackStack();
        return new View(requireContext());
    }
}

