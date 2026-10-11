package com.complaint.management.controller;

import com.complaint.management.dto.ComplaintResponse;
import com.complaint.management.dto.RemarksRequest;
import com.complaint.management.dto.ResolutionSuggestionsResponse;
import com.complaint.management.dto.StatusUpdateRequest;
import com.complaint.management.service.ComplaintService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/staff")
public class StaffController {

    private final ComplaintService complaintService;

    public StaffController(ComplaintService complaintService) {
        this.complaintService = complaintService;
    }

    @GetMapping("/dashboard")
    public List<ComplaintResponse> dashboard(Authentication authentication) {
        return complaintService.getAssignedComplaints(authentication.getName());
    }

    @GetMapping("/complaints")
    public List<ComplaintResponse> complaints(Authentication authentication) {
        return complaintService.getAssignedComplaints(authentication.getName());
    }

    @GetMapping("/complaints/{id}")
    public ComplaintResponse complaint(@PathVariable Long id, Authentication authentication) {
        return complaintService.getAssignedComplaint(id, authentication.getName());
    }

    @GetMapping("/complaints/{id}/resolution-suggestions")
    public ResolutionSuggestionsResponse resolutionSuggestions(
            @PathVariable Long id,
            Authentication authentication
    ) {
        return complaintService.getResolutionSuggestions(id, authentication.getName());
    }

    @PutMapping("/complaints/{id}/status")
    public ComplaintResponse updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody StatusUpdateRequest request,
            Authentication authentication
    ) {
        return complaintService.updateStaffStatus(id, request, authentication.getName());
    }

    @PutMapping("/complaints/{id}/remarks")
    public ComplaintResponse addRemarks(
            @PathVariable Long id,
            @Valid @RequestBody RemarksRequest request,
            Authentication authentication
    ) {
        return complaintService.addStaffRemarks(id, request, authentication.getName());
    }
}
