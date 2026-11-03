package com.aman.bookmark_cleaner.dto;

public class DecisionUpdateRequest {

    private Boolean enabled;

    public DecisionUpdateRequest() {
    }

    public DecisionUpdateRequest(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}
