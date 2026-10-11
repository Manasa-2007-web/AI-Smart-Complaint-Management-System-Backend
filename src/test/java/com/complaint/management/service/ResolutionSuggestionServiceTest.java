package com.complaint.management.service;

import com.complaint.management.dto.ResolutionSuggestionsResponse;
import com.complaint.management.entity.Complaint;
import com.complaint.management.entity.ComplaintStatus;
import com.complaint.management.entity.ComplaintStatusHistory;
import com.complaint.management.entity.Role;
import com.complaint.management.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResolutionSuggestionServiceTest {

    private final ResolutionSuggestionService service = new ResolutionSuggestionService();

    @Test
    void usesRecordedResolutionFromRelevantResolvedComplaint() {
        Complaint target = complaint(1L, "Water leak", "Leak in the basement", "FACILITY", ComplaintStatus.IN_PROGRESS);
        Complaint resolved = complaint(2L, "Basement pipe leak", "Water leaking near basement storage", "FACILITY", ComplaintStatus.RESOLVED);
        resolved.getStatusHistory().add(new ComplaintStatusHistory(
                resolved, ComplaintStatus.RESOLVED, "Replaced the damaged pipe and confirmed the area was dry.", null));

        ResolutionSuggestionsResponse response = service.suggest(target, List.of(target, resolved));

        assertEquals(1, response.pastResolutions().size());
        assertEquals(2L, response.pastResolutions().getFirst().complaintId());
        assertEquals("Replaced the damaged pipe and confirmed the area was dry.",
                response.pastResolutions().getFirst().resolution());
        assertEquals(List.of(), response.guidance());
    }

    @Test
    void fallsBackToCategoryGuidanceWhenNoRecordedResolutionMatches() {
        Complaint target = complaint(1L, "Portal unavailable", "Portal not loading", "TECHNICAL", ComplaintStatus.IN_PROGRESS);
        Complaint resolvedWithoutResolution = complaint(
                2L, "Billing query", "Invoice discrepancy", "BILLING", ComplaintStatus.RESOLVED);

        ResolutionSuggestionsResponse response = service.suggest(target, List.of(target, resolvedWithoutResolution));

        assertEquals(List.of(), response.pastResolutions());
        assertEquals(3, response.guidance().size());
    }

    private Complaint complaint(Long id, String title, String description, String category, ComplaintStatus status) {
        Complaint complaint = new Complaint();
        complaint.setUser(new User("Submitter", "user@example.test", "encoded-password", Role.USER));
        complaint.setTitle(title);
        complaint.setDescription(description);
        complaint.setCategory(category);
        complaint.setStatus(status);
        ReflectionTestUtils.setField(complaint, "id", id);
        return complaint;
    }
}
