package com.travellbudy.app;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.travellbudy.app.databinding.ActivityChatBinding;
import com.travellbudy.app.fragments.ChatListFragment;
import com.travellbudy.app.models.ChatMessage;

import java.util.HashMap;
import java.util.Map;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ChatActivity extends AppCompatActivity {

    public static final String EXTRA_TRIP_ID = "trip_id";
    public static final String EXTRA_TRIP_ROUTE = "trip_route";

    private ActivityChatBinding binding;
    private DatabaseReference chatRef;
    private ChatAdapter adapter;
    private final List<ChatMessage> messages = new ArrayList<>();
    private String currentUserId;
    private String currentUserName;
    private String tripId;
    private ChildEventListener chatListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        tripId = getIntent().getStringExtra(EXTRA_TRIP_ID);
        String tripRoute = getIntent().getStringExtra(EXTRA_TRIP_ROUTE);
        if (tripId == null) {
            finish();
            return;
        }

        if (tripRoute != null) {
            binding.toolbar.setTitle(tripRoute);
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            finish();
            return;
        }
        currentUserId = user.getUid();
        currentUserName = user.getDisplayName() != null ? user.getDisplayName() : "User";

        chatRef = FirebaseDatabase.getInstance().getReference("chats").child(tripId).child("messages");

        adapter = new ChatAdapter();
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        binding.rvMessages.setLayoutManager(layoutManager);
        binding.rvMessages.setAdapter(adapter);

        // TextWatcher to enable/disable send button
        binding.etMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                binding.btnSend.setEnabled(s != null && s.toString().trim().length() > 0);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        binding.btnSend.setOnClickListener(v -> sendMessage());


        // Mark the chat as read when opened
        ChatListFragment.markChatAsRead(this, tripId);
        
        // For direct messages, ensure chat participants are set up before listening
        if (tripId.startsWith("dm_")) {
            initializeDirectMessageChatAndListen();
        } else {
            listenForMessages();
        }
    }
    
    /**
     * Initialize a direct message chat with participants in /chats node, 
     * then create userChats entry and start listening for messages.
     * This ensures Firebase read rules are satisfied before attaching listener.
     */
    private void initializeDirectMessageChatAndListen() {
        String[] parts = tripId.split("_");
        if (parts.length < 3) {
            listenForMessages();
            return;
        }
        
        String otherUserId = parts[1].equals(currentUserId) ? parts[2] : parts[1];
        DatabaseReference chatRootRef = FirebaseDatabase.getInstance().getReference("chats").child(tripId);
        DatabaseReference userChatsRef = FirebaseDatabase.getInstance().getReference("userChats");
        
        // For DMs, always ensure participants exist first before trying to listen
        // This is necessary because read rules require being a participant
        ensureDmParticipantsExist(chatRootRef, otherUserId, () -> {
            // Now we can listen for messages
            listenForMessages();
            
            // Also create userChats entries
            createUserChatEntry(userChatsRef, currentUserId, otherUserId);
            createUserChatEntryForOther(userChatsRef, otherUserId, currentUserId);
        });
    }
    
    /**
     * Ensures participants exist in the /chats/{chatId}/participants node.
     * Uses setValue on participants directly which is allowed for non-existent chats.
     */
    private void ensureDmParticipantsExist(DatabaseReference chatRootRef, String otherUserId, Runnable onComplete) {
        // Use updateChildren to add participants - this works even if chat doesn't exist
        // because the write rule allows creation (!data.exists())
        Map<String, Object> updates = new HashMap<>();
        updates.put("participants/" + currentUserId, true);
        updates.put("participants/" + otherUserId, true);
        
        chatRootRef.updateChildren(updates)
            .addOnSuccessListener(aVoid -> {
                android.util.Log.d("ChatActivity", "DM participants ensured successfully");
                onComplete.run();
            })
            .addOnFailureListener(e -> {
                android.util.Log.e("ChatActivity", "Failed to ensure DM participants: " + e.getMessage());
                // Still try to listen in case we already have access
                onComplete.run();
            });
    }
    
    private void createUserChatEntry(DatabaseReference userChatsRef, String myUid, String otherUid) {
        userChatsRef.child(myUid).child(tripId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("chatId", tripId);
                    entry.put("otherPartyUid", otherUid);
                    entry.put("lastMessageTime", System.currentTimeMillis());
                    userChatsRef.child(myUid).child(tripId).setValue(entry);
                    
                    // Get other user's name
                    FirebaseDatabase.getInstance().getReference("users").child(otherUid)
                            .child("displayName")
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot nameSnapshot) {
                                    String otherUserName = nameSnapshot.getValue(String.class);
                                    if (otherUserName != null) {
                                        userChatsRef.child(myUid).child(tripId)
                                                .child("otherPartyName").setValue(otherUserName);
                                    }
                                }
                                @Override
                                public void onCancelled(@NonNull DatabaseError error) {}
                            });
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }
    
    private void createUserChatEntryForOther(DatabaseReference userChatsRef, String otherUid, String myUid) {
        userChatsRef.child(otherUid).child(tripId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("chatId", tripId);
                    entry.put("otherPartyUid", myUid);
                    entry.put("otherPartyName", currentUserName);
                    entry.put("lastMessageTime", System.currentTimeMillis());
                    userChatsRef.child(otherUid).child(tripId).setValue(entry);
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void listenForMessages() {
        chatListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, String previousChildName) {
                ChatMessage message = snapshot.getValue(ChatMessage.class);
                if (message != null) {
                    messages.add(message);
                    adapter.notifyItemInserted(messages.size() - 1);
                    binding.rvMessages.scrollToPosition(messages.size() - 1);
                    binding.emptyState.setVisibility(View.GONE);
                }
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, String previousChildName) {
            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {
            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, String previousChildName) {
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                android.util.Log.e("ChatActivity", "Chat listener cancelled: " + error.getMessage());
            }
        };
        chatRef.orderByChild("timestamp").addChildEventListener(chatListener);
    }

    private void sendMessage() {
        String text = binding.etMessage.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return;

        String messageId = chatRef.push().getKey();
        if (messageId == null) return;

        ChatMessage message = new ChatMessage(messageId, currentUserId, currentUserName, text);
        binding.etMessage.setText("");
        
        // For DMs, participants are already set up when chat was opened
        // Just send the message directly
        if (tripId.startsWith("dm_")) {
            android.util.Log.d("ChatActivity", "Sending DM message: " + messageId);
            chatRef.child(messageId).setValue(message.toMap())
                .addOnSuccessListener(aVoid -> {
                    android.util.Log.d("ChatActivity", "DM message sent successfully");
                    updateUserChatsWithLatestMessage(text, message.timestamp);
                    ChatListFragment.markChatAsRead(ChatActivity.this, tripId);
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("ChatActivity", "Failed to send DM message: " + e.getMessage());
                    android.widget.Toast.makeText(ChatActivity.this, "Failed to send message: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
                });
            return;
        }
        
        // For group chats, check if chat exists first
        DatabaseReference chatRootRef = FirebaseDatabase.getInstance().getReference("chats").child(tripId);
        chatRootRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    // Chat doesn't exist yet - need to create with participants
                    setupChatAndSendMessage(chatRootRef, messageId, message, text);
                } else {
                    // Chat exists, just send the message
                    chatRef.child(messageId).setValue(message.toMap())
                        .addOnSuccessListener(aVoid -> {
                            updateUserChatsWithLatestMessage(text, message.timestamp);
                            ChatListFragment.markChatAsRead(ChatActivity.this, tripId);
                        })
                        .addOnFailureListener(e -> {
                            android.util.Log.e("ChatActivity", "Failed to send message: " + e.getMessage());
                            android.widget.Toast.makeText(ChatActivity.this, "Failed to send message", android.widget.Toast.LENGTH_SHORT).show();
                        });
                }
            }
            
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }
    
    private void setupChatAndSendMessage(DatabaseReference chatRootRef, String messageId, ChatMessage message, String text) {
        // Group chat (trip-based) - get all participants from trip
        FirebaseDatabase.getInstance().getReference("trips").child(tripId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot tripSnapshot) {
                        if (!tripSnapshot.exists()) return;
                        
                        String driverUid = tripSnapshot.child("driverUid").getValue(String.class);
                        String tripRoute = tripSnapshot.child("originCity").getValue(String.class) + " → " + 
                                         tripSnapshot.child("destinationCity").getValue(String.class);
                        
                        // Build info node for group chat
                        Map<String, Object> chatData = new HashMap<>();
                        Map<String, Object> infoData = new HashMap<>();
                        Map<String, Object> participants = new HashMap<>();
                        
                        // Add driver as participant
                        if (driverUid != null) {
                            participants.put(driverUid, true);
                        }
                        
                        // Add current user as participant
                        participants.put(currentUserId, true);
                        
                        infoData.put("chatId", tripId);
                        infoData.put("tripId", tripId);
                        infoData.put("name", tripRoute);
                        infoData.put("createdBy", currentUserId);
                        infoData.put("participants", participants);
                        
                        chatData.put("info", infoData);
                        
                        // Also add approved riders to participants
                        FirebaseDatabase.getInstance().getReference("tripRequests").child(tripId)
                                .addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override
                                    public void onDataChange(@NonNull DataSnapshot requestsSnapshot) {
                                        for (DataSnapshot reqSnap : requestsSnapshot.getChildren()) {
                                            String status = reqSnap.child("status").getValue(String.class);
                                            String riderUid = reqSnap.child("riderUid").getValue(String.class);
                                            
                                            if ("approved".equals(status) && riderUid != null) {
                                                participants.put(riderUid, true);
                                            }
                                        }
                                        
                                        // Update participants in info
                                        infoData.put("participants", participants);
                                        chatData.put("info", infoData);
                                        
                                        chatRootRef.setValue(chatData).addOnSuccessListener(aVoid -> {
                                            chatRef.child(messageId).setValue(message.toMap());
                                            updateUserChatsWithLatestMessage(text, message.timestamp);
                                            ChatListFragment.markChatAsRead(ChatActivity.this, tripId);
                                        });
                                    }
                                    
                                    @Override
                                    public void onCancelled(@NonNull DatabaseError error) {}
                                });
                    }
                    
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }
    
    private void updateUserChatsWithLatestMessage(String messageText, long timestamp) {
        // Truncate message if too long for preview (keep first 100 chars)
        String truncatedMessage = messageText.length() > 100 
            ? messageText.substring(0, 100) + "..." 
            : messageText;
        
        // Format the last message as "SenderName: message"
        String lastMessagePreview = currentUserName + ": " + truncatedMessage;
        
        // Get all participants in this chat from userChats node
        DatabaseReference userChatsRef = FirebaseDatabase.getInstance().getReference("userChats");
        
        // Check if this is a direct message (starts with "dm_")
        if (tripId.startsWith("dm_")) {
            // Extract the two user IDs from the conversation ID (dm_uid1_uid2)
            String[] parts = tripId.split("_");
            if (parts.length >= 3) {
                String otherUserId = parts[1].equals(currentUserId) ? parts[2] : parts[1];
                
                // Update current user's chat list - use chatId and otherPartyUid as required by rules
                Map<String, Object> myUpdateMap = new HashMap<>();
                myUpdateMap.put("chatId", tripId);
                myUpdateMap.put("lastMessage", lastMessagePreview);
                myUpdateMap.put("lastMessageTime", timestamp);
                myUpdateMap.put("lastMessageSenderId", currentUserId);
                myUpdateMap.put("otherPartyUid", otherUserId);
                userChatsRef.child(currentUserId).child(tripId).updateChildren(myUpdateMap);
                
                // Update other user's chat list
                Map<String, Object> otherUpdateMap = new HashMap<>();
                otherUpdateMap.put("chatId", tripId);
                otherUpdateMap.put("lastMessage", lastMessagePreview);
                otherUpdateMap.put("lastMessageTime", timestamp);
                otherUpdateMap.put("lastMessageSenderId", currentUserId);
                otherUpdateMap.put("otherPartyUid", currentUserId);
                otherUpdateMap.put("otherPartyName", currentUserName);
                userChatsRef.child(otherUserId).child(tripId).updateChildren(otherUpdateMap);
                
                // Also save the other user's name in current user's chat for display
                FirebaseDatabase.getInstance().getReference("users").child(otherUserId)
                        .child("displayName")
                        .addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot snapshot) {
                                String otherUserName = snapshot.getValue(String.class);
                                if (otherUserName != null) {
                                    userChatsRef.child(currentUserId).child(tripId)
                                            .child("otherPartyName").setValue(otherUserName);
                                }
                            }
                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {}
                        });
            }
            return;
        }
        
        // For group chats (trip-based), use isGroupChat flag as required by rules
        // First, get the trip to find the organizer (driver)
        FirebaseDatabase.getInstance().getReference("trips").child(tripId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot tripSnapshot) {
                        if (!tripSnapshot.exists()) return;
                        
                        String driverUid = tripSnapshot.child("driverUid").getValue(String.class);
                        String tripRoute = tripSnapshot.child("originCity").getValue(String.class) + " → " + 
                                         tripSnapshot.child("destinationCity").getValue(String.class);
                        
                        // Update organizer's userChats
                        if (driverUid != null) {
                            Map<String, Object> updateMap = new HashMap<>();
                            updateMap.put("chatId", tripId);
                            updateMap.put("lastMessage", lastMessagePreview);
                            updateMap.put("lastMessageTime", timestamp);
                            updateMap.put("lastMessageSenderId", currentUserId);
                            updateMap.put("isGroupChat", true);
                            updateMap.put("tripRoute", tripRoute);
                            userChatsRef.child(driverUid).child(tripId).updateChildren(updateMap);
                        }
                        
                        // Update all approved participants' userChats
                        FirebaseDatabase.getInstance().getReference("tripRequests").child(tripId)
                                .addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override
                                    public void onDataChange(@NonNull DataSnapshot requestsSnapshot) {
                                        for (DataSnapshot reqSnap : requestsSnapshot.getChildren()) {
                                            String status = reqSnap.child("status").getValue(String.class);
                                            String riderUid = reqSnap.child("riderUid").getValue(String.class);
                                            
                                            if ("approved".equals(status) && riderUid != null) {
                                                Map<String, Object> updateMap = new HashMap<>();
                                                updateMap.put("chatId", tripId);
                                                updateMap.put("lastMessage", lastMessagePreview);
                                                updateMap.put("lastMessageTime", timestamp);
                                                updateMap.put("lastMessageSenderId", currentUserId);
                                                updateMap.put("isGroupChat", true);
                                                updateMap.put("tripRoute", tripRoute);
                                                userChatsRef.child(riderUid).child(tripId).updateChildren(updateMap);
                                            }
                                        }
                                    }
                                    
                                    @Override
                                    public void onCancelled(@NonNull DatabaseError error) {}
                                });
                    }
                    
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (chatListener != null) chatRef.removeEventListener(chatListener);
    }

    private static final int TYPE_SENT = 0;
    private static final int TYPE_RECEIVED = 1;

    private class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        @Override
        public int getItemViewType(int position) {
            return messages.get(position).senderUid.equals(currentUserId) ? TYPE_SENT : TYPE_RECEIVED;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_SENT) {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_chat_message_sent, parent, false);
                return new SentVH(view);
            } else {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_chat_message_received, parent, false);
                return new ReceivedVH(view);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            ChatMessage msg = messages.get(position);
            DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault());
            LocalDateTime time = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(msg.timestamp), ZoneId.systemDefault());
            String timeStr = time.format(timeFormat);

            if (holder instanceof SentVH) {
                ((SentVH) holder).tvMessage.setText(msg.text);
                ((SentVH) holder).tvTime.setText(timeStr);
            } else if (holder instanceof ReceivedVH) {
                ((ReceivedVH) holder).tvSenderName.setText(msg.senderName);
                ((ReceivedVH) holder).tvMessage.setText(msg.text);
                ((ReceivedVH) holder).tvTime.setText(timeStr);
            }
        }

        @Override
        public int getItemCount() {
            return messages.size();
        }

        class SentVH extends RecyclerView.ViewHolder {
            TextView tvMessage, tvTime;

            SentVH(@NonNull View itemView) {
                super(itemView);
                tvMessage = itemView.findViewById(R.id.tvMessage);
                tvTime = itemView.findViewById(R.id.tvTime);
            }
        }

        class ReceivedVH extends RecyclerView.ViewHolder {
            TextView tvSenderName, tvMessage, tvTime;

            ReceivedVH(@NonNull View itemView) {
                super(itemView);
                tvSenderName = itemView.findViewById(R.id.tvSenderName);
                tvMessage = itemView.findViewById(R.id.tvMessage);
                tvTime = itemView.findViewById(R.id.tvTime);
            }
        }
    }
}

