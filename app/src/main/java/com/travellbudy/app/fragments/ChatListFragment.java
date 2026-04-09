package com.travellbudy.app.fragments;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.travellbudy.app.ChatActivity;
import com.travellbudy.app.R;
import com.travellbudy.app.databinding.FragmentChatListBinding;
import com.travellbudy.app.databinding.ItemChatPreviewBinding;
import com.travellbudy.app.models.SeatRequest;
import com.travellbudy.app.models.Trip;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatListFragment extends Fragment {

    private static final String PREFS_CHAT_READ_STATUS = "chat_read_status";
    
    private FragmentChatListBinding binding;
    private ChatListAdapter adapter;
    private final List<ChatItem> chatItems = new ArrayList<>();
    private final List<ChatItem> filteredChatItems = new ArrayList<>();
    private String currentUserId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentChatListBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        }

        setupWindowInsets();
        
        adapter = new ChatListAdapter();
        binding.rvChats.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvChats.setAdapter(adapter);

        // Search functionality
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterChats(s.toString().trim());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        loadChatTrips();
    }
    
    /**
     * Set up window insets for proper safe area handling on notched devices
     */
    private void setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.headerSection, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars());
            v.setPadding(v.getPaddingLeft(), insets.top, v.getPaddingRight(), v.getPaddingBottom());
            return windowInsets;
        });
    }
    
    @Override
    public void onResume() {
        super.onResume();
        // Refresh the chat list when returning from a chat to show updated messages
        if (adapter != null && currentUserId != null) {
            loadChatTrips();
        }
    }

    private void filterChats(String query) {
        filteredChatItems.clear();
        if (query.isEmpty()) {
            filteredChatItems.addAll(chatItems);
        } else {
            String lowerQuery = query.toLowerCase();
            for (ChatItem item : chatItems) {
                if (item.displayName.toLowerCase().contains(lowerQuery) ||
                    item.tripType.toLowerCase().contains(lowerQuery) ||
                    item.lastMessage.toLowerCase().contains(lowerQuery)) {
                    filteredChatItems.add(item);
                }
            }
        }
        adapter.notifyDataSetChanged();
        updateEmptyState();
    }

    private void updateEmptyState() {
        boolean isEmpty = filteredChatItems.isEmpty();
        binding.emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        binding.rvChats.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    private void loadChatTrips() {
        if (currentUserId == null) return;

        // Load chats from userChats node which is populated when creating trips and approving requests
        FirebaseDatabase.getInstance().getReference("userChats")
                .child(currentUserId)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        chatItems.clear();
                        filteredChatItems.clear();
                        
                        if (!snapshot.exists() || !snapshot.hasChildren()) {
                            // Fallback: load from trips directly for backwards compatibility
                            loadChatsFromTrips();
                            return;
                        }
                        
                        // First collect all chat data from userChats snapshot
                        List<ChatSnapshotData> chatDataList = new ArrayList<>();
                        for (DataSnapshot chatSnapshot : snapshot.getChildren()) {
                            String tripId = chatSnapshot.child("tripId").getValue(String.class);
                            if (tripId == null) {
                                tripId = chatSnapshot.getKey();
                            }
                            
                            if (tripId == null) continue;
                            
                            ChatSnapshotData data = new ChatSnapshotData();
                            data.tripId = tripId;
                            data.chatName = chatSnapshot.child("otherPartyName").getValue(String.class);
                            data.lastMessage = chatSnapshot.child("lastMessage").getValue(String.class);
                            data.lastMessageTime = chatSnapshot.child("lastMessageTime").getValue(Long.class);
                            data.lastMessageSenderId = chatSnapshot.child("lastMessageSenderId").getValue(String.class);
                            data.isDirectMessage = Boolean.TRUE.equals(chatSnapshot.child("isDirectMessage").getValue(Boolean.class));
                            // Read otherPartyUid (the correct field name used in ChatActivity)
                            data.otherUserId = chatSnapshot.child("otherPartyUid").getValue(String.class);
                            chatDataList.add(data);
                        }
                        
                        // Now load trip details for each chat
                        final int totalChats = chatDataList.size();
                        final int[] loadedCount = {0};
                        
                        // If no chats to load, just update empty state
                        if (totalChats == 0) {
                            updateEmptyState();
                            return;
                        }
                        
                        for (ChatSnapshotData chatData : chatDataList) {
                            // Handle direct messages differently - they don't have a trip
                            if (chatData.isDirectMessage || chatData.tripId.startsWith("dm_")) {
                                loadDirectMessageChat(chatData, loadedCount, totalChats);
                                continue;
                            }
                            
                            // For group chats, load trip details
                            FirebaseDatabase.getInstance().getReference("trips")
                                    .child(chatData.tripId)
                                    .addListenerForSingleValueEvent(new ValueEventListener() {
                                        @Override
                                        public void onDataChange(@NonNull DataSnapshot tripSnapshot) {
                                            Trip trip = tripSnapshot.getValue(Trip.class);
                                            if (trip != null) {
                                                ChatItem item = new ChatItem();
                                                item.trip = trip;
                                                item.tripId = chatData.tripId;
                                                item.isOrganizer = currentUserId.equals(trip.driverUid);
                                                
                                                // Use chat name or generate from trip - always show "Group: " prefix
                                                if (chatData.chatName != null && !chatData.chatName.isEmpty()) {
                                                    item.displayName = "Group: " + chatData.chatName;
                                                } else {
                                                    item.displayName = "Group: " + generateTripTitle(trip);
                                                }
                                                
                                                item.tripType = generateTripTypeLabel(trip);
                                                item.avatarUrl = trip.imageUrl; // Use trip cover image for all participants
                                                item.lastMessage = chatData.lastMessage != null && !chatData.lastMessage.isEmpty() 
                                                    ? chatData.lastMessage : "No messages yet";
                                                item.lastMessageTime = chatData.lastMessageTime != null ? chatData.lastMessageTime : trip.createdAt;
                                                item.lastMessageSenderId = chatData.lastMessageSenderId;
                                                item.unreadCount = 0;
                                                item.isOnline = false;
                                                
                                                // Always load from actual messages to ensure correct unread status
                                                loadActualMessageSenderAndUpdate(item, loadedCount, totalChats);
                                            } else {
                                                loadedCount[0]++;
                                                // Update UI when all chats are loaded
                                                if (loadedCount[0] >= totalChats) {
                                                    filteredChatItems.clear();
                                                    filteredChatItems.addAll(chatItems);
                                                    adapter.notifyDataSetChanged();
                                                    updateEmptyState();
                                                }
                                            }
                                        }
                                        
                                        @Override
                                        public void onCancelled(@NonNull DatabaseError error) {
                                            loadedCount[0]++;
                                            if (loadedCount[0] >= totalChats) {
                                                filteredChatItems.clear();
                                                filteredChatItems.addAll(chatItems);
                                                adapter.notifyDataSetChanged();
                                                updateEmptyState();
                                            }
                                        }
                                    });
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }
    
    /**
     * Load a direct message chat (not associated with a trip)
     */
    private void loadDirectMessageChat(ChatSnapshotData chatData, int[] loadedCount, int totalChats) {
        ChatItem item = new ChatItem();
        item.tripId = chatData.tripId;
        item.isDirectMessage = true;
        item.isOrganizer = false;
        
        // Get the other user's ID - either from stored data or extract from dm_uid1_uid2 format
        String otherUserId = chatData.otherUserId;
        if (otherUserId == null && chatData.tripId.startsWith("dm_")) {
            String[] parts = chatData.tripId.split("_");
            if (parts.length >= 3) {
                otherUserId = parts[1].equals(currentUserId) ? parts[2] : parts[1];
            }
        }
        item.otherUserId = otherUserId;
        
        // Display name is the other person's name (no "Group:" prefix)
        if (chatData.chatName != null && !chatData.chatName.isEmpty()) {
            item.displayName = chatData.chatName;
        } else {
            item.displayName = "Direct Message";
        }
        
        item.tripType = "DIRECT MESSAGE";
        item.lastMessage = chatData.lastMessage != null && !chatData.lastMessage.isEmpty() 
            ? chatData.lastMessage : "No messages yet";
        item.lastMessageTime = chatData.lastMessageTime != null ? chatData.lastMessageTime : System.currentTimeMillis();
        item.lastMessageSenderId = chatData.lastMessageSenderId;
        item.unreadCount = 0;
        item.isOnline = false;
        
        // Load the other user's profile photo for the avatar
        if (otherUserId != null) {
            final String finalOtherUserId = otherUserId;
            FirebaseDatabase.getInstance().getReference("users").child(otherUserId)
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            String photoUrl = snapshot.child("photoUrl").getValue(String.class);
                            String displayName = snapshot.child("displayName").getValue(String.class);
                            
                            if (photoUrl != null && !photoUrl.isEmpty()) {
                                item.avatarUrl = photoUrl;
                            }
                            
                            // Update display name if we got it from the user profile
                            if (displayName != null && !displayName.isEmpty()) {
                                item.displayName = displayName;
                            }
                            
                            // Check for unread messages
                            item.hasUnreadMessages = hasUnreadMessages(item.tripId, item.lastMessageTime, item.lastMessageSenderId);
                            
                            addChatItemIfNotExists(item);
                            loadedCount[0]++;
                            if (loadedCount[0] >= totalChats) {
                                sortAndUpdateChatList();
                            }
                        }
                        
                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            item.hasUnreadMessages = hasUnreadMessages(item.tripId, item.lastMessageTime, item.lastMessageSenderId);
                            addChatItemIfNotExists(item);
                            loadedCount[0]++;
                            if (loadedCount[0] >= totalChats) {
                                sortAndUpdateChatList();
                            }
                        }
                    });
        } else {
            item.hasUnreadMessages = hasUnreadMessages(item.tripId, item.lastMessageTime, item.lastMessageSenderId);
            addChatItemIfNotExists(item);
            loadedCount[0]++;
            if (loadedCount[0] >= totalChats) {
                sortAndUpdateChatList();
            }
        }
    }
    
    /**
     * Sort chat items by last message time and update the UI
     */
    private void sortAndUpdateChatList() {
        // Sort by last message time (newest first)
        chatItems.sort((a, b) -> Long.compare(b.lastMessageTime, a.lastMessageTime));
        filteredChatItems.clear();
        filteredChatItems.addAll(chatItems);
        adapter.notifyDataSetChanged();
        updateEmptyState();
    }
    
    private void loadLatestMessage(ChatItem item) {
        // Load all messages and find the latest one manually
        // This is more reliable than orderByChild which requires an index
        FirebaseDatabase.getInstance()
                .getReference("chats")
                .child(item.tripId)
                .child("messages")
                .addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists() && snapshot.hasChildren()) {
                    String latestText = null;
                    String latestSenderName = null;
                    String latestSenderId = null;
                    long latestTimestamp = 0;
                    
                    // Find the message with the highest timestamp
                    for (DataSnapshot msgSnap : snapshot.getChildren()) {
                        Long timestamp = msgSnap.child("timestamp").getValue(Long.class);
                        if (timestamp != null && timestamp > latestTimestamp) {
                            latestTimestamp = timestamp;
                            latestText = msgSnap.child("text").getValue(String.class);
                            latestSenderName = msgSnap.child("senderName").getValue(String.class);
                            latestSenderId = msgSnap.child("senderUid").getValue(String.class);
                        }
                    }
                    
                    if (latestText != null) {
                        // Truncate long messages for preview
                        String truncatedText = latestText.length() > 100 
                            ? latestText.substring(0, 100) + "..." 
                            : latestText;
                        
                        if (latestSenderName != null && !latestSenderName.isEmpty()) {
                            item.lastMessage = latestSenderName + ": " + truncatedText;
                        } else {
                            item.lastMessage = truncatedText;
                        }
                        item.lastMessageTime = latestTimestamp;
                        item.lastMessageSenderId = latestSenderId;
                    }
                }
                
                // Check if there are unread messages (only if sent by someone else)
                item.hasUnreadMessages = hasUnreadMessages(item.tripId, item.lastMessageTime, item.lastMessageSenderId);
                
                // Add to list and update UI
                chatItems.add(item);
                filteredChatItems.clear();
                filteredChatItems.addAll(chatItems);
                adapter.notifyDataSetChanged();
                updateEmptyState();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Still add the item even if message load fails
                chatItems.add(item);
                filteredChatItems.clear();
                filteredChatItems.addAll(chatItems);
                adapter.notifyDataSetChanged();
                updateEmptyState();
            }
        });
    }
    
    /**
     * Check if a chat has unread messages by comparing last message time with last read time
     * Only show as unread if the message was sent by someone else
     */
    private boolean hasUnreadMessages(String tripId, long lastMessageTime, String lastMessageSenderId) {
        if (getContext() == null) return false;
        
        // If there's no last message time, there are no messages
        if (lastMessageTime <= 0) {
            return false;
        }
        
        // If there's no last message sender, there are no messages yet, so no unread messages
        if (lastMessageSenderId == null || lastMessageSenderId.trim().isEmpty()) {
            return false;
        }
        
        // If the current user sent the last message, don't show as unread
        if (lastMessageSenderId.trim().equals(currentUserId)) {
            return false;
        }
        
        // Use user-specific key to handle multiple accounts on same device
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_CHAT_READ_STATUS, Context.MODE_PRIVATE);
        String userSpecificKey = currentUserId + "_" + tripId;
        long lastReadTime = prefs.getLong(userSpecificKey, 0);
        
        // If the last message is newer than when we last read, there are unread messages
        return lastMessageTime > lastReadTime;
    }
    
    /**
     * Load the actual message sender from chat messages when userChats doesn't have it
     */
    private void loadActualMessageSenderAndUpdate(ChatItem item, int[] loadedCount, int totalChats) {
        FirebaseDatabase.getInstance()
                .getReference("chats")
                .child(item.tripId)
                .child("messages")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists() && snapshot.hasChildren()) {
                            String latestText = null;
                            String latestSenderName = null;
                            String latestSenderId = null;
                            long latestTimestamp = 0;
                            
                            for (DataSnapshot msgSnap : snapshot.getChildren()) {
                                Long timestamp = msgSnap.child("timestamp").getValue(Long.class);
                                if (timestamp != null && timestamp > latestTimestamp) {
                                    latestTimestamp = timestamp;
                                    latestText = msgSnap.child("text").getValue(String.class);
                                    latestSenderName = msgSnap.child("senderName").getValue(String.class);
                                    latestSenderId = msgSnap.child("senderUid").getValue(String.class);
                                }
                            }
                            
                            if (latestSenderId != null) {
                                item.lastMessageSenderId = latestSenderId;
                                item.lastMessageTime = latestTimestamp;
                            }
                            
                            // Also update the lastMessage text from actual messages
                            if (latestText != null && !latestText.isEmpty()) {
                                String truncatedText = latestText.length() > 100 
                                    ? latestText.substring(0, 100) + "..." 
                                    : latestText;
                                
                                if (latestSenderName != null && !latestSenderName.isEmpty()) {
                                    item.lastMessage = latestSenderName + ": " + truncatedText;
                                } else {
                                    item.lastMessage = truncatedText;
                                }
                            }
                        }
                        
                        // Now check for unread messages with the correct sender ID
                        item.hasUnreadMessages = hasUnreadMessages(item.tripId, item.lastMessageTime, item.lastMessageSenderId);
                        
                        addChatItemIfNotExists(item);
                        
                        loadedCount[0]++;
                        if (loadedCount[0] >= totalChats) {
                            filteredChatItems.clear();
                            filteredChatItems.addAll(chatItems);
                            adapter.notifyDataSetChanged();
                            updateEmptyState();
                        }
                    }
                    
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        // Still add the item even if message load fails
                        item.hasUnreadMessages = hasUnreadMessages(item.tripId, item.lastMessageTime, item.lastMessageSenderId);
                        addChatItemIfNotExists(item);
                        
                        loadedCount[0]++;
                        if (loadedCount[0] >= totalChats) {
                            filteredChatItems.clear();
                            filteredChatItems.addAll(chatItems);
                            adapter.notifyDataSetChanged();
                            updateEmptyState();
                        }
                    }
                });
    }
    
    /**
     * Add a chat item to the list if it doesn't already exist
     */
    private void addChatItemIfNotExists(ChatItem item) {
        boolean alreadyExists = false;
        for (ChatItem existing : chatItems) {
            if (existing.tripId.equals(item.tripId)) {
                alreadyExists = true;
                break;
            }
        }
        if (!alreadyExists) {
            chatItems.add(item);
        }
    }
    
    /**
     * Mark a chat as read by storing the current timestamp
     * Uses user-specific key to handle multiple accounts on same device
     */
    public static void markChatAsRead(Context context, String tripId) {
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null 
            ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (currentUserId == null) return;
        
        SharedPreferences prefs = context.getSharedPreferences(PREFS_CHAT_READ_STATUS, Context.MODE_PRIVATE);
        String userSpecificKey = currentUserId + "_" + tripId;
        prefs.edit().putLong(userSpecificKey, System.currentTimeMillis()).apply();
    }
    
    /**
     * Fallback method to load chats from trips for backwards compatibility
     */
    private void loadChatsFromTrips() {
        FirebaseDatabase.getInstance().getReference("trips")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        chatItems.clear();
                        for (DataSnapshot child : snapshot.getChildren()) {
                            Trip trip = child.getValue(Trip.class);
                            if (trip == null) continue;

                            // Check if user is driver
                            if (currentUserId.equals(trip.driverUid)) {
                                loadChatItemForTrip(trip, true);
                                continue;
                            }

                            // Check if user is approved rider
                            FirebaseDatabase.getInstance().getReference("tripRequests")
                                    .child(trip.tripId)
                                    .orderByChild("riderUid")
                                    .equalTo(currentUserId)
                                    .addListenerForSingleValueEvent(new ValueEventListener() {
                                        @Override
                                        public void onDataChange(@NonNull DataSnapshot reqSnapshot) {
                                            for (DataSnapshot reqSnap : reqSnapshot.getChildren()) {
                                                SeatRequest req = reqSnap.getValue(SeatRequest.class);
                                                if (req != null && "approved".equals(req.status)) {
                                                    loadChatItemForTrip(trip, false);
                                                    break;
                                                }
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

    private void loadChatItemForTrip(Trip trip, boolean isOrganizer) {
        // Load all messages for this trip and find the latest one
        FirebaseDatabase.getInstance()
                .getReference("chats")
                .child(trip.tripId)
                .child("messages")
                .addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                ChatItem item = new ChatItem();
                item.trip = trip;
                item.tripId = trip.tripId;
                item.isOrganizer = isOrganizer;

                // Set display name - always show "Group: " prefix for group chats
                item.displayName = "Group: " + generateTripTitle(trip);

                // Set trip type label
                item.tripType = generateTripTypeLabel(trip);

                // Set avatar URL - use trip cover image for all participants
                item.avatarUrl = trip.imageUrl;

                // Find the latest message
                if (snapshot.exists() && snapshot.hasChildren()) {
                    String latestText = null;
                    String latestSenderName = null;
                    String latestSenderId = null;
                    long latestTimestamp = 0;
                    
                    for (DataSnapshot msgSnap : snapshot.getChildren()) {
                        Long timestamp = msgSnap.child("timestamp").getValue(Long.class);
                        if (timestamp != null && timestamp > latestTimestamp) {
                            latestTimestamp = timestamp;
                            latestText = msgSnap.child("text").getValue(String.class);
                            latestSenderName = msgSnap.child("senderName").getValue(String.class);
                            latestSenderId = msgSnap.child("senderUid").getValue(String.class);
                        }
                    }
                    
                    if (latestText != null) {
                        String truncatedText = latestText.length() > 100 
                            ? latestText.substring(0, 100) + "..." 
                            : latestText;
                        
                        if (latestSenderName != null && !latestSenderName.isEmpty()) {
                            item.lastMessage = latestSenderName + ": " + truncatedText;
                        } else {
                            item.lastMessage = truncatedText;
                        }
                        item.lastMessageTime = latestTimestamp;
                        item.lastMessageSenderId = latestSenderId;
                    } else {
                        item.lastMessage = "No messages yet";
                        item.lastMessageTime = trip.createdAt;
                        item.lastMessageSenderId = null;
                    }
                } else {
                    item.lastMessage = "No messages yet";
                    item.lastMessageTime = trip.createdAt;
                    item.lastMessageSenderId = null;
                }

                // Check for unread messages
                loadUnreadCount(item);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void loadUnreadCount(ChatItem item) {
        // Check for unread messages (only if sent by someone else)
        item.hasUnreadMessages = hasUnreadMessages(item.tripId, item.lastMessageTime, item.lastMessageSenderId);
        item.unreadCount = 0;
        item.isOnline = false;

        // Check for duplicates before adding
        boolean alreadyExists = false;
        for (ChatItem existing : chatItems) {
            if (existing.tripId.equals(item.tripId)) {
                alreadyExists = true;
                break;
            }
        }
        
        if (!alreadyExists) {
            // Add to list and update UI
            chatItems.add(item);
            filteredChatItems.clear();
            filteredChatItems.addAll(chatItems);
            adapter.notifyDataSetChanged();
            updateEmptyState();
        }
    }

    private String generateTripTitle(Trip trip) {
        String activityLabel = getActivityLabel(trip.activityType);
        if (activityLabel != null) {
            return activityLabel;
        }
        return trip.destinationCity != null ? trip.destinationCity : "Adventure";
    }

    private String generateTripTypeLabel(Trip trip) {
        String activityLabel = getActivityLabel(trip.activityType);
        String destination = trip.destinationCity != null ? trip.destinationCity : "";

        if (activityLabel != null && !destination.isEmpty()) {
            return destination.toUpperCase() + " " + activityLabel.toUpperCase();
        } else if (activityLabel != null) {
            return activityLabel.toUpperCase();
        } else if (!destination.isEmpty()) {
            return destination.toUpperCase() + " TRIP";
        }
        return "ADVENTURE";
    }

    private String getActivityLabel(String activityType) {
        if (activityType == null) return null;
        switch (activityType) {
            case "hiking": return "Hiking";
            case "camping": return "Camping";
            case "road_trip": return "Road Trip";
            case "city_explore": return "City Break";
            case "festival": return "Festival Trip";
            case "photography": return "Photography";
            case "outdoor_sports": return "Sports";
            case "backpacking": return "Backpacking";
            case "weekend": return "Weekend";
            default: return null;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    // ─────────────────────────────────────────────────────────────────
    // Chat Snapshot Data Helper Class (for collecting data before loading trips)
    // ─────────────────────────────────────────────────────────────────
    
    private static class ChatSnapshotData {
        String tripId;
        String chatName;
        String lastMessage;
        Long lastMessageTime;
        String lastMessageSenderId;
        boolean isDirectMessage;
        String otherUserId;
    }

    // ─────────────────────────────────────────────────────────────────
    // Chat Item Data Class
    // ─────────────────────────────────────────────────────────────────

    private static class ChatItem {
        Trip trip;
        String tripId;
        String displayName;
        String lastMessage;
        long lastMessageTime;
        String lastMessageSenderId; // Track who sent the last message
        String tripType;
        String avatarUrl;
        int unreadCount;
        boolean isOnline;
        boolean isOrganizer;
        boolean hasUnreadMessages;
        boolean isDirectMessage;
        String otherUserId;
    }

    // ─────────────────────────────────────────────────────────────────
    // Adapter
    // ─────────────────────────────────────────────────────────────────

    private class ChatListAdapter extends RecyclerView.Adapter<ChatListAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemChatPreviewBinding itemBinding = ItemChatPreviewBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new VH(itemBinding);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            holder.bind(filteredChatItems.get(position));
        }

        @Override
        public int getItemCount() {
            return filteredChatItems.size();
        }

        class VH extends RecyclerView.ViewHolder {
            private final ItemChatPreviewBinding b;

            VH(ItemChatPreviewBinding binding) {
                super(binding.getRoot());
                this.b = binding;
            }

            void bind(ChatItem item) {
                // Name
                b.tvName.setText(item.displayName);

                // Last message
                b.tvLastMessage.setText(item.lastMessage);

                // Trip type label
                b.tvTripType.setText(item.tripType);

                // Timestamp
                b.tvTime.setText(formatTimestamp(item.lastMessageTime));

                // Avatar - use profile photo for DMs, trip cover image for group chats
                String imageUrl = item.avatarUrl;
                if (imageUrl != null && !imageUrl.isEmpty() && !imageUrl.equals("null")) {
                    Glide.with(b.ivAvatar.getContext())
                            .load(imageUrl)
                            .placeholder(item.isDirectMessage ? R.drawable.ic_person : R.drawable.ic_people)
                            .error(item.isDirectMessage ? R.drawable.ic_person : R.drawable.ic_people)
                            .circleCrop()
                            .into(b.ivAvatar);
                } else {
                    // Fallback to appropriate icon based on chat type
                    b.ivAvatar.setImageResource(item.isDirectMessage ? R.drawable.ic_person : R.drawable.ic_people);
                }

                // Online indicator
                b.onlineIndicator.setVisibility(item.isOnline ? View.VISIBLE : View.GONE);

                // Read/Unread status
                if (item.hasUnreadMessages) {
                    // Show blue dot for unread messages
                    b.unreadDot.setVisibility(View.VISIBLE);
                    b.ivReadIndicator.setVisibility(View.GONE);
                    b.tvUnreadCount.setVisibility(View.GONE);
                } else {
                    // Show grey tick for read messages
                    b.unreadDot.setVisibility(View.GONE);
                    b.ivReadIndicator.setVisibility(View.VISIBLE);
                    b.tvUnreadCount.setVisibility(View.GONE);
                }

                // Click listener
                b.getRoot().setOnClickListener(v -> {
                    Intent intent = new Intent(requireContext(), ChatActivity.class);
                    intent.putExtra(ChatActivity.EXTRA_TRIP_ID, item.tripId);
                    intent.putExtra(ChatActivity.EXTRA_TRIP_ROUTE, item.displayName);
                    startActivity(intent);
                });
            }

            private String formatTimestamp(long timestamp) {
                if (timestamp == 0) return "";

                Date date = new Date(timestamp);
                Calendar now = Calendar.getInstance();
                Calendar messageTime = Calendar.getInstance();
                messageTime.setTime(date);

                // Same day - show time
                if (now.get(Calendar.YEAR) == messageTime.get(Calendar.YEAR) &&
                    now.get(Calendar.DAY_OF_YEAR) == messageTime.get(Calendar.DAY_OF_YEAR)) {
                    return new SimpleDateFormat("h:mm a", Locale.getDefault()).format(date);
                }

                // Yesterday
                Calendar yesterday = Calendar.getInstance();
                yesterday.add(Calendar.DAY_OF_YEAR, -1);
                if (yesterday.get(Calendar.YEAR) == messageTime.get(Calendar.YEAR) &&
                    yesterday.get(Calendar.DAY_OF_YEAR) == messageTime.get(Calendar.DAY_OF_YEAR)) {
                    return "Yesterday";
                }

                // Within this week - show day name
                if (now.get(Calendar.WEEK_OF_YEAR) == messageTime.get(Calendar.WEEK_OF_YEAR) &&
                    now.get(Calendar.YEAR) == messageTime.get(Calendar.YEAR)) {
                    return new SimpleDateFormat("EEE", Locale.getDefault()).format(date);
                }

                // Older - show date
                return new SimpleDateFormat("MMM d", Locale.getDefault()).format(date);
            }
        }
    }
}
