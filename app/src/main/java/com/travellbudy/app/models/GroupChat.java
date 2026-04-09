package com.travellbudy.app.models;

import com.google.firebase.database.IgnoreExtraProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Model for a group chat associated with a trip.
 * Stored at /chats/{tripId}/info
 */
@IgnoreExtraProperties
public class GroupChat {
    public String chatId;
    public String tripId;
    public String name;           // Trip title as chat name
    public String imageUrl;       // Trip cover image URL
    public String createdBy;      // Driver/host UID
    public long createdAt;
    public String lastMessage;
    public long lastMessageTime;
    public boolean isGroupChat;
    public Map<String, Boolean> participants;

    public GroupChat() {
        // Required empty constructor for Firebase
    }

    public GroupChat(String chatId, String tripId, String name, String imageUrl, String createdBy) {
        this.chatId = chatId;
        this.tripId = tripId;
        this.name = name;
        this.imageUrl = imageUrl != null ? imageUrl : "";
        this.createdBy = createdBy;
        this.createdAt = System.currentTimeMillis();
        this.lastMessage = "";
        this.lastMessageTime = System.currentTimeMillis();
        this.isGroupChat = true;
        this.participants = new HashMap<>();
        this.participants.put(createdBy, true);
    }

    public Map<String, Object> toMap() {
        HashMap<String, Object> result = new HashMap<>();
        result.put("chatId", chatId);
        result.put("tripId", tripId);
        result.put("name", name);
        result.put("imageUrl", imageUrl != null ? imageUrl : "");
        result.put("createdBy", createdBy);
        result.put("createdAt", createdAt);
        result.put("lastMessage", lastMessage);
        result.put("lastMessageTime", lastMessageTime);
        result.put("isGroupChat", isGroupChat);
        result.put("participants", participants != null ? participants : new HashMap<>());
        return result;
    }
}

