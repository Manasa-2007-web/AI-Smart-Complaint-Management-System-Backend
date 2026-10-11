package com.complaint.management.dto;

import com.complaint.management.entity.ComplaintStatus;

public record ComplaintSimilarityResponse(
        Long id,
        String title,
        String description,
        String category,
        String priority,
        ComplaintStatus status,
        double similarityPercentage
) {
}
