package com.travellbudy.app.models;

import com.google.firebase.database.IgnoreExtraProperties;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

@IgnoreExtraProperties
public class Trip {
    
    /**
     * Trip status constants for date-based status calculation
     */
    public static final String STATUS_CANCELED = "canceled";
    public static final String STATUS_UPCOMING = "upcoming";
    public static final String STATUS_ONGOING = "ongoing";
    public static final String STATUS_COMPLETED = "completed";
    
    public String tripId;
    public String driverUid;  // kept as-is in DB for backward compatibility (= organizer UID)

    // Denormalized organizer info (avoids extra read on Home Feed)
    public String driverName;      // organizer display name
    public String driverPhotoUrl;  // organizer photo

    // Location
    public String originCity;      // meeting point / starting location
    public String originAddress;
    public double originLat;
    public double originLng;
    public String destinationCity; // destination or area
    public String destinationAddress;
    public double destLat;
    public double destLng;

    // Schedule
    public long departureTime;       // start time
    public long estimatedArrivalTime;

    // Spots & pricing (was seats)
    public int totalSeats;           // total spots
    public int availableSeats;       // available spots
    public double pricePerSeat;      // price per spot (0 = free)
    public String currency;

    // Status
    public String status; // open, full, in_progress, completed, canceled

    // Adventure details (repurposed from vehicle/preferences)
    public String description;       // adventure description
    public String carModel;          // repurposed: short title/summary
    public String carColor;          // unused, kept for DB compat
    public String carPlate;          // unused, kept for DB compat

    // Adventure-specific fields
    public String activityType;      // hiking, camping, road_trip, city_explore, festival, photography, outdoor_sports, backpacking, weekend, other
    public String difficultyLevel;   // easy, moderate, hard
    public String requiredEquipment; // free-text: "hiking boots, water, sunscreen"
    public String meetingPoint;      // detailed meeting point instructions
    public String imageUrl;          // URL of the adventure cover image

    // Legacy preference fields repurposed
    public boolean smokingAllowed;   // repurposed: beginners welcome
    public boolean petsAllowed;      // pet-friendly
    public String luggageSize;       // repurposed: difficulty level (easy, moderate, hard)

    // Timestamps
    public long createdAt;
    public long updatedAt;

    public Trip() {
        // Required empty constructor for Firebase
    }

    public Trip(String tripId, String driverUid, String driverName,
                String originCity, String originAddress,
                String destinationCity, String destinationAddress,
                long departureTime, long estimatedArrivalTime,
                int totalSeats, double pricePerSeat, String currency,
                String carModel) {
        this.tripId = tripId;
        this.driverUid = driverUid;
        this.driverName = driverName;
        this.driverPhotoUrl = "";
        this.originCity = originCity;
        this.originAddress = originAddress;
        this.destinationCity = destinationCity;
        this.destinationAddress = destinationAddress;
        this.departureTime = departureTime;
        this.estimatedArrivalTime = estimatedArrivalTime;
        this.totalSeats = totalSeats;
        this.availableSeats = totalSeats;
        this.pricePerSeat = pricePerSeat;
        this.currency = currency;
        this.status = "open";
        this.description = "";
        this.carModel = carModel;
        this.carColor = "";
        this.carPlate = "";
        this.activityType = "other";
        this.difficultyLevel = "moderate";
        this.requiredEquipment = "";
        this.meetingPoint = "";
        this.imageUrl = "";
        this.smokingAllowed = false;
        this.petsAllowed = false;
        this.luggageSize = "moderate";
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    public Map<String, Object> toMap() {
        HashMap<String, Object> result = new HashMap<>();
        result.put("tripId", tripId);
        result.put("driverUid", driverUid);
        result.put("driverName", driverName);
        result.put("driverPhotoUrl", driverPhotoUrl != null ? driverPhotoUrl : "");
        result.put("originCity", originCity);
        result.put("originAddress", originAddress != null ? originAddress : "");
        result.put("originLat", originLat);
        result.put("originLng", originLng);
        result.put("destinationCity", destinationCity);
        result.put("destinationAddress", destinationAddress != null ? destinationAddress : "");
        result.put("destLat", destLat);
        result.put("destLng", destLng);
        result.put("departureTime", departureTime);
        result.put("estimatedArrivalTime", estimatedArrivalTime);
        result.put("totalSeats", totalSeats);
        result.put("availableSeats", availableSeats);
        result.put("pricePerSeat", pricePerSeat);
        result.put("currency", currency);
        result.put("status", status);
        result.put("description", description != null ? description : "");
        result.put("carModel", carModel != null ? carModel : "");
        result.put("carColor", carColor != null ? carColor : "");
        result.put("carPlate", carPlate != null ? carPlate : "");
        result.put("activityType", activityType != null ? activityType : "other");
        result.put("difficultyLevel", difficultyLevel != null ? difficultyLevel : "moderate");
        result.put("requiredEquipment", requiredEquipment != null ? requiredEquipment : "");
        result.put("meetingPoint", meetingPoint != null ? meetingPoint : "");
        result.put("imageUrl", imageUrl != null ? imageUrl : "");
        result.put("smokingAllowed", smokingAllowed);
        result.put("petsAllowed", petsAllowed);
        result.put("luggageSize", luggageSize != null ? luggageSize : "moderate");
        result.put("createdAt", createdAt);
        result.put("updatedAt", updatedAt);
        return result;
    }

    public boolean isFull() {
        return availableSeats <= 0;
    }

    public boolean isOpen() {
        return "open".equals(status);
    }
    
    /**
     * Calculate the effective status of the trip based on dates and cancellation state.
     * Uses DATE ONLY comparison (not exact time).
     * 
     * Rules:
     * - Canceled: trip is marked as canceled (always takes priority)
     * - Upcoming: current date is before the trip start date
     * - Ongoing: current date is between start date and end date (inclusive)
     * - Completed: current date is after the trip end date
     * 
     * @return The effective status string: "canceled", "upcoming", "ongoing", or "completed"
     */
    public String getEffectiveStatus() {
        // Canceled trips always stay canceled
        if (STATUS_CANCELED.equals(status)) {
            return STATUS_CANCELED;
        }
        
        // Get current date at midnight (start of day)
        Calendar todayCal = Calendar.getInstance();
        todayCal.set(Calendar.HOUR_OF_DAY, 0);
        todayCal.set(Calendar.MINUTE, 0);
        todayCal.set(Calendar.SECOND, 0);
        todayCal.set(Calendar.MILLISECOND, 0);
        long todayStart = todayCal.getTimeInMillis();
        
        // Get start date at midnight
        Calendar startCal = Calendar.getInstance();
        startCal.setTimeInMillis(departureTime);
        startCal.set(Calendar.HOUR_OF_DAY, 0);
        startCal.set(Calendar.MINUTE, 0);
        startCal.set(Calendar.SECOND, 0);
        startCal.set(Calendar.MILLISECOND, 0);
        long startDateMidnight = startCal.getTimeInMillis();
        
        // Get end date at END of day (23:59:59.999)
        Calendar endCal = Calendar.getInstance();
        endCal.setTimeInMillis(estimatedArrivalTime);
        endCal.set(Calendar.HOUR_OF_DAY, 23);
        endCal.set(Calendar.MINUTE, 59);
        endCal.set(Calendar.SECOND, 59);
        endCal.set(Calendar.MILLISECOND, 999);
        long endDateEnd = endCal.getTimeInMillis();
        
        // Also get end date at start of day for comparison
        endCal.set(Calendar.HOUR_OF_DAY, 0);
        endCal.set(Calendar.MINUTE, 0);
        endCal.set(Calendar.SECOND, 0);
        endCal.set(Calendar.MILLISECOND, 0);
        long endDateMidnight = endCal.getTimeInMillis();
        
        // Determine status based on date comparisons
        if (todayStart < startDateMidnight) {
            // Current date is before start date
            return STATUS_UPCOMING;
        } else if (todayStart <= endDateMidnight) {
            // Current date is between start date and end date (inclusive)
            // Trip stays ongoing for the whole end date
            return STATUS_ONGOING;
        } else {
            // Current date is after end date
            return STATUS_COMPLETED;
        }
    }
    
    /**
     * Check if this trip should be counted in statistics (not canceled).
     * @return true if trip is upcoming, ongoing, or completed (not canceled)
     */
    public boolean isCountable() {
        String effectiveStatus = getEffectiveStatus();
        return !STATUS_CANCELED.equals(effectiveStatus);
    }
    
    /**
     * Check if trip is upcoming based on dates.
     */
    public boolean isUpcoming() {
        return STATUS_UPCOMING.equals(getEffectiveStatus());
    }
    
    /**
     * Check if trip is currently ongoing based on dates.
     */
    public boolean isOngoing() {
        return STATUS_ONGOING.equals(getEffectiveStatus());
    }
    
    /**
     * Check if trip has completed based on dates.
     */
    public boolean isCompleted() {
        return STATUS_COMPLETED.equals(getEffectiveStatus());
    }
    
    /**
     * Check if trip is canceled.
     */
    public boolean isCanceled() {
        return STATUS_CANCELED.equals(status);
    }
}
