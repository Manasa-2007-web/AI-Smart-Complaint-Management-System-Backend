package com.complaint.management.dto;

import com.complaint.management.entity.ComplaintStatus;

import java.time.LocalDateTime;
import java.util.List;

public record ComplaintResponse(
        Long id,
        Long userId,
        String userName,
        Long departmentId,
        String departmentName,
        Long assignedStaffId,
        String assignedStaffName,
        String title,
        String description,
        String location,
        String category,
        String severity,
        String priority,
        ComplaintStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<StatusHistoryResponse> statusHistory,
        Integer ageDays,
        Boolean overdue,
        String communityArea,
        boolean communityVisible
) {
    public ComplaintResponse(
            Long id,
            Long userId,
            String userName,
            Long departmentId,
            String departmentName,
            Long assignedStaffId,
            String assignedStaffName,
            String title,
            String description,
            String location,
            String category,
            String severity,
            String priority,
            ComplaintStatus status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            List<StatusHistoryResponse> statusHistory,
            Integer ageDays,
            Boolean overdue
    ) {
        this(id, userId, userName, departmentId, departmentName, assignedStaffId, assignedStaffName,
                title, description, location, category, severity, priority, status, createdAt,
                updatedAt, statusHistory, ageDays, overdue, null, false);
    }
}
