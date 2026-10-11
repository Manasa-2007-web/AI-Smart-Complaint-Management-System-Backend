package com.complaint.management.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommunityCommentRequest(
        @NotBlank(message = "Comment cannot be blank")
        @Size(max = 2000, message = "Comment cannot exceed 2000 characters")
        String text
) {
}
