package com.complaint.management.dto;

import com.complaint.management.entity.ComplaintStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StatusUpdateRequest(
        @NotNull ComplaintStatus status,
        @Size(max = 1000) String remarks
) {
}
