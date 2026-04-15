package com.travellbudy.app.models;

import com.google.firebase.database.IgnoreExtraProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents an app-wide announcement created by admin.
 * Stored in /announcements/{announcementId}
 */
@IgnoreExtraProperties
public class Announcement {
    public String announcementId;
    public String title;
    public String message;
    public String createdByUid;
    public String createdByName;
    public long createdAt;
    public long expiresAt;        // When the announcement should stop showing
    public boolean isActive;       // Whether the announcement is currently active
    public String type;            // "info", "warning", "event", "update"

    // Announcement types
    public static final String TYPE_INFO = "info";
    public static final String TYPE_WARNING = "warning";
    public static final String TYPE_EVENT = "event";
    public static final String TYPE_UPDATE = "update";

    public Announcement() {
        // Required empty constructor for Firebase
    }

    public Announcement(String announcementId, String title, String message,
                       String createdByUid, String createdByName) {
        this.announcementId = announcementId;
        this.title = title;
        this.message = message;
        this.createdByUid = createdByUid;
        this.createdByName = createdByName;
        this.createdAt = System.currentTimeMillis();
        this.expiresAt = 0; // 0 means no expiration
        this.isActive = true;
        this.type = TYPE_INFO;
    }

    public boolean isExpired() {
        if (expiresAt == 0) return false;
        return System.currentTimeMillis() > expiresAt;
    }

    public boolean shouldShow() {
        return isActive && !isExpired();
    }

    public Map<String, Object> toMap() {
        HashMap<String, Object> result = new HashMap<>();
        result.put("announcementId", announcementId);
        result.put("title", title);
        result.put("message", message);
        result.put("createdByUid", createdByUid);
        result.put("createdByName", createdByName);
        result.put("createdAt", createdAt);
        result.put("expiresAt", expiresAt);
        result.put("isActive", isActive);
        result.put("type", type != null ? type : TYPE_INFO);
        return result;
    }
}

