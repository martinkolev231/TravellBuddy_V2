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
    
    // Firebase listener tracking
    private com.google.firebase.database.DatabaseReference userChatsRef;
    private ValueEventListener userChatsListener;
    private com.google.firebase.database.DatabaseReference tripsRef;
    private ValueEventListener tripsListener;

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
        // Check if user is still logged in
        if (FirebaseAuth.getInstance().getCurrentUser() != null && adapter != null && currentUserId != null) {
            loadChatTrips();
        }
    }
    
    @Override
    public void onPause() {
        super.onPause();
        // Remove listeners when fragment is paused
        removeListeners();
    }
    
    private void removeListeners() {
        if (userChatsRef != null && userChatsListener != null) {
            userChatsRef.removeEventListener(userChatsListener);
        }
        if (tripsRef != null && tripsListener != null) {
            tripsRef.removeEventListener(tripsListener);
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
        // Safety check - don't update UI if fragment is detached
        if (!isAdded() || binding == null) return;
        
        boolean isEmpty = filteredChatItems.isEmpty();
        binding.emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        binding.rvChats.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    private void loadChatTrips() {
        if (currentUserId == null) return;
        
        // Remove existing listener before adding new one
        if (userChatsRef != null && userChatsListener != null) {
            userChatsRef.removeEventListener(userChatsListener);
        }

        userChatsRef = FirebaseDatabase.getInstance().getReference("userChats")
                .child(currentUserId);
        userChatsListener = new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        // Safety check - don't update if fragment is detached
                        if (!isAdded() || binding == null) return;
                        
                        chatItems.clear();
                        filteredChatItems.clear();

                        if (!snapshot.exists() || !snapshot.hasChildren()) {
                            loadChatsFromTrips();
                            return;
                        }

                        int totalChats = (int) snapshot.getChildrenCount();
                        int[] loadedCount = {0};

                        for (DataSnapshot chatSnapshot : snapshot.getChildren()) {
                            String tripId = chatSnapshot.child("tripId").getValue(String.class);
                            if (tripId == null) {
                                tripId = chatSnapshot.getKey();
                            }
                            if (tripId == null) {
                                loadedCount[0]++;
                                continue;
                            }

                            String chatName = chatSnapshot.child("chatName").getValue(String.class);
                            String lastMessage = chatSnapshot.child("lastMessage").getValue(String.class);
                            Long lastMessageTime = chatSnapshot.child("lastMessageTime").getValue(Long.class);
                            String lastMessageSenderId = chatSnapshot.child("lastMessageSenderId").getValue(String.class);
                            boolean isDirectMessage = Boolean.TRUE.equals(chatSnapshot.child("isDirectMessage").getValue(Boolean.class));
                            String otherUserId = chatSnapshot.child("otherPartyUid").getValue(String.class);

                            ChatSnapshotData data = new ChatSnapshotData();
                            data.tripId = tripId;
                            data.chatName = chatName;
                            data.lastMessage = lastMessage;
                            data.lastMessageTime = lastMessageTime;
                            data.lastMessageSenderId = lastMessageSenderId;
                            data.isDirectMessage = isDirectMessage;
                            data.otherUserId = otherUserId;

                            if (isDirectMessage) {
                                loadDirectMessageChat(data, loadedCount, totalChats);
                            } else {
                                loadGroupChat(data, loadedCount, totalChats);
                            }
                        }

                        if (totalChats == 0) {
                            updateEmptyState();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        loadChatsFromTrips();
                    }
                };
        userChatsRef.addValueEventListener(userChatsListener);
    }

    private void loadDirectMessageChat(ChatSnapshotData chatData, int[] loadedCount, int totalChats) {
        ChatItem item = new ChatItem();
        item.tripId = chatData.tripId;
        item.isDirectMessage = true;

        String otherUserId = chatData.otherUserId;
        if (otherUserId == null && chatData.tripId.startsWith("dm_")) {
            String[] parts = chatData.tripId.split("_");
            if (parts.length >= 3) {
                otherUserId = parts[1].equals(currentUserId) ? parts[2] : parts[1];
            }
        }
        item.otherUserId = otherUserId;

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

                            if (displayName != null && !displayName.isEmpty()) {
                                item.displayName = displayName;
                            }

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

    private void loadGroupChat(ChatSnapshotData chatData, int[] loadedCount, int totalChats) {
        FirebaseDatabase.getInstance().getReference("trips").child(chatData.tripId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        Trip trip = snapshot.getValue(Trip.class);
                        if (trip != null) {
                            ChatItem item = new ChatItem();
                            item.trip = trip;
                            item.tripId = chatData.tripId;
                            item.isOrganizer = currentUserId.equals(trip.driverUid);
                            item.isDirectMessage = false;

                            if (chatData.chatName != null && !chatData.chatName.isEmpty()) {
                                item.displayName = "Group: " + chatData.chatName;
                            } else {
                                item.displayName = "Group: " + generateTripTitle(trip);
                            }

                            item.tripType = generateTripTypeLabel(trip);
                            item.avatarUrl = trip.imageUrl;
                            item.lastMessage = chatData.lastMessage != null && !chatData.lastMessage.isEmpty()
                                ? chatData.lastMessage : "No messages yet";
                            item.lastMessageTime = chatData.lastMessageTime != null ? chatData.lastMessageTime : trip.createdAt;
                            item.lastMessageSenderId = chatData.lastMessageSenderId;
                            item.unreadCount = 0;
                            item.isOnline = false;

                            loadActualMessageSenderAndUpdate(item, loadedCount, totalChats);
                        } else {
                            loadedCount[0]++;
                            if (loadedCount[0] >= totalChats) {
                                sortAndUpdateChatList();
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        loadedCount[0]++;
                        if (loadedCount[0] >= totalChats) {
                            sortAndUpdateChatList();
                        }
                    }
                });
    }

    private void sortAndUpdateChatList() {
        // Safety check - don't update if fragment is detached
        if (!isAdded() || binding == null || adapter == null) return;
        
        chatItems.sort((a, b) -> Long.compare(b.lastMessageTime, a.lastMessageTime));
        filteredChatItems.clear();
        filteredChatItems.addAll(chatItems);
        adapter.notifyDataSetChanged();
        updateEmptyState();
    }

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
    }

    private boolean hasUnreadMessages(String tripId, long lastMessageTime, String lastMessageSenderId) {
        if (getContext() == null) return false;
        if (lastMessageTime <= 0) return false;
        if (lastMessageSenderId == null || lastMessageSenderId.trim().isEmpty()) return false;
        if (lastMessageSenderId.trim().equals(currentUserId)) return false;

        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_CHAT_READ_STATUS, Context.MODE_PRIVATE);
        String userSpecificKey = currentUserId + "_" + tripId;
        long lastReadTime = prefs.getLong(userSpecificKey, 0);

        return lastMessageTime > lastReadTime;
    }

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

    public static void markChatAsRead(Context context, String tripId) {
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null
            ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (currentUserId == null) return;

        SharedPreferences prefs = context.getSharedPreferences(PREFS_CHAT_READ_STATUS, Context.MODE_PRIVATE);
        String userSpecificKey = currentUserId + "_" + tripId;
        prefs.edit().putLong(userSpecificKey, System.currentTimeMillis()).apply();
    }

    private void loadChatsFromTrips() {
        // Remove existing listener before adding new one
        if (tripsRef != null && tripsListener != null) {
            tripsRef.removeEventListener(tripsListener);
        }
        
        tripsRef = FirebaseDatabase.getInstance().getReference("trips");
        tripsListener = new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        chatItems.clear();
                        for (DataSnapshot child : snapshot.getChildren()) {
                            Trip trip = child.getValue(Trip.class);
                            if (trip == null) continue;

                            if (currentUserId.equals(trip.driverUid)) {
                                loadChatItemForTrip(trip, true);
                                continue;
                            }

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
                };
        tripsRef.addValueEventListener(tripsListener);
    }

    private void loadChatItemForTrip(Trip trip, boolean isOrganizer) {
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

                        if (isOrganizer) {
                            item.displayName = "Group: " + generateTripTitle(trip);
                        } else {
                            item.displayName = trip.driverName != null ? trip.driverName : "Unknown";
                        }

                        item.tripType = generateTripTypeLabel(trip);
                        item.avatarUrl = isOrganizer ? trip.imageUrl : trip.driverPhotoUrl;

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
                                if (latestSenderName != null && !latestSenderName.isEmpty()) {
                                    item.lastMessage = latestSenderName + ": " + latestText;
                                } else {
                                    item.lastMessage = latestText;
                                }
                            } else {
                                item.lastMessage = "No messages yet";
                            }
                            item.lastMessageTime = latestTimestamp;
                            item.lastMessageSenderId = latestSenderId;
                        } else {
                            item.lastMessage = "No messages yet";
                            item.lastMessageTime = trip.createdAt;
                            item.lastMessageSenderId = null;
                        }

                        item.hasUnreadMessages = hasUnreadMessages(item.tripId, item.lastMessageTime, item.lastMessageSenderId);
                        item.unreadCount = 0;
                        item.isOnline = false;

                        addChatItemIfNotExists(item);
                        filteredChatItems.clear();
                        filteredChatItems.addAll(chatItems);
                        adapter.notifyDataSetChanged();
                        updateEmptyState();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private String generateTripTitle(Trip trip) {
        // Use the actual trip title (stored in carModel field)
        if (trip.carModel != null && !trip.carModel.isEmpty()) {
            return trip.carModel;
        }
        // Fallback to destination if no title
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
        // Remove all listeners when view is destroyed to prevent crashes during logout
        removeListeners();
        binding = null;
    }

    // ─────────────────────────────────────────────────────────────────
    // Helper Classes
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

    private static class ChatItem {
        Trip trip;
        String tripId;
        String displayName;
        String lastMessage;
        long lastMessageTime;
        String lastMessageSenderId;
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
                b.tvName.setText(item.displayName);
                b.tvLastMessage.setText(item.lastMessage);
                b.tvTripType.setText(item.tripType);
                b.tvTime.setText(formatTimestamp(item.lastMessageTime));

                if (item.avatarUrl != null && !item.avatarUrl.isEmpty()) {
                    Glide.with(b.ivAvatar.getContext())
                            .load(item.avatarUrl)
                            .placeholder(R.drawable.ic_person)
                            .error(R.drawable.ic_person)
                            .circleCrop()
                            .into(b.ivAvatar);
                } else {
                    b.ivAvatar.setImageResource(R.drawable.ic_person);
                }

                b.onlineIndicator.setVisibility(item.isOnline ? View.VISIBLE : View.GONE);

                if (item.hasUnreadMessages) {
                    b.unreadDot.setVisibility(View.VISIBLE);
                    b.ivReadIndicator.setVisibility(View.GONE);
                    b.tvUnreadCount.setVisibility(View.GONE);
                } else {
                    b.unreadDot.setVisibility(View.GONE);
                    b.ivReadIndicator.setVisibility(View.VISIBLE);
                    b.tvUnreadCount.setVisibility(View.GONE);
                }

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

                if (now.get(Calendar.YEAR) == messageTime.get(Calendar.YEAR) &&
                    now.get(Calendar.DAY_OF_YEAR) == messageTime.get(Calendar.DAY_OF_YEAR)) {
                    return new SimpleDateFormat("h:mm a", Locale.getDefault()).format(date);
                }

                Calendar yesterday = Calendar.getInstance();
                yesterday.add(Calendar.DAY_OF_YEAR, -1);
                if (yesterday.get(Calendar.YEAR) == messageTime.get(Calendar.YEAR) &&
                    yesterday.get(Calendar.DAY_OF_YEAR) == messageTime.get(Calendar.DAY_OF_YEAR)) {
                    return "Yesterday";
                }

                if (now.get(Calendar.WEEK_OF_YEAR) == messageTime.get(Calendar.WEEK_OF_YEAR) &&
                    now.get(Calendar.YEAR) == messageTime.get(Calendar.YEAR)) {
                    return new SimpleDateFormat("EEE", Locale.getDefault()).format(date);
                }

                return new SimpleDateFormat("MMM d", Locale.getDefault()).format(date);
            }
        }
    }
}

