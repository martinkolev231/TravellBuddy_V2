package com.travellbudy.app.models;

import com.google.firebase.database.IgnoreExtraProperties;

import java.util.HashMap;
import java.util.Map;

@IgnoreExtraProperties
public class Rating {
    public String ratingId;
    public String tripId;
    public String reviewerUid;
    public String reviewerName;
    public String revieweeUid;
    public int score; // 1-5
    public String comment;
    public long createdAt;
    public boolean isEditable;

    public Rating() {
        // Required empty constructor for Firebase
    }

    public Rating(String ratingId, String tripId, String reviewerUid, String reviewerName,
                  String revieweeUid, int score, String comment) {
        this.ratingId = ratingId;
        this.tripId = tripId;
        this.reviewerUid = reviewerUid;
        this.reviewerName = reviewerName;
        this.revieweeUid = revieweeUid;
        this.score = score;
        this.comment = comment;
        this.createdAt = System.currentTimeMillis();
        this.isEditable = true; // editable for 10 minutes after creation
    }

    public Map<String, Object> toMap() {
        HashMap<String, Object> result = new HashMap<>();
        result.put("ratingId", ratingId);
        result.put("tripId", tripId);
        result.put("reviewerUid", reviewerUid);
        result.put("reviewerName", reviewerName);
        result.put("revieweeUid", revieweeUid);
        result.put("score", score);
        result.put("comment", comment);
        result.put("createdAt", createdAt);
        result.put("isEditable", isEditable);
        return result;
    }
}

