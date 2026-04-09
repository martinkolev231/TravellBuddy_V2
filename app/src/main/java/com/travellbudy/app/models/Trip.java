package com.travellbudy.app.models;

import com.google.firebase.database.IgnoreExtraProperties;

import java.util.HashMap;
import java.util.Map;

@IgnoreExtraProperties
public class Trip {
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
}
