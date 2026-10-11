package com.complaint.management.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommunitySimilarRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank String description,
        @Size(max = 100) String category
) {
}
