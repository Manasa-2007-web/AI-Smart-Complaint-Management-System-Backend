package com.complaint.management.dto;

import java.time.LocalDateTime;

public record ReportedCommunityCommentResponse(
        Long id,
        Long complaintId,
        String complaintTitle,
        String text,
        String authorName,
        LocalDateTime createdAt
) {
}
