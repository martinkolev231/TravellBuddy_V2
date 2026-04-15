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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Query;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;
import com.travellbudy.app.databinding.ActivityNotificationsBinding;
import com.travellbudy.app.databinding.ItemNotificationBinding;
import com.travellbudy.app.models.Notification;
import com.travellbudy.app.models.SeatRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NotificationsActivity extends BaseActivity {

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

        binding.btnBack.setOnClickListener(v -> finish());

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
                        
                        // Mark as read when viewed
                        if (!notification.isRead) {
                            ds.getRef().child("isRead").setValue(true);
                        }
                    }
                }

                // Sort by createdAt descending (newest first)
                Collections.sort(notifications, (a, b) -> Long.compare(b.createdAt, a.createdAt));

                adapter.notifyDataSetChanged();
                updateEmptyState();
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

        // Find and update the request in tripRequests
        DatabaseReference tripRequestsRef = FirebaseDatabase.getInstance()
                .getReference("tripRequests")
                .child(notification.tripId);

        tripRequestsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                DataSnapshot requestSnapshot = null;
                for (DataSnapshot child : snapshot.getChildren()) {
                    String riderUid = child.child("riderUid").getValue(String.class);
                    if (notification.fromUserId.equals(riderUid)) {
                        requestSnapshot = child;
                        break;
                    }
                }

                if (requestSnapshot != null) {
                    // Get the full request data and update status
                    SeatRequest request = requestSnapshot.getValue(SeatRequest.class);
                    if (request != null) {
                        final int seatsToDeduct = request.seatsRequested > 0 ? request.seatsRequested : 1;
                        final DataSnapshot finalRequestSnapshot = requestSnapshot;
                        
                        // First, atomically decrement available seats
                        DatabaseReference seatsRef = FirebaseDatabase.getInstance()
                                .getReference("trips")
                                .child(notification.tripId)
                                .child("availableSeats");
                        
                        seatsRef.runTransaction(new Transaction.Handler() {
                            @NonNull
                            @Override
                            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                                Integer seats = currentData.getValue(Integer.class);
                                if (seats == null || seats <= 0) {
                                    return Transaction.abort();
                                }
                                currentData.setValue(seats - seatsToDeduct);
                                return Transaction.success(currentData);
                            }

                            @Override
                            public void onComplete(DatabaseError error, boolean committed, DataSnapshot snapshot) {
                                if (committed) {
                                    // Update request status
                                    request.status = "approved";
                                    request.updatedAt = System.currentTimeMillis();
                                    
                                    finalRequestSnapshot.getRef().setValue(request.toMap())
                                            .addOnSuccessListener(aVoid -> {
                                                // Add user to trip participants
                                                FirebaseDatabase.getInstance()
                                                        .getReference("trips")
                                                        .child(notification.tripId)
                                                        .child("participants")
                                                        .child(notification.fromUserId)
                                                        .setValue(true);
                                                
                                                // Add rider to tripMembers (required for My Adventures screen)
                                                com.travellbudy.app.models.TripMember member = new com.travellbudy.app.models.TripMember(
                                                        notification.fromUserId, "rider", seatsToDeduct);
                                                FirebaseDatabase.getInstance().getReference("tripMembers")
                                                        .child(notification.tripId).child(notification.fromUserId)
                                                        .setValue(member.toMap());

                                                // Add user to the group chat
                                                addUserToGroupChat(notification.tripId, notification.fromUserId);
                                                
                                                // Increment the rider's tripsAsRider counter
                                                incrementRiderTripCounter(notification.fromUserId);

                                                // Update notification status
                                                if (notification.notificationId != null) {
                                                    notificationsRef.child(notification.notificationId)
                                                            .child("status").setValue("approved");
                                                    markAsRead(notification);
                                                }

                                                // Create notification for the requester
                                                sendResponseNotification(notification, true);

                                                // Check if trip is now full
                                                Integer remaining = snapshot.getValue(Integer.class);
                                                if (remaining != null && remaining <= 0) {
                                                    FirebaseDatabase.getInstance()
                                                            .getReference("trips")
                                                            .child(notification.tripId)
                                                            .child("status")
                                                            .setValue("full");
                                                }

                                                Toast.makeText(NotificationsActivity.this, "Request approved!", Toast.LENGTH_SHORT).show();
                                            })
                                            .addOnFailureListener(e ->
                                                    Toast.makeText(NotificationsActivity.this, "Failed to approve request: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                                            );
                                } else {
                                    Toast.makeText(NotificationsActivity.this, "No available seats", Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                    }
                } else {
                    Toast.makeText(NotificationsActivity.this, "Request not found", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(NotificationsActivity.this, "Failed to approve request", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void addUserToGroupChat(String tripId, String userId) {
        DatabaseReference tripRef = FirebaseDatabase.getInstance()
                .getReference("trips")
                .child(tripId);

        tripRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;

                // Get trip details for chat entry
                String tripName = snapshot.child("carModel").getValue(String.class);
                if (tripName == null || tripName.isEmpty()) {
                    tripName = snapshot.child("destinationCity").getValue(String.class);
                }
                if (tripName == null) {
                    tripName = "Adventure";
                }

                // Add user to userChats so the group chat appears in their chat list
                java.util.Map<String, Object> userChatEntry = new java.util.HashMap<>();
                userChatEntry.put("chatId", tripId);
                userChatEntry.put("tripId", tripId);
                userChatEntry.put("otherPartyName", tripName);
                userChatEntry.put("isGroupChat", true);
                userChatEntry.put("lastMessage", "");
                userChatEntry.put("lastMessageTime", System.currentTimeMillis());

                FirebaseDatabase.getInstance()
                        .getReference("userChats")
                        .child(userId)
                        .child(tripId)
                        .setValue(userChatEntry);

                // Add user to chat participants
                FirebaseDatabase.getInstance()
                        .getReference("chats")
                        .child(tripId)
                        .child("participants")
                        .child(userId)
                        .setValue(true);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Silent fail - chat functionality is secondary
            }
        });
    }

    private void handleDecline(Notification notification) {
        if (notification.tripId == null || notification.fromUserId == null) {
            Toast.makeText(this, "Invalid request data", Toast.LENGTH_SHORT).show();
            return;
        }

        // Find and update the request in tripRequests
        DatabaseReference tripRequestsRef = FirebaseDatabase.getInstance()
                .getReference("tripRequests")
                .child(notification.tripId);

        tripRequestsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                DataSnapshot requestSnapshot = null;
                for (DataSnapshot child : snapshot.getChildren()) {
                    String riderUid = child.child("riderUid").getValue(String.class);
                    if (notification.fromUserId.equals(riderUid)) {
                        requestSnapshot = child;
                        break;
                    }
                }

                if (requestSnapshot != null) {
                    // Get the full request data and update status
                    SeatRequest request = requestSnapshot.getValue(SeatRequest.class);
                    if (request != null) {
                        request.status = "denied";
                        request.updatedAt = System.currentTimeMillis();
                        
                        requestSnapshot.getRef().setValue(request.toMap())
                                .addOnSuccessListener(aVoid -> {
                                    // Update notification status
                                    if (notification.notificationId != null) {
                                        notificationsRef.child(notification.notificationId)
                                                .child("status").setValue("denied");
                                        markAsRead(notification);
                                    }

                                    // Create notification for the requester
                                    sendResponseNotification(notification, false);

                                    Toast.makeText(NotificationsActivity.this, "Request declined", Toast.LENGTH_SHORT).show();
                                })
                                .addOnFailureListener(e ->
                                        Toast.makeText(NotificationsActivity.this, "Failed to decline request: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                                );
                    }
                } else {
                    Toast.makeText(NotificationsActivity.this, "Request not found", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(NotificationsActivity.this, "Failed to decline request", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void sendResponseNotification(Notification originalNotification, boolean approved) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;

        DatabaseReference userNotificationsRef = FirebaseDatabase.getInstance()
                .getReference("notifications")
                .child(originalNotification.fromUserId);

        String notificationId = userNotificationsRef.push().getKey();
        if (notificationId == null) return;

        // Get trip name from original notification, or use default
        String tripName = originalNotification.tripName != null && !originalNotification.tripName.isEmpty()
                ? originalNotification.tripName
                : getString(R.string.label_the_trip);

        Notification responseNotification = new Notification();
        responseNotification.type = approved ? "request_approved" : "request_denied";
        responseNotification.title = approved ? getString(R.string.notif_title_approved) : getString(R.string.notif_title_declined);
        responseNotification.message = approved 
                ? getString(R.string.notif_message_approved, tripName)
                : getString(R.string.notif_message_declined, tripName);
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
     * Increments the tripsAsRider counter for the user who just joined a trip.
     */
    private void incrementRiderTripCounter(String riderUid) {
        if (riderUid == null) return;
        
        DatabaseReference counterRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(riderUid)
                .child("tripCounters")
                .child("tripsAsRider");
        
        counterRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                Integer current = currentData.getValue(Integer.class);
                if (current == null) {
                    currentData.setValue(1);
                } else {
                    currentData.setValue(current + 1);
                }
                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot snapshot) {
                // Silent - counter update is secondary
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
                
                // Make avatar clickable to view user profile
                b.ivAvatar.setOnClickListener(v -> {
                    if (notification.fromUserId != null && !notification.fromUserId.isEmpty()) {
                        Intent intent = new Intent(NotificationsActivity.this, UserProfileActivity.class);
                        intent.putExtra(UserProfileActivity.EXTRA_USER_ID, notification.fromUserId);
                        startActivity(intent);
                    }
                });

                // Show action buttons for join requests that are pending
                boolean isJoinRequest = "join_request".equals(notification.type);
                boolean isPending = notification.status == null || "pending".equals(notification.status);
                boolean showActions = isJoinRequest && isPending;
                
                b.actionButtonsContainer.setVisibility(showActions ? View.VISIBLE : View.GONE);


                // Button click listeners
                b.btnAccept.setOnClickListener(v -> handleAccept(notification));
                b.btnDecline.setOnClickListener(v -> handleDecline(notification));

                // Click listener for the whole item
                b.getRoot().setOnClickListener(v -> openNotification(notification));
            }

            private String buildNotificationMessage(Notification notification) {
                String userName = notification.fromUserName != null ? notification.fromUserName : getString(R.string.label_someone);
                String tripName = notification.tripName != null ? notification.tripName : getString(R.string.label_a_trip);
                
                if (notification.type == null) {
                    return notification.message != null ? notification.message : getString(R.string.notif_new_notification);
                }
                
                switch (notification.type) {
                    case "join_request":
                        return getString(R.string.notif_join_request, userName, tripName);
                    case "request_approved":
                        return getString(R.string.notif_message_approved, tripName);
                    case "request_denied":
                        return getString(R.string.notif_message_declined, tripName);
                    case "new_message":
                        return getString(R.string.notif_new_message, userName);
                    case "trip_update":
                        return getString(R.string.notif_trip_update, tripName);
                    default:
                        return notification.message != null ? notification.message : getString(R.string.notif_new_notification);
                }
            }
        }
    }
}

