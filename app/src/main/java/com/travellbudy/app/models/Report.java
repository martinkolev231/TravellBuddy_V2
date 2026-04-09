package com.travellbudy.app.models;

import com.google.firebase.database.IgnoreExtraProperties;

import java.util.HashMap;
import java.util.Map;

@IgnoreExtraProperties
public class Report {
    public String reportId;
    public String reporterUid;
    public String reportedUid;
    public String tripId;
    public String reason;    // e.g. "no_show", "harassment", "unsafe_driving", "other"
    public String description;
    public long createdAt;
    public String status;    // "open", "reviewed", "resolved"

    public Report() {
        // Required empty constructor for Firebase
    }

    public Report(String reportId, String reporterUid, String reportedUid,
                  String tripId, String reason, String description) {
        this.reportId = reportId;
        this.reporterUid = reporterUid;
        this.reportedUid = reportedUid;
        this.tripId = tripId != null ? tripId : "";
        this.reason = reason;
        this.description = description;
        this.createdAt = System.currentTimeMillis();
        this.status = "open";
    }

    public Map<String, Object> toMap() {
        HashMap<String, Object> result = new HashMap<>();
        result.put("reportId", reportId);
        result.put("reporterUid", reporterUid);
        result.put("reportedUid", reportedUid);
        result.put("tripId", tripId != null ? tripId : "");
        result.put("reason", reason);
        result.put("description", description);
        result.put("createdAt", createdAt);
        result.put("status", status);
        return result;
    }
}

