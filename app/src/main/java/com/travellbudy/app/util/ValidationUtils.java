package com.travellbudy.app.util;

import android.text.TextUtils;
import android.util.Patterns;

import androidx.annotation.Nullable;

/**
 * Centralized input validation. Returns 0 if valid, or a string resource ID
 * for the error message if invalid. Keeps validation logic out of Activities/Fragments.
 *
 * <p>Usage:
 * <pre>
 *   int error = ValidationUtils.validateEmail(email);
 *   if (error != 0) { tilEmail.setError(getString(error)); return; }
 * </pre>
 */
public final class ValidationUtils {

    private ValidationUtils() {
        // Prevent instantiation
    }

    /**
     * Validates an email address.
     * @return 0 if valid, or error string resource ID.
     */
    public static int validateEmail(@Nullable String email) {
        if (TextUtils.isEmpty(email)) {
            return com.travellbudy.app.R.string.error_empty_email;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            return com.travellbudy.app.R.string.error_invalid_email;
        }
        return 0;
    }

    /**
     * Validates a password (min 6 characters).
     * @return 0 if valid, or error string resource ID.
     */
    public static int validatePassword(@Nullable String password) {
        if (TextUtils.isEmpty(password)) {
            return com.travellbudy.app.R.string.error_empty_password;
        }
        if (password.length() < Constants.MIN_PASSWORD_LENGTH) {
            return com.travellbudy.app.R.string.error_weak_password;
        }
        return 0;
    }

    /**
     * Validates a display name (non-empty, max 100 chars).
     * @return 0 if valid, or error string resource ID.
     */
    public static int validateDisplayName(@Nullable String name) {
        if (TextUtils.isEmpty(name)) {
            return com.travellbudy.app.R.string.error_empty_name;
        }
        if (name.length() > Constants.MAX_DISPLAY_NAME) {
            return com.travellbudy.app.R.string.error_name_too_long;
        }
        return 0;
    }

    /**
     * Validates a seat count (1–8).
     * @return 0 if valid, or error string resource ID.
     */
    public static int validateSeatCount(@Nullable String seatsStr) {
        if (TextUtils.isEmpty(seatsStr)) {
            return com.travellbudy.app.R.string.error_invalid_seats;
        }
        try {
            int seats = Integer.parseInt(seatsStr);
            if (seats < Constants.MIN_SEATS || seats > Constants.MAX_SEATS) {
                return com.travellbudy.app.R.string.error_invalid_seats;
            }
        } catch (NumberFormatException e) {
            return com.travellbudy.app.R.string.error_invalid_seats;
        }
        return 0;
    }

    /**
     * Validates a price (non-negative number).
     * @return 0 if valid, or error string resource ID.
     */
    public static int validatePrice(@Nullable String priceStr) {
        if (TextUtils.isEmpty(priceStr)) {
            return com.travellbudy.app.R.string.error_empty_price;
        }
        try {
            double price = Double.parseDouble(priceStr);
            if (price < 0) {
                return com.travellbudy.app.R.string.error_invalid_price;
            }
        } catch (NumberFormatException e) {
            return com.travellbudy.app.R.string.error_invalid_price;
        }
        return 0;
    }

    /**
     * Validates a non-empty required field.
     * @param value     The input value.
     * @param errorResId The error string resource to return if empty.
     * @return 0 if valid, or the provided error resource ID.
     */
    public static int validateRequired(@Nullable String value, int errorResId) {
        return TextUtils.isEmpty(value) ? errorResId : 0;
    }

    /**
     * Validates a chat message text (non-empty, max 1000 chars).
     * @return 0 if valid, or error string resource ID.
     */
    public static int validateMessageText(@Nullable String text) {
        if (TextUtils.isEmpty(text)) {
            return com.travellbudy.app.R.string.error_empty_message;
        }
        if (text.length() > Constants.MAX_MESSAGE_LENGTH) {
            return com.travellbudy.app.R.string.error_message_too_long;
        }
        return 0;
    }
}

