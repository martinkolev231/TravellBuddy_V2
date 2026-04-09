package com.travellbudy.app.repository;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.travellbudy.app.firebase.FirebaseManager;
import com.travellbudy.app.model.Result;
import com.travellbudy.app.models.ChatMessage;
import com.travellbudy.app.util.Constants;
import com.travellbudy.app.util.FirebaseErrorMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Repository for chat message operations.
 * Reads/writes to /chats/{chatId}/messages using ChildEventListener for realtime.
 * Fan-out writes to /userChats/{uid} for chat list previews.
 */
public class ChatRepository {

    private final FirebaseManager firebase;
    private final Application application;
    private ChildEventListener messagesListener;
    private DatabaseReference messagesRef;

    public ChatRepository(@NonNull Application application) {
        this.application = application;
        this.firebase = FirebaseManager.getInstance();
    }

    /**
     * Observes messages in a chat in realtime using ChildEventListener.
     * New messages are appended to the list as they arrive.
     */
    public LiveData<Result<List<ChatMessage>>> observeMessages(@NonNull String chatId) {
        MutableLiveData<Result<List<ChatMessage>>> result = new MutableLiveData<>(Result.loading());
        List<ChatMessage> messages = new ArrayList<>();

        messagesRef = firebase.getMessagesRef(chatId);
        messagesListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, String previousChildName) {
                ChatMessage message = snapshot.getValue(ChatMessage.class);
                if (message != null) {
                    messages.add(message);
                    result.setValue(Result.success(new ArrayList<>(messages)));
                }
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, String previousChildName) {
                // Messages are write-once in our schema; no-op
            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {
                // Messages cannot be deleted per security rules; no-op
            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, String previousChildName) {
                // No-op
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                result.setValue(Result.error(
                        FirebaseErrorMapper.map(application, error)));
            }
        };
        messagesRef.addChildEventListener(messagesListener);

        return result;
    }

    /**
     * Sends a message to a chat.
     * Also updates /userChats for both participants (fan-out).
     */
    public LiveData<Result<Void>> sendMessage(@NonNull String chatId,
                                                @NonNull ChatMessage message,
                                                @NonNull String senderUid,
                                                @NonNull String recipientUid,
                                                @NonNull String senderName,
                                                @NonNull String recipientName,
                                                String tripId) {
        MutableLiveData<Result<Void>> result = new MutableLiveData<>(Result.loading());

        DatabaseReference msgRef = firebase.getMessagesRef(chatId);
        String messageId = msgRef.push().getKey();
        if (messageId == null) {
            result.setValue(Result.error("Failed to create message"));
            return result;
        }

        message.messageId = messageId;

        // Fan-out: write message + update both users' chat previews atomically
        Map<String, Object> updates = new HashMap<>();

        // Write the message
        String msgPath = Constants.PATH_CHATS + "/" + chatId + "/" +
                Constants.PATH_MESSAGES + "/" + messageId;
        updates.put(msgPath, message.toMap());

        // Update chat metadata
        String chatMetaPath = Constants.PATH_CHATS + "/" + chatId + "/";
        updates.put(chatMetaPath + Constants.FIELD_LAST_MESSAGE, message.text);
        updates.put(chatMetaPath + Constants.FIELD_LAST_MSG_TIME, message.timestamp);

        // Ensure participants are set
        updates.put(chatMetaPath + Constants.FIELD_PARTICIPANTS + "/" + senderUid, true);
        updates.put(chatMetaPath + Constants.FIELD_PARTICIPANTS + "/" + recipientUid, true);

        // Update sender's userChats
        String senderChatPath = Constants.PATH_USER_CHATS + "/" + senderUid + "/" + chatId + "/";
        updates.put(senderChatPath + "chatId", chatId);
        updates.put(senderChatPath + "otherPartyUid", recipientUid);
        updates.put(senderChatPath + "otherPartyName", recipientName);
        updates.put(senderChatPath + Constants.FIELD_LAST_MESSAGE, message.text);
        updates.put(senderChatPath + Constants.FIELD_LAST_MSG_TIME, message.timestamp);
        if (tripId != null) updates.put(senderChatPath + "tripId", tripId);

        // Update recipient's userChats
        String recipientChatPath = Constants.PATH_USER_CHATS + "/" + recipientUid + "/" + chatId + "/";
        updates.put(recipientChatPath + "chatId", chatId);
        updates.put(recipientChatPath + "otherPartyUid", senderUid);
        updates.put(recipientChatPath + "otherPartyName", senderName);
        updates.put(recipientChatPath + Constants.FIELD_LAST_MESSAGE, message.text);
        updates.put(recipientChatPath + Constants.FIELD_LAST_MSG_TIME, message.timestamp);
        if (tripId != null) updates.put(recipientChatPath + "tripId", tripId);

        // Execute atomic multi-path update
        firebase.getDatabase().getReference().updateChildren(updates)
                .addOnSuccessListener(aVoid -> result.setValue(Result.success(null)))
                .addOnFailureListener(e -> result.setValue(Result.error(e.getMessage())));

        return result;
    }

    /**
     * Generates a deterministic chat ID from two UIDs.
     * Sorts alphabetically so both users get the same ID.
     */
    @NonNull
    public static String generateChatId(@NonNull String uid1, @NonNull String uid2) {
        return uid1.compareTo(uid2) < 0
                ? uid1 + "_" + uid2
                : uid2 + "_" + uid1;
    }

    /**
     * Removes the messages listener. Call in ViewModel.onCleared().
     */
    public void removeListeners() {
        if (messagesRef != null && messagesListener != null) {
            messagesRef.removeEventListener(messagesListener);
        }
    }
}

