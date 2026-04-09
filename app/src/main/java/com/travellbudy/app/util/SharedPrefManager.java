package com.travellbudy.app.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

/**
 * Wrapper around SharedPreferences for app-wide flags.
 * Keeps SharedPreferences key management centralized.
 */
public final class SharedPrefManager {

    private final SharedPreferences prefs;

    public SharedPrefManager(@NonNull Context context) {
        this.prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isOnboardingDone() {
        return prefs.getBoolean(Constants.KEY_ONBOARDING_DONE, false);
    }

    public void setOnboardingDone(boolean done) {
        prefs.edit().putBoolean(Constants.KEY_ONBOARDING_DONE, done).apply();
    }

    public String getTheme() {
        return prefs.getString(Constants.KEY_THEME, "system");
    }

    public void setTheme(@NonNull String theme) {
        prefs.edit().putString(Constants.KEY_THEME, theme).apply();
    }
}

