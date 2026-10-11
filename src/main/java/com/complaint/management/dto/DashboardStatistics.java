package com.complaint.management.dto;

public record DashboardStatistics(
        long totalComplaints,
        long submittedComplaints,
        long assignedComplaints,
        long inProgressComplaints,
        long resolvedComplaints,
        long totalUsers,
        long totalStaff,
        long totalDepartments
) {
}
