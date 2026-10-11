package com.complaint.management.dto;

import com.complaint.management.entity.ComplaintStatus;

import java.time.LocalDateTime;
import java.util.List;

public record CommunityComplaintResponse(
        Long id,
        String title,
        String description,
        String area,
        String category,
        ComplaintStatus status,
        String priority,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        long supportCount,
        boolean supportedByCurrentUser,
        long commentCount,
        List<CommunityCommentResponse> comments
) {
}
