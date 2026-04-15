package com.travellbudy.app.models;

import com.google.firebase.database.IgnoreExtraProperties;

import java.util.HashMap;
import java.util.Map;

@IgnoreExtraProperties
public class User {
    public String uid;
    public String displayName;
    public String email;
    public String photoUrl;
    public String coverPhotoUrl;
    public String phoneNumber;
    public String bio;
    public String location;
    public boolean isVerified;
    public boolean isBanned;           // Admin system: whether user is banned
    public String role;                 // Admin system: "user" or "admin"
    public String fcmToken;
    public long createdAt;

    // Denormalized sub-objects
    public RatingSummary ratingSummary;
    public TripCounters tripCounters;

    // Constants for roles
    public static final String ROLE_USER = "user";
    public static final String ROLE_ADMIN = "admin";

    public User() {
        // Required empty constructor for Firebase
    }

    public User(String uid, String displayName, String email) {
        this.uid = uid;
        this.displayName = displayName;
        this.email = email;
        this.photoUrl = "";
        this.coverPhotoUrl = "";
        this.phoneNumber = "";
        this.bio = "";
        this.location = "";
        this.isVerified = false;
        this.isBanned = false;
        this.role = ROLE_USER;           // Default role is user
        this.fcmToken = "";
        this.createdAt = System.currentTimeMillis();
        this.ratingSummary = new RatingSummary();
        this.tripCounters = new TripCounters();
    }

    public boolean isAdmin() {
        return ROLE_ADMIN.equals(role);
    }

    public Map<String, Object> toMap() {
        HashMap<String, Object> result = new HashMap<>();
        result.put("uid", uid);
        result.put("displayName", displayName);
        result.put("email", email);
        result.put("photoUrl", photoUrl);
        result.put("coverPhotoUrl", coverPhotoUrl);
        result.put("phoneNumber", phoneNumber);
        result.put("bio", bio);
        result.put("location", location);
        result.put("isVerified", isVerified);
        result.put("isBanned", isBanned);
        result.put("role", role != null ? role : ROLE_USER);
        result.put("fcmToken", fcmToken);
        result.put("createdAt", createdAt);
        if (ratingSummary != null) {
            result.put("ratingSummary", ratingSummary.toMap());
        }
        if (tripCounters != null) {
            result.put("tripCounters", tripCounters.toMap());
        }
        return result;
    }

    // =========================================================================
    // Nested: /users/{uid}/ratingSummary
    // =========================================================================
    @IgnoreExtraProperties
    public static class RatingSummary {
        public double averageRating;
        public int totalRatings;

        public RatingSummary() {
            this.averageRating = 0.0;
            this.totalRatings = 0;
        }

        public Map<String, Object> toMap() {
            HashMap<String, Object> result = new HashMap<>();
            result.put("averageRating", averageRating);
            result.put("totalRatings", totalRatings);
            return result;
        }
    }

    // =========================================================================
    // Nested: /users/{uid}/tripCounters
    // =========================================================================
    @IgnoreExtraProperties
    public static class TripCounters {
        public int tripsAsDriver;
        public int tripsAsRider;

        public TripCounters() {
            this.tripsAsDriver = 0;
            this.tripsAsRider = 0;
        }

        public Map<String, Object> toMap() {
            HashMap<String, Object> result = new HashMap<>();
            result.put("tripsAsDriver", tripsAsDriver);
            result.put("tripsAsRider", tripsAsRider);
            return result;
        }
    }
}
