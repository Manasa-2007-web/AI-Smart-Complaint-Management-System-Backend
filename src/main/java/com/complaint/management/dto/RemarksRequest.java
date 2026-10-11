package com.complaint.management.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RemarksRequest(@NotBlank @Size(max = 1000) String remarks) {
}
