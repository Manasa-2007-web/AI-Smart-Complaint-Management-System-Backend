package com.complaint.management.controller;

import com.complaint.management.dto.ComplaintRequest;
import com.complaint.management.dto.ComplaintResponse;
import com.complaint.management.dto.ComplaintSimilarityResponse;
import com.complaint.management.dto.ComplaintUpdateRequest;
import com.complaint.management.dto.CommunityVisibilityRequest;
import com.complaint.management.service.CommunityService;
import com.complaint.management.service.ComplaintService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/complaints")
public class ComplaintController {

    private final ComplaintService complaintService;
    private final CommunityService communityService;

    public ComplaintController(ComplaintService complaintService, CommunityService communityService) {
        this.complaintService = complaintService;
        this.communityService = communityService;
    }

    @PostMapping
    public ResponseEntity<ComplaintResponse> create(
            @Valid @RequestBody ComplaintRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(complaintService.createComplaint(request, authentication.getName()));
    }

    @GetMapping("/my")
    public ResponseEntity<List<ComplaintResponse>> getMine(Authentication authentication) {
        return ResponseEntity.ok(complaintService.getMyComplaints(authentication.getName()));
    }

    @GetMapping("/{id}/similar")
    public ResponseEntity<List<ComplaintSimilarityResponse>> getSimilarComplaints(
            @PathVariable Long id,
            Authentication authentication
    ) {
        return ResponseEntity.ok(complaintService.getSimilarComplaints(id, authentication.getName()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ComplaintResponse> getMineById(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(complaintService.getMyComplaint(id, authentication.getName()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ComplaintResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody ComplaintUpdateRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(complaintService.updateMyComplaint(id, request, authentication.getName()));
    }

    @PutMapping("/{id}/community-visibility")
    public ResponseEntity<Boolean> setCommunityVisibility(
            @PathVariable Long id,
            @Valid @RequestBody CommunityVisibilityRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(communityService.setComplaintVisibilityAsOwner(
                id,
                request.visible(),
                authentication.getName()
        ));
    }
}
