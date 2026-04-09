package com.travellbudy.app.models;

import com.google.firebase.database.PropertyName;

/**
 * Model class representing a user notification.
 */
public class Notification {
    public String notificationId;
    public String userId;
    public String type; // "join_request", "request_approved", "request_denied", "new_message", "trip_update"
    public String title;
    public String message;
    public String tripId;
    public String tripName;
    public String fromUserId;
    public String fromUserName;
    public String fromUserPhoto;
    public long createdAt;
    public boolean isRead;
    public String status; // "pending", "approved", "denied" for join requests

    public Notification() {
        // Required for Firebase
    }

    public Notification(String userId, String type, String title, String message) {
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.message = message;
        this.createdAt = System.currentTimeMillis();
        this.isRead = false;
    }

    @PropertyName("isRead")
    public boolean isRead() {
        return isRead;
    }

    @PropertyName("isRead")
    public void setRead(boolean read) {
        isRead = read;
    }
}

