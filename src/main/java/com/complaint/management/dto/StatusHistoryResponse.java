package com.complaint.management.dto;

import com.complaint.management.entity.ComplaintStatus;

import java.time.LocalDateTime;

public record StatusHistoryResponse(
        Long id,
        ComplaintStatus status,
        String remarks,
        Long updatedById,
        String updatedByName,
        LocalDateTime updatedAt
) {
}
