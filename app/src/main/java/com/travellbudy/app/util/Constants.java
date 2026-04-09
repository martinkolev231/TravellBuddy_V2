package com.travellbudy.app.util;

/**
 * Application-wide constants. Centralizes all Firebase database paths,
 * intent extras, SharedPreferences keys, and hard limits.
 *
 * <p>Usage: {@code FirebaseManager.getRef(Constants.PATH_TRIPS)}
 */
public final class Constants {

    private Constants() {
        // Prevent instantiation
    }

    // =========================================================================
    // Firebase Realtime Database paths
    // =========================================================================
    public static final String PATH_USERS         = "users";
    public static final String PATH_TRIPS         = "trips";
    public static final String PATH_TRIP_REQUESTS = "tripRequests";
    public static final String PATH_TRIP_MEMBERS  = "tripMembers";
    public static final String PATH_CHATS         = "chats";
    public static final String PATH_MESSAGES      = "messages";
    public static final String PATH_USER_CHATS    = "userChats";
    public static final String PATH_RATINGS       = "ratings";
    public static final String PATH_REPORTS       = "reports";
    public static final String PATH_FCM_QUEUE     = "notifications";

    // User sub-paths
    public static final String FIELD_RATING_SUMMARY = "ratingSummary";
    public static final String FIELD_TRIP_COUNTERS  = "tripCounters";
    public static final String FIELD_AVG_RATING     = "averageRating";
    public static final String FIELD_TOTAL_RATINGS  = "totalRatings";

    // Trip fields used in queries
    public static final String FIELD_DEPARTURE_TIME   = "departureTime";
    public static final String FIELD_DRIVER_UID       = "driverUid";
    public static final String FIELD_STATUS           = "status";
    public static final String FIELD_AVAILABLE_SEATS  = "availableSeats";
    public static final String FIELD_UPDATED_AT       = "updatedAt";

    // Request fields
    public static final String FIELD_RIDER_UID        = "riderUid";
    public static final String FIELD_SEATS_REQUESTED  = "seatsRequested";

    // Rating fields
    public static final String FIELD_REVIEWER_UID     = "reviewerUid";
    public static final String FIELD_REVIEWEE_UID     = "revieweeUid";

    // Chat fields
    public static final String FIELD_PARTICIPANTS     = "participants";
    public static final String FIELD_LAST_MESSAGE     = "lastMessage";
    public static final String FIELD_LAST_MSG_TIME    = "lastMessageTime";

    // FCM / notifications
    public static final String FIELD_FCM_TOKEN      = "fcmToken";
    public static final String NOTIF_CHANNEL_ID_MAIN = "travellbuddy_main";
    public static final String NOTIF_TYPE_SEAT_REQUEST = "join_request_received";
    public static final String NOTIF_TYPE_REQUEST_STATUS = "request_status_updated";
    public static final String NOTIF_TYPE_TRIP_CANCELED = "adventure_canceled";
    public static final String NOTIF_TYPE_CHAT_MESSAGE = "chat_message";
    public static final String EXTRA_CHAT_ID = "chatId";
    public static final String EXTRA_NOTIFICATION_TYPE = "type";

    // Trip status values
    public static final String STATUS_OPEN        = "open";
    public static final String STATUS_FULL        = "full";
    public static final String STATUS_IN_PROGRESS = "in_progress";
    public static final String STATUS_COMPLETED   = "completed";
    public static final String STATUS_CANCELED    = "canceled";

    // Request status values
    public static final String REQUEST_PENDING          = "pending";
    public static final String REQUEST_APPROVED         = "approved";
    public static final String REQUEST_DENIED           = "denied";
    public static final String REQUEST_CANCELED_BY_RIDER = "canceled_by_rider";

    // =========================================================================
    // Intent / NavArgs extras
    // =========================================================================
    public static final String EXTRA_TRIP_ID        = "tripId";
    public static final String EXTRA_USER_ID        = "userId";
    public static final String EXTRA_RECIPIENT_UID  = "recipientUid";
    public static final String EXTRA_RECIPIENT_NAME = "recipientName";
    public static final String EXTRA_TRIP_ROUTE     = "tripRoute";

    // =========================================================================
    // Validation limits
    // =========================================================================
    public static final int MAX_SEATS             = 20;
    public static final int MIN_SEATS             = 1;
    public static final int MAX_SEATS_PER_REQUEST = 4;
    public static final int MIN_PASSWORD_LENGTH   = 6;
    public static final int MAX_DISPLAY_NAME      = 100;
    public static final int MAX_MESSAGE_LENGTH    = 1000;
    public static final int MAX_REASON_LENGTH     = 200;
    public static final int MAX_DESCRIPTION_LENGTH = 2000;
    public static final int MIN_RATING_SCORE      = 1;
    public static final int MAX_RATING_SCORE      = 5;

    // =========================================================================
    // SharedPreferences
    // =========================================================================
    public static final String PREFS_NAME          = "travellbuddy_prefs";
    public static final String KEY_ONBOARDING_DONE = "onboarding_done";
    public static final String KEY_THEME           = "app_theme";

    // =========================================================================
    // Deep link scheme
    // =========================================================================
    public static final String DEEP_LINK_SCHEME = "travellbuddy";
    public static final String DEEP_LINK_TRIP   = "trip";

    // =========================================================================
    // Connectivity
    // =========================================================================
    public static final String PATH_CONNECTED = ".info/connected";

    // =========================================================================
    // Default values
    // =========================================================================
    public static final String DEFAULT_CURRENCY    = "BGN";
    public static final String DEFAULT_LUGGAGE     = "moderate";
    public static final String DEFAULT_DRIVER_NAME = "Organizer";
    public static final String DEFAULT_ACTIVITY_TYPE = "other";
    public static final String DEFAULT_DIFFICULTY  = "moderate";
    public static final long   ARRIVAL_OFFSET_MS   = 2 * 60 * 60 * 1000L; // 2 hours

    // =========================================================================
    // Pagination
    // =========================================================================
    public static final int HOME_PAGE_SIZE = 20;
}
