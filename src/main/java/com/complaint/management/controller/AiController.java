package com.complaint.management.controller;

import com.complaint.management.dto.AiPredictionRequest;
import com.complaint.management.dto.AiPredictionResponse;
import com.complaint.management.service.AiComplaintClassifierService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiComplaintClassifierService classifierService;

    public AiController(AiComplaintClassifierService classifierService) {
        this.classifierService = classifierService;
    }

    @PostMapping("/predict")
    public ResponseEntity<AiPredictionResponse> predict(@Valid @RequestBody AiPredictionRequest request) {
        return ResponseEntity.ok(classifierService.predict(request));
    }
}
