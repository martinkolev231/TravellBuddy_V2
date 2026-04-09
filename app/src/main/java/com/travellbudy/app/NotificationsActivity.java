package com.travellbudy.app;

import android.content.Intent;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.format.DateUtils;
import android.text.style.StyleSpan;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.travellbudy.app.databinding.ActivityNotificationsBinding;
import com.travellbudy.app.databinding.ItemNotificationBinding;
import com.travellbudy.app.models.Notification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NotificationsActivity extends AppCompatActivity {

    private ActivityNotificationsBinding binding;
    private NotificationsAdapter adapter;
    private DatabaseReference notificationsRef;
    private ValueEventListener listener;
    private final List<Notification> notifications = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityNotificationsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            finish();
            return;
        }

        notificationsRef = FirebaseDatabase.getInstance()
                .getReference("notifications")
                .child(currentUser.getUid());

        adapter = new NotificationsAdapter();
        binding.rvNotifications.setLayoutManager(new LinearLayoutManager(this));
        binding.rvNotifications.setAdapter(adapter);

        loadNotifications();
    }

    private void loadNotifications() {
        binding.progressBar.setVisibility(View.VISIBLE);

        Query query = notificationsRef.orderByChild("createdAt").limitToLast(50);
        listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                binding.progressBar.setVisibility(View.GONE);
                notifications.clear();

                for (DataSnapshot ds : snapshot.getChildren()) {
                    Notification notification = ds.getValue(Notification.class);
                    if (notification != null) {
                        notification.notificationId = ds.getKey();
                        notifications.add(notification);
                    }
                }

                // Sort by createdAt descending (newest first)
                Collections.sort(notifications, (a, b) -> Long.compare(b.createdAt, a.createdAt));

                adapter.notifyDataSetChanged();
                updateEmptyState();
                
                // Mark all notifications as read when user views them
                markAllAsRead();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                binding.progressBar.setVisibility(View.GONE);
                updateEmptyState();
            }
        };
        query.addValueEventListener(listener);
    }

    private void updateEmptyState() {
        boolean isEmpty = notifications.isEmpty();
        binding.emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        binding.rvNotifications.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    private void markAllAsRead() {
        for (Notification notification : notifications) {
            if (!notification.isRead && notification.notificationId != null) {
                notificationsRef.child(notification.notificationId)
                        .child("isRead").setValue(true);
            }
        }
    }

    private void markAsRead(Notification notification) {
        if (!notification.isRead && notification.notificationId != null) {
            notificationsRef.child(notification.notificationId)
                    .child("isRead").setValue(true);
        }
    }

    private void handleAccept(Notification notification) {
        if (notification.tripId == null || notification.fromUserId == null) {
            Toast.makeText(this, "Invalid request data", Toast.LENGTH_SHORT).show();
            return;
        }

        // Update notification status first (this always exists)
        if (notification.notificationId != null) {
            notificationsRef.child(notification.notificationId)
                    .child("status").setValue("approved")
                    .addOnSuccessListener(aVoid -> {
                        markAsRead(notification);
                        
                        // Add user to trip participants
                        DatabaseReference tripRef = FirebaseDatabase.getInstance()
                                .getReference("trips")
                                .child(notification.tripId);
                        
                        tripRef.child("participants")
                                .child(notification.fromUserId)
                                .setValue(true);

                        // Decrement availableSeats
                        tripRef.child("availableSeats").get().addOnSuccessListener(snapshot -> {
                            Integer currentSeats = snapshot.getValue(Integer.class);
                            if (currentSeats != null && currentSeats > 0) {
                                tripRef.child("availableSeats").setValue(currentSeats - 1);
                            }
                        });

                        // Add user to group chat
                        addUserToGroupChat(notification.tripId, notification.fromUserId, notification.fromUserName, notification.tripName);

                        // Update tripRequests status (used by TripDetailsActivity)
                        DatabaseReference tripRequestsRef = FirebaseDatabase.getInstance()
                                .getReference("tripRequests")
                                .child(notification.tripId);
                        // Iterate through all requests and find matching riderUid
                        tripRequestsRef.addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot snapshot) {
                                for (DataSnapshot child : snapshot.getChildren()) {
                                    String riderUid = child.child("riderUid").getValue(String.class);
                                    if (notification.fromUserId.equals(riderUid)) {
                                        // Update status and updatedAt together
                                        java.util.Map<String, Object> updates = new java.util.HashMap<>();
                                        updates.put("status", "approved");
                                        updates.put("updatedAt", System.currentTimeMillis());
                                        child.getRef().updateChildren(updates);
                                    }
                                }
                            }
                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {}
                        });

                        // Also update tripJoinRequests if it exists
                        DatabaseReference requestRef = FirebaseDatabase.getInstance()
                                .getReference("tripJoinRequests")
                                .child(notification.tripId)
                                .child(notification.fromUserId);
                        requestRef.child("status").setValue("approved");

                        // Create notification for the requester
                        sendResponseNotification(notification, true);

                        Toast.makeText(this, "Request approved!", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> 
                        Toast.makeText(this, "Failed to approve request", Toast.LENGTH_SHORT).show()
                    );
        }
    }

    private void handleDecline(Notification notification) {
        if (notification.tripId == null || notification.fromUserId == null) {
            Toast.makeText(this, "Invalid request data", Toast.LENGTH_SHORT).show();
            return;
        }

        // Update notification status first (this always exists)
        if (notification.notificationId != null) {
            notificationsRef.child(notification.notificationId)
                    .child("status").setValue("denied")
                    .addOnSuccessListener(aVoid -> {
                        markAsRead(notification);

                        // Update tripRequests status (used by TripDetailsActivity)
                        DatabaseReference tripRequestsRef = FirebaseDatabase.getInstance()
                                .getReference("tripRequests")
                                .child(notification.tripId);
                        // Iterate through all requests and find matching riderUid
                        tripRequestsRef.addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot snapshot) {
                                for (DataSnapshot child : snapshot.getChildren()) {
                                    String riderUid = child.child("riderUid").getValue(String.class);
                                    if (notification.fromUserId.equals(riderUid)) {
                                        // Update status and updatedAt together
                                        java.util.Map<String, Object> updates = new java.util.HashMap<>();
                                        updates.put("status", "denied");
                                        updates.put("updatedAt", System.currentTimeMillis());
                                        child.getRef().updateChildren(updates);
                                    }
                                }
                            }
                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {}
                        });

                        // Also update tripJoinRequests if it exists
                        DatabaseReference requestRef = FirebaseDatabase.getInstance()
                                .getReference("tripJoinRequests")
                                .child(notification.tripId)
                                .child(notification.fromUserId);
                        requestRef.child("status").setValue("denied");

                        // Create notification for the requester
                        sendResponseNotification(notification, false);

                        Toast.makeText(this, "Request declined", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> 
                        Toast.makeText(this, "Failed to decline request", Toast.LENGTH_SHORT).show()
                    );
        }
    }

    private void sendResponseNotification(Notification originalNotification, boolean approved) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;

        DatabaseReference userNotificationsRef = FirebaseDatabase.getInstance()
                .getReference("notifications")
                .child(originalNotification.fromUserId);

        String notificationId = userNotificationsRef.push().getKey();
        if (notificationId == null) return;

        // Get the trip name for the message
        String tripName = originalNotification.tripName != null && !originalNotification.tripName.isEmpty()
                ? originalNotification.tripName
                : "the trip";

        Notification responseNotification = new Notification();
        responseNotification.type = approved ? "request_approved" : "request_denied";
        responseNotification.title = approved ? "Request Approved!" : "Request Declined";
        responseNotification.message = approved 
                ? "Your request to join " + tripName + " has been approved!"
                : "Your request to join " + tripName + " has been declined.";
        responseNotification.tripId = originalNotification.tripId;
        responseNotification.tripName = originalNotification.tripName;
        responseNotification.fromUserId = currentUser.getUid();
        responseNotification.fromUserName = currentUser.getDisplayName();
        responseNotification.fromUserPhoto = currentUser.getPhotoUrl() != null 
                ? currentUser.getPhotoUrl().toString() : null;
        responseNotification.createdAt = System.currentTimeMillis();
        responseNotification.isRead = false;

        userNotificationsRef.child(notificationId).setValue(responseNotification);
    }

    /**
     * Adds a user to the group chat when their join request is approved.
     */
    private void addUserToGroupChat(String tripId, String userId, String userName, String tripName) {
        if (tripId == null || userId == null) return;

        DatabaseReference chatRef = FirebaseDatabase.getInstance()
                .getReference("chats")
                .child(tripId);
        
        // Add user to chat participants
        chatRef.child("info").child("participants").child(userId).setValue(true);
        
        // Get the chat name from trip if not provided
        String chatName = tripName != null ? tripName : "Adventure Chat";
        
        // Fetch the last message from the chat to show in userChats
        chatRef.child("messages").orderByChild("timestamp").limitToLast(1)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String lastMessage = "";
                        long lastMessageTime = System.currentTimeMillis();
                        String lastMessageSenderId = "";
                        
                        for (DataSnapshot msgSnapshot : snapshot.getChildren()) {
                            String msgText = msgSnapshot.child("text").getValue(String.class);
                            Long msgTime = msgSnapshot.child("timestamp").getValue(Long.class);
                            String senderId = msgSnapshot.child("senderId").getValue(String.class);
                            
                            if (msgText != null) {
                                lastMessage = msgText;
                            }
                            if (msgTime != null) {
                                lastMessageTime = msgTime;
                            }
                            if (senderId != null) {
                                lastMessageSenderId = senderId;
                            }
                        }
                        
                        // Add chat to user's userChats list with actual last message data
                        java.util.Map<String, Object> userChatEntry = new java.util.HashMap<>();
                        userChatEntry.put("chatId", tripId);
                        userChatEntry.put("tripId", tripId);
                        userChatEntry.put("otherPartyName", chatName);
                        userChatEntry.put("isGroupChat", true);
                        userChatEntry.put("lastMessage", lastMessage);
                        userChatEntry.put("lastMessageTime", lastMessageTime);
                        userChatEntry.put("lastMessageSenderId", lastMessageSenderId);
                        
                        FirebaseDatabase.getInstance()
                                .getReference("userChats")
                                .child(userId)
                                .child(tripId)
                                .setValue(userChatEntry);
                    }
                    
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        // Fallback: create entry with empty message if fetch fails
                        java.util.Map<String, Object> userChatEntry = new java.util.HashMap<>();
                        userChatEntry.put("chatId", tripId);
                        userChatEntry.put("tripId", tripId);
                        userChatEntry.put("otherPartyName", chatName);
                        userChatEntry.put("isGroupChat", true);
                        userChatEntry.put("lastMessage", "");
                        userChatEntry.put("lastMessageTime", System.currentTimeMillis());
                        userChatEntry.put("lastMessageSenderId", "");
                        
                        FirebaseDatabase.getInstance()
                                .getReference("userChats")
                                .child(userId)
                                .child(tripId)
                                .setValue(userChatEntry);
                    }
                });
    }

    private void openNotification(Notification notification) {
        markAsRead(notification);

        if (notification.tripId != null && !notification.tripId.isEmpty()) {
            Intent intent = new Intent(this, TripDetailsActivity.class);
            intent.putExtra(TripDetailsActivity.EXTRA_TRIP_ID, notification.tripId);
            startActivity(intent);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (listener != null && notificationsRef != null) {
            notificationsRef.removeEventListener(listener);
        }
    }

    private class NotificationsAdapter extends RecyclerView.Adapter<NotificationsAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemNotificationBinding itemBinding = ItemNotificationBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new VH(itemBinding);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            holder.bind(notifications.get(position));
        }

        @Override
        public int getItemCount() {
            return notifications.size();
        }

        class VH extends RecyclerView.ViewHolder {
            private final ItemNotificationBinding b;

            VH(ItemNotificationBinding binding) {
                super(binding.getRoot());
                this.b = binding;
            }

            void bind(Notification notification) {
                // Build message with bold user name
                String message = buildNotificationMessage(notification);
                SpannableString spannableMessage = new SpannableString(message);
                
                // Make the user name bold if present
                String userName = notification.fromUserName != null ? notification.fromUserName : "";
                if (!userName.isEmpty() && message.contains(userName)) {
                    int startIndex = message.indexOf(userName);
                    int endIndex = startIndex + userName.length();
                    spannableMessage.setSpan(new StyleSpan(Typeface.BOLD), startIndex, endIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                
                b.tvMessage.setText(spannableMessage);

                // Time ago
                if (notification.createdAt > 0) {
                    CharSequence timeAgo = DateUtils.getRelativeTimeSpanString(
                            notification.createdAt,
                            System.currentTimeMillis(),
                            DateUtils.MINUTE_IN_MILLIS,
                            DateUtils.FORMAT_ABBREV_RELATIVE);
                    b.tvTime.setText(timeAgo);
                } else {
                    b.tvTime.setText("");
                }

                // Unread indicator
                b.unreadIndicator.setVisibility(notification.isRead ? View.GONE : View.VISIBLE);

                // Avatar
                if (notification.fromUserPhoto != null && !notification.fromUserPhoto.isEmpty()) {
                    Glide.with(b.ivAvatar.getContext())
                            .load(notification.fromUserPhoto)
                            .placeholder(R.drawable.bg_profile_placeholder)
                            .error(R.drawable.bg_profile_placeholder)
                            .circleCrop()
                            .into(b.ivAvatar);
                } else {
                    b.ivAvatar.setImageResource(R.drawable.bg_profile_placeholder);
                }

                // Show action buttons for join requests that are pending
                boolean isJoinRequest = "join_request".equals(notification.type);
                boolean isPending = notification.status == null || "pending".equals(notification.status);
                boolean showActions = isJoinRequest && isPending;
                
                b.actionButtonsContainer.setVisibility(showActions ? View.VISIBLE : View.GONE);
                
                // Show icon badge only for join requests
                b.iconBadgeContainer.setVisibility(isJoinRequest ? View.VISIBLE : View.GONE);

                // Type icon
                int iconRes = getIconForType(notification.type);
                b.ivTypeIcon.setImageResource(iconRes);

                // Button click listeners
                b.btnAccept.setOnClickListener(v -> handleAccept(notification));
                b.btnDecline.setOnClickListener(v -> handleDecline(notification));

                // Avatar click - open user profile
                b.ivAvatar.setOnClickListener(v -> {
                    if (notification.fromUserId != null && !notification.fromUserId.isEmpty()) {
                        Intent intent = new Intent(NotificationsActivity.this, UserProfileActivity.class);
                        intent.putExtra(UserProfileActivity.EXTRA_USER_ID, notification.fromUserId);
                        startActivity(intent);
                    }
                });

                // Click listener for the whole item
                b.getRoot().setOnClickListener(v -> openNotification(notification));
            }

            private String buildNotificationMessage(Notification notification) {
                String userName = notification.fromUserName != null ? notification.fromUserName : "Someone";
                String tripName = notification.tripName != null ? notification.tripName : "a trip";
                
                if (notification.type == null) {
                    return notification.message != null ? notification.message : "New notification";
                }
                
                switch (notification.type) {
                    case "join_request":
                        return userName + " wants to join your " + tripName + " adventure.";
                    case "request_approved":
                        return "Your request to join " + tripName + " has been approved!";
                    case "request_denied":
                        return "Your request to join " + tripName + " has been declined.";
                    case "new_message":
                        return userName + " sent you a message.";
                    case "trip_update":
                        return tripName + " has been updated.";
                    default:
                        return notification.message != null ? notification.message : "New notification";
                }
            }

            private int getIconForType(String type) {
                if (type == null) return R.drawable.ic_notification;
                switch (type) {
                    case "join_request":
                        return R.drawable.ic_person_add;
                    case "request_approved":
                        return R.drawable.ic_check_circle;
                    case "request_denied":
                        return R.drawable.ic_close;
                    case "new_message":
                        return R.drawable.ic_chat;
                    case "trip_update":
                        return R.drawable.ic_edit_pencil;
                    default:
                        return R.drawable.ic_notification;
                }
            }
        }
    }
}

