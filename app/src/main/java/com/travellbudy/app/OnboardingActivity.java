package com.travellbudy.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayoutMediator;
import com.travellbudy.app.databinding.ActivityOnboardingBinding;

public class OnboardingActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "travellbuddy_prefs";
    private static final String KEY_ONBOARDING_DONE = "onboarding_done";

    private ActivityOnboardingBinding binding;

    private final String[] titles = new String[3];
    private final String[] descriptions = new String[3];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityOnboardingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        titles[0] = getString(R.string.onboarding_title_1);
        titles[1] = getString(R.string.onboarding_title_2);
        titles[2] = getString(R.string.onboarding_title_3);
        descriptions[0] = getString(R.string.onboarding_desc_1);
        descriptions[1] = getString(R.string.onboarding_desc_2);
        descriptions[2] = getString(R.string.onboarding_desc_3);

        binding.viewPager.setAdapter(new OnboardingAdapter());

        new TabLayoutMediator(binding.tabIndicator, binding.viewPager,
                (tab, position) -> {}).attach();

        binding.viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                if (position == titles.length - 1) {
                    binding.btnNext.setText(R.string.btn_get_started);
                } else {
                    binding.btnNext.setText(R.string.btn_next);
                }
            }
        });

        binding.btnNext.setOnClickListener(v -> {
            int current = binding.viewPager.getCurrentItem();
            if (current < titles.length - 1) {
                binding.viewPager.setCurrentItem(current + 1);
            } else {
                finishOnboarding();
            }
        });

        binding.btnSkip.setOnClickListener(v -> finishOnboarding());
    }

    private void finishOnboarding() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, true).apply();
        startActivity(new Intent(this, SignUpActivity.class));
        finish();
    }

    private class OnboardingAdapter extends RecyclerView.Adapter<OnboardingAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_onboarding_page, parent, false);
            return new VH(view);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            holder.tvTitle.setText(titles[position]);
            holder.tvDescription.setText(descriptions[position]);
        }

        @Override
        public int getItemCount() {
            return titles.length;
        }

        class VH extends RecyclerView.ViewHolder {
            TextView tvTitle, tvDescription;

            VH(@NonNull View itemView) {
                super(itemView);
                tvTitle = itemView.findViewById(R.id.tvTitle);
                tvDescription = itemView.findViewById(R.id.tvDescription);
            }
        }
    }
}

