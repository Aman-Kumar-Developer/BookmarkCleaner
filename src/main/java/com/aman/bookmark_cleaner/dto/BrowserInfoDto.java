package com.aman.bookmark_cleaner.dto;

import java.util.Collections;
import java.util.List;

/**
 * DTO representing an installed/supported browser and its available profiles.
 */
public class BrowserInfoDto {

    private final String id;
    private final String name;
    private final boolean available;
    private final List<BrowserProfileDto> profiles;

    public BrowserInfoDto(String id, String name, boolean available, List<BrowserProfileDto> profiles) {
        this.id = id;
        this.name = name;
        this.available = available;
        this.profiles = profiles != null ? Collections.unmodifiableList(profiles) : Collections.emptyList();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isAvailable() {
        return available;
    }

    public List<BrowserProfileDto> getProfiles() {
        return profiles;
    }
}
