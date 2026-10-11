package com.complaint.management.dto;

public record AiPredictionResponse(
        String category,
        String priority,
        Double confidence
) {
}
