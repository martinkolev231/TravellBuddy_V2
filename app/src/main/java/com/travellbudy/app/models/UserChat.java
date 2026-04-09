package com.travellbudy.app.models;

import com.google.firebase.database.IgnoreExtraProperties;

import java.util.HashMap;
import java.util.Map;

@IgnoreExtraProperties
public class UserChat {
    public String chatId;
    public String otherPartyUid;
    public String otherPartyName;
    public String lastMessage;
    public long lastMessageTime;
    public int unreadCount;
    public String tripId;

    public UserChat() {
        // Required empty constructor for Firebase
    }

    public UserChat(String chatId, String otherPartyUid, String otherPartyName, String tripId) {
        this.chatId = chatId;
        this.otherPartyUid = otherPartyUid;
        this.otherPartyName = otherPartyName;
        this.lastMessage = "";
        this.lastMessageTime = System.currentTimeMillis();
        this.unreadCount = 0;
        this.tripId = tripId;
    }

    public Map<String, Object> toMap() {
        HashMap<String, Object> result = new HashMap<>();
        result.put("chatId", chatId);
        result.put("otherPartyUid", otherPartyUid);
        result.put("otherPartyName", otherPartyName);
        result.put("lastMessage", lastMessage);
        result.put("lastMessageTime", lastMessageTime);
        result.put("unreadCount", unreadCount);
        result.put("tripId", tripId != null ? tripId : "");
        return result;
    }
}

