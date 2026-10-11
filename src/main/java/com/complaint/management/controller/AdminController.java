package com.complaint.management.controller;

import com.complaint.management.dto.*;
import com.complaint.management.service.AdminService;
import com.complaint.management.service.ComplaintService;
import com.complaint.management.service.CommunityService;
import com.complaint.management.service.DepartmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final ComplaintService complaintService;
    private final DepartmentService departmentService;
    private final AdminService adminService;
    private final CommunityService communityService;

    public AdminController(
            ComplaintService complaintService,
            DepartmentService departmentService,
            AdminService adminService,
            CommunityService communityService
    ) {
        this.complaintService = complaintService;
        this.departmentService = departmentService;
        this.adminService = adminService;
        this.communityService = communityService;
    }

    @GetMapping("/dashboard")
    public DashboardStatistics dashboard() {
        return adminService.getStatistics();
    }

    @GetMapping("/complaints")
    public List<ComplaintResponse> complaints() {
        return complaintService.getAllComplaints();
    }

    @GetMapping("/complaints/{id}")
    public ComplaintResponse complaint(@PathVariable Long id) {
        return complaintService.getComplaint(id);
    }

    @GetMapping("/users")
    public List<UserResponse> users() {
        return adminService.getUsers();
    }

    @PostMapping("/departments")
    public ResponseEntity<DepartmentResponse> createDepartment(@Valid @RequestBody DepartmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(departmentService.create(request));
    }

    @GetMapping("/departments")
    public List<DepartmentResponse> departments() {
        return departmentService.findAll();
    }

    @PutMapping("/complaints/{id}/assign")
    public ComplaintResponse assign(
            @PathVariable Long id,
            @Valid @RequestBody AssignmentRequest request,
            Authentication authentication
    ) {
        return complaintService.assignComplaint(id, request, authentication.getName());
    }

    @PutMapping("/complaints/{id}/status")
    public ComplaintResponse updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody StatusUpdateRequest request,
            Authentication authentication
    ) {
        return complaintService.updateAdminStatus(id, request, authentication.getName());
    }

    @PutMapping("/complaints/{id}/community-visibility")
    public ResponseEntity<Boolean> setCommunityVisibility(
            @PathVariable Long id,
            @Valid @RequestBody CommunityVisibilityRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(communityService.setComplaintVisibilityAsAdmin(
                id,
                request.visible(),
                authentication.getName()
        ));
    }

    @GetMapping("/community/comments/reported")
    public List<ReportedCommunityCommentResponse> reportedCommunityComments(Authentication authentication) {
        return communityService.getReportedComments(authentication.getName());
    }

    @DeleteMapping("/community/comments/{id}")
    public ResponseEntity<Void> moderateCommunityComment(@PathVariable Long id, Authentication authentication) {
        communityService.deleteComment(id, authentication.getName(), true);
        return ResponseEntity.noContent().build();
    }
}
