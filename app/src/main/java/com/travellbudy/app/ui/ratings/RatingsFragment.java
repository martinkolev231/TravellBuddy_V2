package com.travellbudy.app.ui.ratings;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.travellbudy.app.RatingsActivity;

/**
 * Navigation Component wrapper for RatingsActivity.
 */
public class RatingsFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        String userId = null;
        if (getArguments() != null) {
            userId = getArguments().getString("userId");
        }
        if (userId == null && FirebaseAuth.getInstance().getCurrentUser() != null) {
            userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        }
        Intent intent = new Intent(requireContext(), RatingsActivity.class);
        if (userId != null) {
            intent.putExtra(RatingsActivity.EXTRA_USER_ID, userId);
        }
        startActivity(intent);
        requireActivity().getSupportFragmentManager().popBackStack();
        return new View(requireContext());
    }
}

