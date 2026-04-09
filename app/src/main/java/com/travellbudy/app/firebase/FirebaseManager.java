package com.travellbudy.app.firebase;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.travellbudy.app.util.Constants;

/**
 * Thread-safe singleton that provides access to Firebase Auth and
 * Realtime Database instances. All repositories use this class
 * instead of calling Firebase SDK directly.
 *
 * <p>Offline persistence must be enabled in {@code TravellBuddyApp.onCreate()}
 * BEFORE any database reference is created.
 */
public final class FirebaseManager {

    private static volatile FirebaseManager instance;

    private final FirebaseAuth auth;
    private final FirebaseDatabase database;

    private FirebaseManager() {
        this.auth = FirebaseAuth.getInstance();
        this.database = FirebaseDatabase.getInstance();
    }

    @NonNull
    public static FirebaseManager getInstance() {
        if (instance == null) {
            synchronized (FirebaseManager.class) {
                if (instance == null) {
                    instance = new FirebaseManager();
                }
            }
        }
        return instance;
    }

    // =========================================================================
    // Auth
    // =========================================================================

    @NonNull
    public FirebaseAuth getAuth() {
        return auth;
    }

    /**
     * @return The currently signed-in user, or null if not authenticated.
     */
    public FirebaseUser getCurrentUser() {
        return auth.getCurrentUser();
    }

    /**
     * @return The UID of the current user, or null if not authenticated.
     */
    public String getCurrentUid() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    /**
     * @return true if a user is currently signed in.
     */
    public boolean isAuthenticated() {
        return auth.getCurrentUser() != null;
    }

    // =========================================================================
    // Database references
    // =========================================================================

    @NonNull
    public FirebaseDatabase getDatabase() {
        return database;
    }

    /**
     * Returns a DatabaseReference for the given path.
     * Usage: {@code getRef(Constants.PATH_TRIPS)}
     */
    @NonNull
    public DatabaseReference getRef(@NonNull String path) {
        return database.getReference(path);
    }

    /** /users */
    @NonNull
    public DatabaseReference getUsersRef() {
        return database.getReference(Constants.PATH_USERS);
    }

    /** /users/{uid} */
    @NonNull
    public DatabaseReference getUserRef(@NonNull String uid) {
        return database.getReference(Constants.PATH_USERS).child(uid);
    }

    /** /trips */
    @NonNull
    public DatabaseReference getTripsRef() {
        return database.getReference(Constants.PATH_TRIPS);
    }

    /** /trips/{tripId} */
    @NonNull
    public DatabaseReference getTripRef(@NonNull String tripId) {
        return database.getReference(Constants.PATH_TRIPS).child(tripId);
    }

    /** /tripRequests/{tripId} */
    @NonNull
    public DatabaseReference getRequestsRef(@NonNull String tripId) {
        return database.getReference(Constants.PATH_TRIP_REQUESTS).child(tripId);
    }

    /** /tripMembers/{tripId} */
    @NonNull
    public DatabaseReference getMembersRef(@NonNull String tripId) {
        return database.getReference(Constants.PATH_TRIP_MEMBERS).child(tripId);
    }

    /** /chats/{chatId} */
    @NonNull
    public DatabaseReference getChatRef(@NonNull String chatId) {
        return database.getReference(Constants.PATH_CHATS).child(chatId);
    }

    /** /chats/{chatId}/messages */
    @NonNull
    public DatabaseReference getMessagesRef(@NonNull String chatId) {
        return database.getReference(Constants.PATH_CHATS).child(chatId)
                .child(Constants.PATH_MESSAGES);
    }

    /** /userChats/{uid} */
    @NonNull
    public DatabaseReference getUserChatsRef(@NonNull String uid) {
        return database.getReference(Constants.PATH_USER_CHATS).child(uid);
    }

    /** /ratings/{tripId} */
    @NonNull
    public DatabaseReference getRatingsRef(@NonNull String tripId) {
        return database.getReference(Constants.PATH_RATINGS).child(tripId);
    }

    /** /reports */
    @NonNull
    public DatabaseReference getReportsRef() {
        return database.getReference(Constants.PATH_REPORTS);
    }

    /** .info/connected */
    @NonNull
    public DatabaseReference getConnectedRef() {
        return database.getReference(Constants.PATH_CONNECTED);
    }
}

