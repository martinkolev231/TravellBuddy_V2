package com.travellbudy.app.util;

import androidx.annotation.NonNull;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Utility class for timestamp ↔ formatted-string conversions.
 * Uses {@code java.time} API (minSdk 26+).
 */
public final class DateTimeUtils {

    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM, HH:mm", Locale.getDefault());

    private static final DateTimeFormatter DATE_ONLY_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault());

    private static final DateTimeFormatter TIME_ONLY_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault());

    private static final DateTimeFormatter FULL_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.getDefault());

    private DateTimeUtils() {
        // Prevent instantiation
    }

    /**
     * Formats a millis timestamp as "dd MMM, HH:mm" (e.g., "15 Mar, 09:30").
     */
    @NonNull
    public static String formatDepartureTime(long millis) {
        LocalDateTime dt = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(millis), ZoneId.systemDefault());
        return dt.format(DATE_TIME_FORMAT);
    }

    /**
     * Formats a millis timestamp as "dd MMM yyyy" (e.g., "15 Mar 2026").
     */
    @NonNull
    public static String formatDateOnly(long millis) {
        LocalDateTime dt = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(millis), ZoneId.systemDefault());
        return dt.format(DATE_ONLY_FORMAT);
    }

    /**
     * Formats a millis timestamp as "HH:mm" (e.g., "09:30").
     */
    @NonNull
    public static String formatTimeOnly(long millis) {
        LocalDateTime dt = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(millis), ZoneId.systemDefault());
        return dt.format(TIME_ONLY_FORMAT);
    }

    /**
     * Formats a millis timestamp as "dd MMM yyyy, HH:mm" (e.g., "15 Mar 2026, 09:30").
     */
    @NonNull
    public static String formatFull(long millis) {
        LocalDateTime dt = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(millis), ZoneId.systemDefault());
        return dt.format(FULL_FORMAT);
    }

    /**
     * Returns a human-readable relative time string (e.g., "2 hours ago", "Just now").
     */
    @NonNull
    public static String formatRelativeTime(long millis) {
        long now = System.currentTimeMillis();
        long diff = now - millis;

        if (diff < 0) return "Just now";

        long seconds = diff / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (seconds < 60) return "Just now";
        if (minutes < 60) return minutes + (minutes == 1 ? " minute ago" : " minutes ago");
        if (hours < 24) return hours + (hours == 1 ? " hour ago" : " hours ago");
        if (days < 7) return days + (days == 1 ? " day ago" : " days ago");

        return formatDateOnly(millis);
    }

    /**
     * Returns true if the given timestamp is in the future.
     */
    public static boolean isInFuture(long millis) {
        return millis > System.currentTimeMillis();
    }

    /**
     * Returns true if the given timestamp is in the past.
     */
    public static boolean isInPast(long millis) {
        return millis < System.currentTimeMillis();
    }
}

