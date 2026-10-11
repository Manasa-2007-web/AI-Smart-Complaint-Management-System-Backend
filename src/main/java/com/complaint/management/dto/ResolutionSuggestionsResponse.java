package com.complaint.management.dto;

import java.util.List;

public record ResolutionSuggestionsResponse(
        List<ResolutionExampleResponse> pastResolutions,
        List<String> guidance
) {
}
