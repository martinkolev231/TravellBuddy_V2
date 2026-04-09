package com.travellbudy.app.models;

import com.google.firebase.database.IgnoreExtraProperties;

import java.util.HashMap;
import java.util.Map;

@IgnoreExtraProperties
public class ChatMessage {
    public String messageId;
    public String senderUid;
    public String senderName;
    public String text;
    public long timestamp;
    public Map<String, Boolean> readBy;

    public ChatMessage() {
        // Required empty constructor for Firebase
    }

    public ChatMessage(String messageId, String senderUid, String senderName, String text) {
        this.messageId = messageId;
        this.senderUid = senderUid;
        this.senderName = senderName;
        this.text = text;
        this.timestamp = System.currentTimeMillis();
        this.readBy = new HashMap<>();
        this.readBy.put(senderUid, true);
    }

    public Map<String, Object> toMap() {
        HashMap<String, Object> result = new HashMap<>();
        result.put("messageId", messageId);
        result.put("senderUid", senderUid);
        result.put("senderName", senderName);
        result.put("text", text);
        result.put("timestamp", timestamp);
        // Note: readBy is excluded from toMap() as it's not allowed by Firebase rules
        return result;
    }
}

