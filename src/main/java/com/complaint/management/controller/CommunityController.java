package com.complaint.management.controller;

import com.complaint.management.dto.*;
import com.complaint.management.service.CommunityService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/community")
public class CommunityController {

    private final CommunityService communityService;

    public CommunityController(CommunityService communityService) {
        this.communityService = communityService;
    }

    @GetMapping("/complaints")
    public List<CommunityComplaintResponse> complaints(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String area,
            @RequestParam(defaultValue = "newest") String sort,
            Authentication authentication
    ) {
        return communityService.listComplaints(authentication.getName(), search, category, status, area, sort);
    }

    @GetMapping("/complaints/{id}")
    public CommunityComplaintResponse complaint(@PathVariable Long id, Authentication authentication) {
        return communityService.getComplaint(id, authentication.getName());
    }

    @PostMapping("/complaints/{id}/comments")
    public ResponseEntity<CommunityCommentResponse> comment(
            @PathVariable Long id,
            @Valid @RequestBody CommunityCommentRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(communityService.addComment(id, request, authentication.getName()));
    }

    @PostMapping("/comments/{id}/report")
    public ResponseEntity<Void> reportComment(@PathVariable Long id, Authentication authentication) {
        communityService.reportComment(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/comments/{id}")
    public ResponseEntity<Void> deleteOwnComment(@PathVariable Long id, Authentication authentication) {
        communityService.deleteComment(id, authentication.getName(), false);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/complaints/{id}/support")
    public CommunityComplaintResponse support(@PathVariable Long id, Authentication authentication) {
        return communityService.setSupport(id, authentication.getName(), true);
    }

    @DeleteMapping("/complaints/{id}/support")
    public CommunityComplaintResponse removeSupport(@PathVariable Long id, Authentication authentication) {
        return communityService.setSupport(id, authentication.getName(), false);
    }

    @PostMapping("/similar")
    public List<ComplaintSimilarityResponse> similar(
            @Valid @RequestBody CommunitySimilarRequest request,
            Authentication authentication
    ) {
        return communityService.findSimilarCommunityComplaints(request, authentication.getName());
    }
}
