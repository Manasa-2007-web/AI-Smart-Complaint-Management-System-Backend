package com.complaint.management.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AssignmentRequest(
        @NotNull Long departmentId,
        @NotNull Long staffId,
        @Size(max = 1000) String remarks
) {
}
