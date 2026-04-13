package com.travellbudy.app.utils;

import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.travellbudy.app.models.Trip;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/**
 * Helper class to create test trips for development purposes.
 * DELETE THIS CLASS BEFORE PRODUCTION RELEASE.
 */
public class TestDataHelper {

    private static final String TAG = "TestDataHelper";
    
    // Fake driver ID for trips where current user joins
    private static final String FAKE_DRIVER_UID = "test_driver_123";
    private static final String FAKE_DRIVER_NAME = "Test Driver";

    /**
     * Creates test trips with different statuses for the current user.
     * Call this from a button click or debug menu.
     * 
     * Note: Firebase rules require departureTime > now on creation.
     * We create with future dates then update to actual past dates.
     */
    public static void createTestTrips() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Log.e(TAG, "No user logged in!");
            return;
        }

        String userId = user.getUid();
        String userName = user.getDisplayName() != null ? user.getDisplayName() : "Test User";
        String userPhoto = user.getPhotoUrl() != null ? user.getPhotoUrl().toString() : "";

        Log.d(TAG, "Creating test trips for user: " + userId + " (" + userName + ")");

        DatabaseReference tripsRef = FirebaseDatabase.getInstance().getReference("trips");
        DatabaseReference tripRequestsRef = FirebaseDatabase.getInstance().getReference("tripRequests");

        // ═══════════════════════════════════════════════════════════════════════
        // HOSTED TRIPS (user is the driver)
        // ═══════════════════════════════════════════════════════════════════════
        
        // Create an ONGOING trip (hosted)
        createOngoingHostedTrip(tripsRef, userId, userName, userPhoto);

        // Create a COMPLETED trip (hosted)
        createCompletedHostedTrip(tripsRef, userId, userName, userPhoto);

        // Create another COMPLETED trip (hosted)
        createCompletedHostedTrip2(tripsRef, userId, userName, userPhoto);
        
        // ═══════════════════════════════════════════════════════════════════════
        // JOINED TRIPS (user joins someone else's trip)
        // ═══════════════════════════════════════════════════════════════════════
        
        // Create an ONGOING trip (joined)
        createOngoingJoinedTrip(tripsRef, tripRequestsRef, userId, userName);
        
        // Create a COMPLETED trip (joined)
        createCompletedJoinedTrip(tripsRef, tripRequestsRef, userId, userName);
        
        Log.d(TAG, "All test trips creation initiated!");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HOSTED TRIPS
    // ═══════════════════════════════════════════════════════════════════════════

    private static void createOngoingHostedTrip(DatabaseReference tripsRef, String userId, String userName, String userPhoto) {
        String tripId = tripsRef.push().getKey();
        if (tripId == null) {
            Log.e(TAG, "Failed to generate trip ID for ongoing hosted trip");
            return;
        }

        Calendar actualStartCal = Calendar.getInstance();
        actualStartCal.add(Calendar.DAY_OF_MONTH, -1);
        actualStartCal.set(Calendar.HOUR_OF_DAY, 9);
        actualStartCal.set(Calendar.MINUTE, 0);
        long actualStartTime = actualStartCal.getTimeInMillis();

        Calendar actualEndCal = Calendar.getInstance();
        actualEndCal.add(Calendar.DAY_OF_MONTH, 2);
        actualEndCal.set(Calendar.HOUR_OF_DAY, 18);
        actualEndCal.set(Calendar.MINUTE, 0);
        long actualEndTime = actualEndCal.getTimeInMillis();

        Calendar tempStartCal = Calendar.getInstance();
        tempStartCal.add(Calendar.DAY_OF_MONTH, 100);
        long tempStartTime = tempStartCal.getTimeInMillis();
        long tempEndTime = tempStartTime + (actualEndTime - actualStartTime);

        Log.d(TAG, "Creating ONGOING HOSTED trip: " + tripId);

        Trip trip = new Trip(
                tripId, userId, userName,
                "Sofia", "Sofia Center",
                "Bansko", "Bansko Ski Resort",
                tempStartTime,
                tempEndTime,
                4, 50.0, "EUR",
                "Mountain Skiing Weekend"
        );
        trip.driverPhotoUrl = userPhoto;
        trip.activityType = "outdoor_sports";
        trip.description = "Currently enjoying a skiing trip in the mountains! This is an ONGOING HOSTED trip for testing.";
        trip.imageUrl = "https://images.unsplash.com/photo-1551524559-8af4e6624178?w=800";

        tripsRef.child(tripId).setValue(trip.toMap())
                .addOnSuccessListener(aVoid -> {
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("departureTime", actualStartTime);
                    updates.put("estimatedArrivalTime", actualEndTime);
                    tripsRef.child(tripId).updateChildren(updates)
                            .addOnSuccessListener(v -> Log.d(TAG, "ONGOING HOSTED trip created: " + tripId))
                            .addOnFailureListener(e -> Log.e(TAG, "Failed to update dates: " + e.getMessage()));
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to create trip: " + e.getMessage()));
    }

    private static void createCompletedHostedTrip(DatabaseReference tripsRef, String userId, String userName, String userPhoto) {
        String tripId = tripsRef.push().getKey();
        if (tripId == null) return;

        Calendar actualStartCal = Calendar.getInstance();
        actualStartCal.add(Calendar.DAY_OF_MONTH, -5);
        actualStartCal.set(Calendar.HOUR_OF_DAY, 8);
        long actualStartTime = actualStartCal.getTimeInMillis();

        Calendar actualEndCal = Calendar.getInstance();
        actualEndCal.add(Calendar.DAY_OF_MONTH, -3);
        actualEndCal.set(Calendar.HOUR_OF_DAY, 20);
        long actualEndTime = actualEndCal.getTimeInMillis();

        Calendar tempStartCal = Calendar.getInstance();
        tempStartCal.add(Calendar.DAY_OF_MONTH, 101);
        long tempStartTime = tempStartCal.getTimeInMillis();
        long tempEndTime = tempStartTime + (actualEndTime - actualStartTime);

        Log.d(TAG, "Creating COMPLETED HOSTED trip 1: " + tripId);

        Trip trip = new Trip(
                tripId, userId, userName,
                "Plovdiv", "Plovdiv Central Station",
                "Veliko Tarnovo", "Tsarevets Fortress",
                tempStartTime, tempEndTime,
                3, 30.0, "EUR",
                "Historical Tour - Veliko Tarnovo"
        );
        trip.driverPhotoUrl = userPhoto;
        trip.activityType = "city_explore";
        trip.description = "Explored the ancient capital! COMPLETED HOSTED trip.";
        trip.imageUrl = "https://images.unsplash.com/photo-1558618666-fcd25c85cd64?w=800";

        tripsRef.child(tripId).setValue(trip.toMap())
                .addOnSuccessListener(aVoid -> {
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("departureTime", actualStartTime);
                    updates.put("estimatedArrivalTime", actualEndTime);
                    tripsRef.child(tripId).updateChildren(updates)
                            .addOnSuccessListener(v -> Log.d(TAG, "COMPLETED HOSTED trip 1 created: " + tripId))
                            .addOnFailureListener(e -> Log.e(TAG, "Failed: " + e.getMessage()));
                });
    }

    private static void createCompletedHostedTrip2(DatabaseReference tripsRef, String userId, String userName, String userPhoto) {
        String tripId = tripsRef.push().getKey();
        if (tripId == null) return;

        Calendar actualStartCal = Calendar.getInstance();
        actualStartCal.add(Calendar.DAY_OF_MONTH, -10);
        long actualStartTime = actualStartCal.getTimeInMillis();

        Calendar actualEndCal = Calendar.getInstance();
        actualEndCal.add(Calendar.DAY_OF_MONTH, -7);
        long actualEndTime = actualEndCal.getTimeInMillis();

        Calendar tempStartCal = Calendar.getInstance();
        tempStartCal.add(Calendar.DAY_OF_MONTH, 102);
        long tempStartTime = tempStartCal.getTimeInMillis();
        long tempEndTime = tempStartTime + (actualEndTime - actualStartTime);

        Log.d(TAG, "Creating COMPLETED HOSTED trip 2: " + tripId);

        Trip trip = new Trip(
                tripId, userId, userName,
                "Varna", "Varna Airport",
                "Sunny Beach", "Beach Resort",
                tempStartTime, tempEndTime,
                5, 0.0, "EUR",
                "Beach Getaway"
        );
        trip.driverPhotoUrl = userPhoto;
        trip.activityType = "beach";
        trip.description = "Amazing beach trip! COMPLETED HOSTED trip.";
        trip.imageUrl = "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=800";

        tripsRef.child(tripId).setValue(trip.toMap())
                .addOnSuccessListener(aVoid -> {
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("departureTime", actualStartTime);
                    updates.put("estimatedArrivalTime", actualEndTime);
                    tripsRef.child(tripId).updateChildren(updates)
                            .addOnSuccessListener(v -> Log.d(TAG, "COMPLETED HOSTED trip 2 created: " + tripId))
                            .addOnFailureListener(e -> Log.e(TAG, "Failed: " + e.getMessage()));
                });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // JOINED TRIPS (trips where current user is a rider, not the host)
    // Note: We can create these trips but can't update dates (security rules).
    // So we create them with dates that will naturally be ongoing/completed.
    // ═══════════════════════════════════════════════════════════════════════════

    private static void createOngoingJoinedTrip(DatabaseReference tripsRef, DatabaseReference tripRequestsRef, 
                                                 String userId, String userName) {
        String tripId = tripsRef.push().getKey();
        if (tripId == null) {
            Log.e(TAG, "Failed to generate trip ID for ongoing joined trip");
            return;
        }

        // For ongoing: start just 1 minute from now, end in 3 days
        // The trip will become "ongoing" almost immediately
        Calendar startCal = Calendar.getInstance();
        startCal.add(Calendar.MINUTE, 1); // Starts in 1 minute
        long startTime = startCal.getTimeInMillis();

        Calendar endCal = Calendar.getInstance();
        endCal.add(Calendar.DAY_OF_MONTH, 3);
        long endTime = endCal.getTimeInMillis();

        Log.d(TAG, "Creating ONGOING JOINED trip: " + tripId);

        Trip trip = new Trip(
                tripId, FAKE_DRIVER_UID, FAKE_DRIVER_NAME,
                "Athens", "Athens Airport",
                "Santorini", "Santorini Port",
                startTime, endTime,
                6, 120.0, "EUR",
                "Greek Islands Adventure"
        );
        trip.driverPhotoUrl = "";
        trip.activityType = "road_trip";
        trip.availableSeats = 4;
        trip.description = "Exploring the beautiful Greek islands! ONGOING JOINED trip.";
        trip.imageUrl = "https://images.unsplash.com/photo-1613395877344-13d4a8e0d49e?w=800";

        tripsRef.child(tripId).setValue(trip.toMap())
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "ONGOING JOINED trip created: " + tripId);
                    // Create approved seat request for current user
                    createApprovedSeatRequest(tripRequestsRef, tripId, userId, userName, FAKE_DRIVER_UID);
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to create ONGOING JOINED trip: " + e.getMessage()));
    }

    private static void createCompletedJoinedTrip(DatabaseReference tripsRef, DatabaseReference tripRequestsRef,
                                                   String userId, String userName) {
        // LIMITATION: We cannot create completed joined trips from the client
        // because Firebase rules require departureTime > now on creation,
        // and we can't update trips we don't own.
        //
        // For testing completed joined trips, you need to either:
        // 1. Use the Firebase Console to manually adjust dates
        // 2. Use two accounts (one to host, one to join)
        // 3. Wait for the ongoing joined trip to complete naturally
        
        Log.w(TAG, "COMPLETED JOINED trips cannot be created from client due to Firebase rules.");
        Log.w(TAG, "To test: Use Firebase Console to change dates on an existing joined trip.");
        
        // Create as UPCOMING instead (user can manually change dates in Firebase)
        String tripId = tripsRef.push().getKey();
        if (tripId == null) {
            Log.e(TAG, "Failed to generate trip ID for completed joined trip");
            return;
        }

        Calendar startCal = Calendar.getInstance();
        startCal.add(Calendar.DAY_OF_MONTH, 7); // 7 days from now (upcoming)
        long startTime = startCal.getTimeInMillis();

        Calendar endCal = Calendar.getInstance();
        endCal.add(Calendar.DAY_OF_MONTH, 10);
        long endTime = endCal.getTimeInMillis();

        Log.d(TAG, "Creating UPCOMING JOINED trip (change dates in Firebase for completed): " + tripId);

        Trip trip = new Trip(
                tripId, FAKE_DRIVER_UID, FAKE_DRIVER_NAME,
                "Barcelona", "Barcelona Sants",
                "Valencia", "Valencia Beach",
                startTime, endTime,
                4, 85.0, "EUR",
                "Spanish Coast Road Trip"
        );
        trip.driverPhotoUrl = "";
        trip.activityType = "road_trip";
        trip.availableSeats = 2;
        trip.description = "Road trip along the Spanish coast! Change dates in Firebase Console to test as COMPLETED.";
        trip.imageUrl = "https://images.unsplash.com/photo-1583422409516-2895a77efded?w=800";

        tripsRef.child(tripId).setValue(trip.toMap())
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "UPCOMING JOINED trip created: " + tripId);
                    createApprovedSeatRequest(tripRequestsRef, tripId, userId, userName, FAKE_DRIVER_UID);
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to create trip: " + e.getMessage()));
    }

    /**
     * Creates an approved seat request for the user, making them a "joined" rider on the trip.
     */
    private static void createApprovedSeatRequest(DatabaseReference tripRequestsRef, String tripId, 
                                                   String userId, String userName, String driverUid) {
        String requestId = tripRequestsRef.child(tripId).push().getKey();
        if (requestId == null) {
            Log.e(TAG, "Failed to generate request ID");
            return;
        }

        Map<String, Object> request = new HashMap<>();
        request.put("requestId", requestId);
        request.put("tripId", tripId);
        request.put("riderUid", userId);
        request.put("riderName", userName);
        request.put("driverUid", driverUid);
        request.put("status", "approved");  // Already approved!
        request.put("seatsRequested", 1);
        request.put("createdAt", System.currentTimeMillis());
        request.put("message", "Test join request");

        tripRequestsRef.child(tripId).child(requestId).setValue(request)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Approved seat request created for trip: " + tripId))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to create seat request: " + e.getMessage()));
    }
}

