package com.travellbudy.app.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.travellbudy.app.EditProfileActivity;

/**
 * Navigation Component wrapper for EditProfileActivity.
 */
public class EditProfileFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        startActivity(new Intent(requireContext(), EditProfileActivity.class));
        requireActivity().getSupportFragmentManager().popBackStack();
        return new View(requireContext());
    }
}

