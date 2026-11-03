package com.aman.bookmark_cleaner.dto;

public class DecisionUpdateResponse {

    private final String itemId;
    private final boolean applicable;
    private final boolean enabled;
    private final String message;

    public DecisionUpdateResponse(String itemId, boolean applicable, boolean enabled, String message) {
        this.itemId = itemId;
        this.applicable = applicable;
        this.enabled = enabled;
        this.message = message;
    }

    public String getItemId() {
        return itemId;
    }

    public boolean isApplicable() {
        return applicable;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getMessage() {
        return message;
    }
}
