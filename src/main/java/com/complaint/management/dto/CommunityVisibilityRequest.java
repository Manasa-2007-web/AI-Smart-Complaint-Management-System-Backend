package com.complaint.management.dto;

import jakarta.validation.constraints.NotNull;

public record CommunityVisibilityRequest(@NotNull Boolean visible) {
}
