package com.complaint.management.service;

import com.complaint.management.dto.AiPredictionRequest;
import com.complaint.management.dto.AiPredictionResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AiComplaintClassifierServiceTest {

    @Test
    void predictsComplaintCategoryAndPriorityWithTheLocalModel() {
        AiComplaintClassifierService service = new AiComplaintClassifierService(
                new ObjectMapper(),
                "python",
                "ai/predict_complaint.py",
                30
        );

        AiPredictionResponse prediction = service.predict(new AiPredictionRequest(
                "Water leak in the dormitory",
                "The water pipe burst and water is leaking into the hallway near the entrance."
        ));

        assertNotNull(prediction);
        assertNotNull(prediction.category());
        assertTrue(Set.of("TECHNICAL", "BILLING", "SERVICE", "SECURITY", "FACILITY", "ACADEMIC", "OTHER")
                .contains(prediction.category()));
        assertNotNull(prediction.priority());
        assertTrue(Set.of("LOW", "MEDIUM", "HIGH", "URGENT").contains(prediction.priority()));
        assertNotNull(prediction.confidence());
        assertTrue(prediction.confidence() >= 0.0 && prediction.confidence() <= 1.0);
    }
}
