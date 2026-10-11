package com.complaint.management.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ComplaintRequest(
        @NotBlank(message = "Title is required") @Size(max = 200) String title,
        @NotBlank(message = "Description is required") String description,
        @Size(max = 255) String location,
        @Size(max = 100) String category,
        @Size(max = 100) String severity,
        @Size(max = 100) String priority,
        @Size(max = 160) String communityArea,
        Boolean communityVisible
) {
    public ComplaintRequest(
            String title,
            String description,
            String location,
            String category,
            String severity
    ) {
        this(title, description, location, category, severity, null, null, null);
    }

    public ComplaintRequest(
            String title,
            String description,
            String location,
            String category,
            String severity,
            String priority
    ) {
        this(title, description, location, category, severity, priority, null, null);
    }
}
