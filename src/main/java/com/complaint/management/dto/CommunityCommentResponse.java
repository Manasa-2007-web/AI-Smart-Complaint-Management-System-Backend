package com.complaint.management.dto;

import java.time.LocalDateTime;

public record CommunityCommentResponse(
        Long id,
        Long complaintId,
        String text,
        String authorName,
        LocalDateTime createdAt,
        boolean ownComment
) {
}
