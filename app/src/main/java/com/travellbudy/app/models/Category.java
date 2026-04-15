package com.travellbudy.app.models;

import com.google.firebase.database.IgnoreExtraProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents an adventure category (e.g., Hiking, Kayaking, Climbing).
 * Stored in /categories/{categoryId}
 */
@IgnoreExtraProperties
public class Category {
    public String categoryId;
    public String name;
    public String nameEn;          // English name for potential localization
    public String icon;            // Icon resource name or emoji
    public String description;
    public int order;              // Display order
    public boolean isActive;       // Whether category is visible to users
    public long createdAt;
    public String createdByUid;

    public Category() {
        // Required empty constructor for Firebase
    }

    public Category(String categoryId, String name, String icon) {
        this.categoryId = categoryId;
        this.name = name;
        this.nameEn = name;
        this.icon = icon;
        this.description = "";
        this.order = 0;
        this.isActive = true;
        this.createdAt = System.currentTimeMillis();
        this.createdByUid = "";
    }

    public Map<String, Object> toMap() {
        HashMap<String, Object> result = new HashMap<>();
        result.put("categoryId", categoryId);
        result.put("name", name);
        result.put("nameEn", nameEn);
        result.put("icon", icon);
        result.put("description", description);
        result.put("order", order);
        result.put("isActive", isActive);
        result.put("createdAt", createdAt);
        result.put("createdByUid", createdByUid);
        return result;
    }
}

