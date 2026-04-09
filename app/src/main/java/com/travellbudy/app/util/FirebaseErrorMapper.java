package com.travellbudy.app.util;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.firebase.database.DatabaseError;
import com.travellbudy.app.R;

/**
 * Maps Firebase DatabaseError codes to user-friendly string resource IDs.
 * Keeps all error-message logic in one place so repositories return clean strings
 * and UI never deals with Firebase-specific codes.
 *
 * <p>Usage: {@code String msg = FirebaseErrorMapper.map(context, error);}
 */
public final class FirebaseErrorMapper {

    private FirebaseErrorMapper() {
        // Prevent instantiation
    }

    /**
     * Maps a {@link DatabaseError} to a user-friendly localized string.
     *
     * @param context Application or Activity context for string resolution.
     * @param error   The Firebase database error.
     * @return A user-readable error message.
     */
    @NonNull
    public static String map(@NonNull Context context, @NonNull DatabaseError error) {
        switch (error.getCode()) {
            case DatabaseError.PERMISSION_DENIED:
                return context.getString(R.string.error_permission_denied);

            case DatabaseError.DISCONNECTED:
            case DatabaseError.NETWORK_ERROR:
                return context.getString(R.string.error_network);

            case DatabaseError.EXPIRED_TOKEN:
            case DatabaseError.INVALID_TOKEN:
                return context.getString(R.string.error_session_expired);

            case DatabaseError.MAX_RETRIES:
                return context.getString(R.string.error_max_retries);

            case DatabaseError.OVERRIDDEN_BY_SET:
                return context.getString(R.string.error_data_conflict);

            case DatabaseError.WRITE_CANCELED:
                return context.getString(R.string.error_write_canceled);

            case DatabaseError.DATA_STALE:
            case DatabaseError.OPERATION_FAILED:
            case DatabaseError.UNKNOWN_ERROR:
            default:
                return context.getString(R.string.error_generic);
        }
    }

    /**
     * Maps a {@link DatabaseError} code to a string resource ID (without context).
     * Useful when the caller wants to pass the resource ID to LiveData.
     */
    public static int mapToResId(@NonNull DatabaseError error) {
        switch (error.getCode()) {
            case DatabaseError.PERMISSION_DENIED:
                return R.string.error_permission_denied;
            case DatabaseError.DISCONNECTED:
            case DatabaseError.NETWORK_ERROR:
                return R.string.error_network;
            case DatabaseError.EXPIRED_TOKEN:
            case DatabaseError.INVALID_TOKEN:
                return R.string.error_session_expired;
            case DatabaseError.MAX_RETRIES:
                return R.string.error_max_retries;
            case DatabaseError.OVERRIDDEN_BY_SET:
                return R.string.error_data_conflict;
            case DatabaseError.WRITE_CANCELED:
                return R.string.error_write_canceled;
            default:
                return R.string.error_generic;
        }
    }

    /**
     * Maps a FirebaseAuth exception message to a friendlier string.
     * FirebaseAuth exceptions don't use error codes — they use exception types.
     */
    @NonNull
    public static String mapAuthException(@NonNull Context context, @NonNull Exception exception) {
        String msg = exception.getMessage();
        if (msg == null) return context.getString(R.string.error_generic);

        if (msg.contains("INVALID_LOGIN_CREDENTIALS") || msg.contains("INVALID_EMAIL")) {
            return context.getString(R.string.error_invalid_credentials);
        } else if (msg.contains("USER_NOT_FOUND")) {
            return context.getString(R.string.error_user_not_found);
        } else if (msg.contains("WRONG_PASSWORD")) {
            return context.getString(R.string.error_wrong_password);
        } else if (msg.contains("EMAIL_ALREADY_IN_USE")) {
            return context.getString(R.string.error_email_in_use);
        } else if (msg.contains("WEAK_PASSWORD")) {
            return context.getString(R.string.error_weak_password);
        } else if (msg.contains("TOO_MANY_REQUESTS")) {
            return context.getString(R.string.error_too_many_requests);
        } else if (msg.contains("NETWORK")) {
            return context.getString(R.string.error_network);
        }
        return context.getString(R.string.error_generic);
    }
}

