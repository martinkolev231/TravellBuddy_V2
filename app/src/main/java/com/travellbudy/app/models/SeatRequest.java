package com.travellbudy.app.models;

import com.google.firebase.database.IgnoreExtraProperties;

import java.util.HashMap;
import java.util.Map;

@IgnoreExtraProperties
public class SeatRequest {
    public String requestId;
    public String tripId;
    public String riderUid;
    public String riderName;
    public String driverUid;
    public String status; // pending, approved, denied, canceled_by_rider
    public int seatsRequested;
    public String message;
    public long createdAt;
    public long updatedAt;

    public SeatRequest() {
        // Required empty constructor for Firebase
    }

    public SeatRequest(String requestId, String tripId, String riderUid,
                       String riderName, String driverUid, String message) {
        this.requestId = requestId;
        this.tripId = tripId;
        this.riderUid = riderUid;
        this.riderName = riderName;
        this.driverUid = driverUid;
        this.status = "pending";
        this.seatsRequested = 1;
        this.message = message;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    public Map<String, Object> toMap() {
        HashMap<String, Object> result = new HashMap<>();
        result.put("requestId", requestId);
        result.put("tripId", tripId);
        result.put("riderUid", riderUid);
        result.put("riderName", riderName);
        result.put("driverUid", driverUid);
        result.put("status", status);
        result.put("seatsRequested", seatsRequested);
        result.put("message", message);
        result.put("createdAt", createdAt);
        result.put("updatedAt", updatedAt);
        return result;
    }

    public boolean isPending() {
        return "pending".equals(status);
    }

    public boolean isApproved() {
        return "approved".equals(status);
    }
}

