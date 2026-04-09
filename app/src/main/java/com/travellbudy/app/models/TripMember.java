package com.travellbudy.app.models;

import com.google.firebase.database.IgnoreExtraProperties;

import java.util.HashMap;
import java.util.Map;

@IgnoreExtraProperties
public class TripMember {
    public String uid;
    public String role; // "driver" (organizer) or "rider" (participant) — kept for DB compat
    public long joinedAt;
    public int seatsOccupied; // spots occupied

    public TripMember() {
        // Required empty constructor for Firebase
    }

    public TripMember(String uid, String role, int seatsOccupied) {
        this.uid = uid;
        this.role = role;
        this.joinedAt = System.currentTimeMillis();
        this.seatsOccupied = seatsOccupied;
    }

    public Map<String, Object> toMap() {
        HashMap<String, Object> result = new HashMap<>();
        result.put("uid", uid);
        result.put("role", role);
        result.put("joinedAt", joinedAt);
        result.put("seatsOccupied", seatsOccupied);
        return result;
    }
}

