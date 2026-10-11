package com.complaint.management.dto;

public record ResolutionExampleResponse(
        Long complaintId,
        String title,
        String category,
        String resolution
) {
}
