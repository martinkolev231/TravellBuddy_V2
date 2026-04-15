package com.travellbudy.app.repository;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;
import com.travellbudy.app.models.Announcement;
import com.travellbudy.app.models.Category;
import com.travellbudy.app.models.Trip;
import com.travellbudy.app.models.User;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Repository for admin operations.
 * All operations here require admin privileges.
 */
public class AdminRepository {

    private static AdminRepository instance;
    private final DatabaseReference dbRef;
    private final FirebaseAuth mAuth;

    // Database paths
    private static final String PATH_USERS = "users";
    private static final String PATH_TRIPS = "trips";
    private static final String PATH_ADMINS = "admins";
    private static final String PATH_ANNOUNCEMENTS = "announcements";
    private static final String PATH_CATEGORIES = "categories";
    private static final String PATH_NOTIFICATIONS_SEND = "notifications/send";
    private static final String PATH_FEATURED_TRIPS = "featuredTrips";

    private AdminRepository() {
        dbRef = FirebaseDatabase.getInstance().getReference();
        mAuth = FirebaseAuth.getInstance();
    }

    public static synchronized AdminRepository getInstance() {
        if (instance == null) {
            instance = new AdminRepository();
        }
        return instance;
    }

    // =========================================================================
    // Admin verification
    // =========================================================================

    /**
     * Checks if the current user is an admin.
     * Checks both the user's role field and the /admins whitelist.
     */
    public LiveData<Boolean> isCurrentUserAdmin() {
        MutableLiveData<Boolean> result = new MutableLiveData<>();
        String uid = getCurrentUid();

        if (uid == null) {
            result.setValue(false);
            return result;
        }

        // Check both /users/{uid}/role and /admins/{uid}
        dbRef.child(PATH_USERS).child(uid).child("role")
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    String role = snapshot.getValue(String.class);
                    if (User.ROLE_ADMIN.equals(role)) {
                        result.setValue(true);
                    } else {
                        // Also check admins whitelist
                        checkAdminsWhitelist(uid, result);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    result.setValue(false);
                }
            });

        return result;
    }

    private void checkAdminsWhitelist(String uid, MutableLiveData<Boolean> result) {
        dbRef.child(PATH_ADMINS).child(uid)
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    Boolean isAdmin = snapshot.getValue(Boolean.class);
                    result.setValue(isAdmin != null && isAdmin);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    result.setValue(false);
                }
            });
    }

    // =========================================================================
    // User Management
    // =========================================================================

    /**
     * Gets all registered users.
     */
    public LiveData<List<User>> getAllUsers() {
        MutableLiveData<List<User>> result = new MutableLiveData<>();

        dbRef.child(PATH_USERS)
            .orderByChild("createdAt")
            .addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    List<User> users = new ArrayList<>();
                    for (DataSnapshot child : snapshot.getChildren()) {
                        User user = child.getValue(User.class);
                        if (user == null) {
                            continue;
                        }

                        // Ensure uid is always set (some records may not store it in the node)
                        if (user.uid == null || user.uid.isEmpty()) {
                            user.uid = child.getKey();
                        }

                        // Normalize avatar url across legacy schemas
                        String photoUrl = user.photoUrl;
                        if (photoUrl == null || photoUrl.isEmpty()) {
                            photoUrl = child.child("photoUrl").getValue(String.class);
                        }
                        if (photoUrl == null || photoUrl.isEmpty()) {
                            photoUrl = child.child("photoURL").getValue(String.class);
                        }
                        if (photoUrl == null || photoUrl.isEmpty()) {
                            photoUrl = child.child("profilePhotoUrl").getValue(String.class);
                        }
                        if (photoUrl != null && !photoUrl.isEmpty()) {
                            user.photoUrl = photoUrl;
                        }

                        users.add(user);
                    }
                    result.setValue(users);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    result.setValue(new ArrayList<>());
                }
            });

        return result;
    }

    /**
     * Bans a user by setting isBanned = true.
     */
    public Task<Void> banUser(String uid) {
        return dbRef.child(PATH_USERS).child(uid).child("isBanned").setValue(true);
    }

    /**
     * Unbans a user by setting isBanned = false.
     */
    public Task<Void> unbanUser(String uid) {
        return dbRef.child(PATH_USERS).child(uid).child("isBanned").setValue(false);
    }

    /**
     * Promotes a user to admin.
     * Updates both /users/{uid}/role and /admins/{uid}.
     */
    public Task<Void> promoteToAdmin(String uid) {
        Map<String, Object> updates = new HashMap<>();
        updates.put(PATH_USERS + "/" + uid + "/role", User.ROLE_ADMIN);
        updates.put(PATH_ADMINS + "/" + uid, true);
        return dbRef.updateChildren(updates);
    }

    /**
     * Demotes an admin to regular user.
     * Updates both /users/{uid}/role and removes from /admins.
     */
    public Task<Void> demoteToUser(String uid) {
        Map<String, Object> updates = new HashMap<>();
        updates.put(PATH_USERS + "/" + uid + "/role", User.ROLE_USER);
        updates.put(PATH_ADMINS + "/" + uid, null); // Remove from admins whitelist
        return dbRef.updateChildren(updates);
    }

    /**
     * Deletes a user and all their data.
     */
    public Task<Void> deleteUser(String uid) {
        Map<String, Object> updates = new HashMap<>();
        updates.put(PATH_USERS + "/" + uid, null);
        updates.put(PATH_ADMINS + "/" + uid, null);
        // Note: Trips, messages, etc. would need separate cleanup
        // Consider using a Cloud Function for complete cleanup
        return dbRef.updateChildren(updates);
    }

    /**
     * Gets users registered in the last N days.
     */
    public LiveData<List<User>> getRecentUsers(int days) {
        MutableLiveData<List<User>> result = new MutableLiveData<>();

        long cutoffTime = System.currentTimeMillis() - (days * 24L * 60L * 60L * 1000L);

        dbRef.child(PATH_USERS)
            .orderByChild("createdAt")
            .startAt(cutoffTime)
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    List<User> users = new ArrayList<>();
                    for (DataSnapshot child : snapshot.getChildren()) {
                        User user = child.getValue(User.class);
                        if (user != null) {
                            users.add(user);
                        }
                    }
                    result.setValue(users);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    result.setValue(new ArrayList<>());
                }
            });

        return result;
    }

    // =========================================================================
    // Content Moderation
    // =========================================================================

    /**
     * Gets all trips across the app.
     */
    public LiveData<List<Trip>> getAllTrips() {
        MutableLiveData<List<Trip>> result = new MutableLiveData<>();

        dbRef.child(PATH_TRIPS)
            .orderByChild("createdAt")
            .addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    List<Trip> trips = new ArrayList<>();
                    for (DataSnapshot child : snapshot.getChildren()) {
                        Trip trip = child.getValue(Trip.class);
                        if (trip != null) {
                            trips.add(trip);
                        }
                    }
                    result.setValue(trips);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    result.setValue(new ArrayList<>());
                }
            });

        return result;
    }

    /**
     * Deletes a trip (admin override - bypasses owner check).
     */
    public Task<Void> deleteTrip(String tripId) {
        Map<String, Object> updates = new HashMap<>();
        updates.put(PATH_TRIPS + "/" + tripId, null);
        updates.put(PATH_FEATURED_TRIPS + "/" + tripId, null);
        // Could also clean up related data: tripRequests, tripMembers, etc.
        return dbRef.updateChildren(updates);
    }

    /**
     * Features a trip on the home feed.
     */
    public Task<Void> featureTrip(String tripId) {
        Map<String, Object> data = new HashMap<>();
        data.put("tripId", tripId);
        data.put("featuredAt", ServerValue.TIMESTAMP);
        data.put("featuredByUid", getCurrentUid());
        return dbRef.child(PATH_FEATURED_TRIPS).child(tripId).setValue(data);
    }

    /**
     * Unfeatures a trip.
     */
    public Task<Void> unfeatureTrip(String tripId) {
        return dbRef.child(PATH_FEATURED_TRIPS).child(tripId).removeValue();
    }

    /**
     * Gets all featured trips.
     */
    public LiveData<List<String>> getFeaturedTripIds() {
        MutableLiveData<List<String>> result = new MutableLiveData<>();

        dbRef.child(PATH_FEATURED_TRIPS)
            .addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    List<String> tripIds = new ArrayList<>();
                    for (DataSnapshot child : snapshot.getChildren()) {
                        tripIds.add(child.getKey());
                    }
                    result.setValue(tripIds);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    result.setValue(new ArrayList<>());
                }
            });

        return result;
    }

    // =========================================================================
    // Category Management
    // =========================================================================

    /**
     * Gets all categories.
     */
    public LiveData<List<Category>> getAllCategories() {
        MutableLiveData<List<Category>> result = new MutableLiveData<>();

        dbRef.child(PATH_CATEGORIES)
            .orderByChild("order")
            .addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    List<Category> categories = new ArrayList<>();
                    for (DataSnapshot child : snapshot.getChildren()) {
                        Category category = child.getValue(Category.class);
                        if (category != null) {
                            categories.add(category);
                        }
                    }
                    result.setValue(categories);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    result.setValue(new ArrayList<>());
                }
            });

        return result;
    }

    /**
     * Creates a new category.
     */
    public Task<Void> createCategory(Category category) {
        String key = dbRef.child(PATH_CATEGORIES).push().getKey();
        if (key == null) {
            return Tasks.forException(new Exception("Failed to generate key"));
        }
        category.categoryId = key;
        category.createdByUid = getCurrentUid();
        return dbRef.child(PATH_CATEGORIES).child(key).setValue(category.toMap());
    }

    /**
     * Updates a category.
     */
    public Task<Void> updateCategory(Category category) {
        return dbRef.child(PATH_CATEGORIES).child(category.categoryId)
            .setValue(category.toMap());
    }

    /**
     * Deletes a category.
     */
    public Task<Void> deleteCategory(String categoryId) {
        return dbRef.child(PATH_CATEGORIES).child(categoryId).removeValue();
    }

    // =========================================================================
    // Announcements
    // =========================================================================

    /**
     * Gets all announcements.
     */
    public LiveData<List<Announcement>> getAllAnnouncements() {
        MutableLiveData<List<Announcement>> result = new MutableLiveData<>();

        dbRef.child(PATH_ANNOUNCEMENTS)
            .orderByChild("createdAt")
            .addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    List<Announcement> announcements = new ArrayList<>();
                    for (DataSnapshot child : snapshot.getChildren()) {
                        Announcement announcement = child.getValue(Announcement.class);
                        if (announcement != null) {
                            announcements.add(announcement);
                        }
                    }
                    result.setValue(announcements);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    result.setValue(new ArrayList<>());
                }
            });

        return result;
    }

    /**
     * Gets active announcements (for display to users).
     */
    public LiveData<List<Announcement>> getActiveAnnouncements() {
        MutableLiveData<List<Announcement>> result = new MutableLiveData<>();

        dbRef.child(PATH_ANNOUNCEMENTS)
            .orderByChild("isActive")
            .equalTo(true)
            .addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    List<Announcement> announcements = new ArrayList<>();
                    for (DataSnapshot child : snapshot.getChildren()) {
                        Announcement announcement = child.getValue(Announcement.class);
                        if (announcement != null && announcement.shouldShow()) {
                            announcements.add(announcement);
                        }
                    }
                    result.setValue(announcements);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    result.setValue(new ArrayList<>());
                }
            });

        return result;
    }

    /**
     * Creates a new announcement.
     */
    public Task<Void> createAnnouncement(String title, String message, String type, long expiresAt) {
        String key = dbRef.child(PATH_ANNOUNCEMENTS).push().getKey();
        if (key == null) {
            return Tasks.forException(new Exception("Failed to generate key"));
        }

        String uid = getCurrentUid();
        Announcement announcement = new Announcement(key, title, message, uid, "Admin");
        announcement.type = type;
        announcement.expiresAt = expiresAt;

        return dbRef.child(PATH_ANNOUNCEMENTS).child(key).setValue(announcement.toMap());
    }

    /**
     * Updates an announcement.
     */
    public Task<Void> updateAnnouncement(Announcement announcement) {
        return dbRef.child(PATH_ANNOUNCEMENTS).child(announcement.announcementId)
            .setValue(announcement.toMap());
    }

    /**
     * Deactivates an announcement.
     */
    public Task<Void> deactivateAnnouncement(String announcementId) {
        return dbRef.child(PATH_ANNOUNCEMENTS).child(announcementId)
            .child("isActive").setValue(false);
    }

    /**
     * Deletes an announcement.
     */
    public Task<Void> deleteAnnouncement(String announcementId) {
        return dbRef.child(PATH_ANNOUNCEMENTS).child(announcementId).removeValue();
    }

    // =========================================================================
    // Push Notifications
    // =========================================================================

    /**
     * Sends a push notification to all users.
     * This writes to /notifications/send which triggers a Cloud Function.
     */
    public Task<Void> sendPushNotificationToAll(String title, String message) {
        String key = dbRef.child(PATH_NOTIFICATIONS_SEND).push().getKey();
        if (key == null) {
            return Tasks.forException(new Exception("Failed to generate key"));
        }

        Map<String, Object> notification = new HashMap<>();
        notification.put("id", key);
        notification.put("title", title);
        notification.put("message", message);
        notification.put("target", "all"); // All users
        notification.put("sentByUid", getCurrentUid());
        notification.put("createdAt", ServerValue.TIMESTAMP);
        notification.put("processed", false);

        return dbRef.child(PATH_NOTIFICATIONS_SEND).child(key).setValue(notification);
    }

    // =========================================================================
    // Dashboard Stats
    // =========================================================================

    private MutableLiveData<DashboardStats> dashboardStatsLiveData;
    private final DashboardStats currentStats = new DashboardStats();
    private ValueEventListener usersListener;
    private ValueEventListener tripsListener;
    private ValueEventListener newUsersListener;
    private ValueEventListener bannedUsersListener;
    private ValueEventListener adminsListener;

    /**
     * Gets dashboard statistics with real-time updates.
     */
    public LiveData<DashboardStats> getDashboardStats() {
        if (dashboardStatsLiveData == null) {
            dashboardStatsLiveData = new MutableLiveData<>();
            setupDashboardListeners();
        }
        return dashboardStatsLiveData;
    }

    private void setupDashboardListeners() {
        // Remove old listeners if any
        removeDashboardListeners();

        // Count total users - real-time listener
        usersListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                currentStats.totalUsers = (int) snapshot.getChildrenCount();
                postDashboardStats();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                currentStats.totalUsers = 0;
                postDashboardStats();
            }
        };
        dbRef.child(PATH_USERS).addValueEventListener(usersListener);

        // Count total trips - real-time listener
        tripsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                currentStats.totalTrips = (int) snapshot.getChildrenCount();
                postDashboardStats();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                currentStats.totalTrips = 0;
                postDashboardStats();
            }
        };
        dbRef.child(PATH_TRIPS).addValueEventListener(tripsListener);

        // Count new users in last 7 days - real-time listener
        long sevenDaysAgo = System.currentTimeMillis() - (7 * 24L * 60L * 60L * 1000L);
        newUsersListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                currentStats.newUsersLast7Days = (int) snapshot.getChildrenCount();
                postDashboardStats();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                currentStats.newUsersLast7Days = 0;
                postDashboardStats();
            }
        };
        dbRef.child(PATH_USERS)
            .orderByChild("createdAt")
            .startAt(sevenDaysAgo)
            .addValueEventListener(newUsersListener);

        // Count banned users - real-time listener
        bannedUsersListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                currentStats.bannedUsers = (int) snapshot.getChildrenCount();
                postDashboardStats();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                currentStats.bannedUsers = 0;
                postDashboardStats();
            }
        };
        dbRef.child(PATH_USERS)
            .orderByChild("isBanned")
            .equalTo(true)
            .addValueEventListener(bannedUsersListener);

        // Count admin users - real-time listener
        adminsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                currentStats.adminCount = (int) snapshot.getChildrenCount();
                postDashboardStats();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                currentStats.adminCount = 0;
                postDashboardStats();
            }
        };
        dbRef.child(PATH_ADMINS).addValueEventListener(adminsListener);
    }

    private void postDashboardStats() {
        // Create a copy to avoid mutation issues
        DashboardStats statsCopy = new DashboardStats();
        statsCopy.totalUsers = currentStats.totalUsers;
        statsCopy.totalTrips = currentStats.totalTrips;
        statsCopy.newUsersLast7Days = currentStats.newUsersLast7Days;
        statsCopy.bannedUsers = currentStats.bannedUsers;
        statsCopy.adminCount = currentStats.adminCount;
        dashboardStatsLiveData.postValue(statsCopy);
    }

    private void removeDashboardListeners() {
        if (usersListener != null) {
            dbRef.child(PATH_USERS).removeEventListener(usersListener);
        }
        if (tripsListener != null) {
            dbRef.child(PATH_TRIPS).removeEventListener(tripsListener);
        }
        if (newUsersListener != null) {
            dbRef.child(PATH_USERS).removeEventListener(newUsersListener);
        }
        if (bannedUsersListener != null) {
            dbRef.child(PATH_USERS).removeEventListener(bannedUsersListener);
        }
        if (adminsListener != null) {
            dbRef.child(PATH_ADMINS).removeEventListener(adminsListener);
        }
    }

    /**
     * Force refresh dashboard stats by resetting listeners.
     */
    public void refreshDashboardStats() {
        if (dashboardStatsLiveData != null) {
            setupDashboardListeners();
        }
    }

    // =========================================================================
    // Helper
    // =========================================================================

    private String getCurrentUid() {
        return mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;
    }

    // =========================================================================
    // Dashboard Stats Model
    // =========================================================================

    public static class DashboardStats {
        public int totalUsers;
        public int totalTrips;
        public int newUsersLast7Days;
        public int bannedUsers;
        public int adminCount;
        int loadedCount = 0; // Internal counter
    }
}

