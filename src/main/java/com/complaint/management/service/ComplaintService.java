package com.complaint.management.service;

import com.complaint.management.dto.*;
import com.complaint.management.entity.*;
import com.complaint.management.exception.ResourceNotFoundException;
import com.complaint.management.repository.ComplaintRepository;
import com.complaint.management.repository.ComplaintStatusHistoryRepository;
import com.complaint.management.repository.DepartmentRepository;
import com.complaint.management.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
public class ComplaintService {

    private static final Set<ComplaintStatus> ACTIVE_STATUSES = EnumSet.of(
            ComplaintStatus.SUBMITTED,
            ComplaintStatus.ASSIGNED,
            ComplaintStatus.IN_PROGRESS
    );

    private final ComplaintRepository complaintRepository;
    private final ComplaintStatusHistoryRepository historyRepository;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final ComplaintSimilarityService complaintSimilarityService;
    private final ResolutionSuggestionService resolutionSuggestionService;

    @Value("${complaint.overdue-after-days:0}")
    private int overdueAfterDays;

    public ComplaintService(
            ComplaintRepository complaintRepository,
            ComplaintStatusHistoryRepository historyRepository,
            DepartmentRepository departmentRepository,
            UserRepository userRepository,
            ComplaintSimilarityService complaintSimilarityService,
            ResolutionSuggestionService resolutionSuggestionService
    ) {
        this.complaintRepository = complaintRepository;
        this.historyRepository = historyRepository;
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
        this.complaintSimilarityService = complaintSimilarityService;
        this.resolutionSuggestionService = resolutionSuggestionService;
    }

    @PostConstruct
    private void validateOverdueAfterDays() {
        if (overdueAfterDays < 0) {
            throw new IllegalArgumentException("complaint.overdue-after-days cannot be negative");
        }
    }

    @Transactional
    public ComplaintResponse createComplaint(ComplaintRequest request, String email) {
        User user = findUser(email);
        Complaint complaint = new Complaint();
        complaint.setUser(user);
        complaint.setTitle(request.title().trim());
        complaint.setDescription(request.description().trim());
        complaint.setLocation(trimToNull(request.location()));
        complaint.setCommunityArea(trimToNull(request.communityArea()));
        complaint.setCommunityVisible(Boolean.TRUE.equals(request.communityVisible()));
        complaint.setCategory(trimToNull(request.category()));
        complaint.setSeverity(trimToNull(request.severity()));
        String priority = trimToNull(request.priority());
        if (priority != null) {
            complaint.setPriority(priority);
        }
        complaint.setStatus(ComplaintStatus.SUBMITTED);
        complaint = complaintRepository.save(complaint);
        recordStatus(complaint, ComplaintStatus.SUBMITTED, "Complaint submitted", user);
        return toResponse(complaint);
    }

    @Transactional(readOnly = true)
    public List<ComplaintResponse> getMyComplaints(String email) {
        User user = findUser(email);
        return complaintRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ComplaintResponse getMyComplaint(Long id, String email) {
        Complaint complaint = findComplaint(id);
        if (!complaint.getUser().getEmail().equalsIgnoreCase(email)) {
            throw new AccessDeniedException("You can only view your own complaints");
        }
        return toResponse(complaint);
    }

    @Transactional
    public ComplaintResponse updateMyComplaint(Long id, ComplaintUpdateRequest request, String email) {
        Complaint complaint = findComplaint(id);
        if (!complaint.getUser().getEmail().equalsIgnoreCase(email)) {
            throw new AccessDeniedException("You can only update your own complaints");
        }
        if (complaint.getStatus() != ComplaintStatus.SUBMITTED) {
            throw new IllegalArgumentException("Only submitted complaints can be edited");
        }
        complaint.setTitle(request.title().trim());
        complaint.setDescription(request.description().trim());
        complaint.setLocation(trimToNull(request.location()));
        return toResponse(complaintRepository.save(complaint));
    }

    @Transactional(readOnly = true)
    public List<ComplaintResponse> getAllComplaints() {
        return complaintRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ComplaintResponse getComplaint(Long id) {
        return toResponse(findComplaint(id));
    }

    @Transactional(readOnly = true)
    public List<ComplaintSimilarityResponse> getSimilarComplaints(Long id, String email) {
        Complaint complaint = findComplaint(id);
        User requester = findUser(email);
        validateComplaintAccess(complaint, requester);
        List<Complaint> visibleComplaints = switch (requester.getRole()) {
            case ADMIN -> complaintRepository.findAll();
            case STAFF -> complaintRepository.findByAssignedStaffIdOrderByUpdatedAtDesc(requester.getId());
            case USER -> complaintRepository.findByUserIdOrderByCreatedAtDesc(requester.getId());
        };
        return complaintSimilarityService.findSimilarComplaints(complaint, visibleComplaints);
    }

    @Transactional(readOnly = true)
    public ResolutionSuggestionsResponse getResolutionSuggestions(Long id, String staffEmail) {
        Complaint complaint = findAssignedToStaff(id, staffEmail);
        List<Complaint> assignedComplaints = complaintRepository
                .findByAssignedStaffIdOrderByUpdatedAtDesc(complaint.getAssignedStaff().getId());
        return resolutionSuggestionService.suggest(complaint, assignedComplaints);
    }

    @Transactional
    public ComplaintResponse assignComplaint(Long id, AssignmentRequest request, String adminEmail) {
        Complaint complaint = findComplaint(id);
        Department department = departmentRepository.findById(request.departmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Department not found: " + request.departmentId()));
        User staff = userRepository.findById(request.staffId())
                .orElseThrow(() -> new ResourceNotFoundException("Staff user not found: " + request.staffId()));
        if (staff.getRole() != Role.STAFF) {
            throw new IllegalArgumentException("The selected user is not a staff member");
        }
        User admin = findUser(adminEmail);
        complaint.setDepartment(department);
        complaint.setAssignedStaff(staff);
        changeStatus(complaint, ComplaintStatus.ASSIGNED, request.remarks(), admin);
        return toResponse(complaintRepository.save(complaint));
    }

    @Transactional
    public ComplaintResponse updateAdminStatus(Long id, StatusUpdateRequest request, String adminEmail) {
        Complaint complaint = findComplaint(id);
        changeStatus(complaint, request.status(), request.remarks(), findUser(adminEmail));
        return toResponse(complaintRepository.save(complaint));
    }

    @Transactional(readOnly = true)
    public List<ComplaintResponse> getAssignedComplaints(String email) {
        User staff = findUser(email);
        return complaintRepository.findByAssignedStaffIdOrderByUpdatedAtDesc(staff.getId())
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ComplaintResponse getAssignedComplaint(Long id, String email) {
        return toResponse(findAssignedToStaff(id, email));
    }

    @Transactional
    public ComplaintResponse updateStaffStatus(Long id, StatusUpdateRequest request, String staffEmail) {
        Complaint complaint = findAssignedToStaff(id, staffEmail);
        if (!List.of(ComplaintStatus.IN_PROGRESS, ComplaintStatus.RESOLVED, ComplaintStatus.CLOSED, ComplaintStatus.REJECTED)
                .contains(request.status())) {
            throw new IllegalArgumentException("Staff cannot set the complaint to that status");
        }
        changeStatus(complaint, request.status(), request.remarks(), findUser(staffEmail));
        return toResponse(complaintRepository.save(complaint));
    }

    @Transactional
    public ComplaintResponse addStaffRemarks(Long id, RemarksRequest request, String staffEmail) {
        Complaint complaint = findAssignedToStaff(id, staffEmail);
        recordStatus(complaint, complaint.getStatus(), request.remarks().trim(), findUser(staffEmail));
        complaint.markUpdated();
        complaintRepository.save(complaint);
        return toResponse(complaint);
    }

    private Complaint findAssignedToStaff(Long id, String staffEmail) {
        Complaint complaint = findComplaint(id);
        if (complaint.getAssignedStaff() == null || !complaint.getAssignedStaff().getEmail().equalsIgnoreCase(staffEmail)) {
            throw new AccessDeniedException("This complaint is not assigned to you");
        }
        return complaint;
    }

    private void validateComplaintAccess(Complaint complaint, User requester) {
        if (requester.getRole() == Role.ADMIN) {
            return;
        }
        if (requester.getRole() == Role.STAFF) {
            if (complaint.getAssignedStaff() == null || !complaint.getAssignedStaff().getId().equals(requester.getId())) {
                throw new AccessDeniedException("This complaint is not assigned to you");
            }
            return;
        }
        if (!complaint.getUser().getId().equals(requester.getId())) {
            throw new AccessDeniedException("You can only view your own complaint details");
        }
    }

    private void changeStatus(Complaint complaint, ComplaintStatus next, String remarks, User updatedBy) {
        if (complaint.getStatus() != next) {
            complaint.setStatus(next);
            recordStatus(complaint, next, trimToNull(remarks), updatedBy);
        } else if (remarks != null && !remarks.isBlank()) {
            recordStatus(complaint, next, remarks.trim(), updatedBy);
        }
    }

    private void recordStatus(Complaint complaint, ComplaintStatus status, String remarks, User updatedBy) {
        ComplaintStatusHistory entry = historyRepository.save(
                new ComplaintStatusHistory(complaint, status, remarks, updatedBy));
        complaint.getStatusHistory().add(entry);
    }

    private Complaint findComplaint(Long id) {
        return complaintRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Complaint not found: " + id));
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private ComplaintResponse toResponse(Complaint complaint) {
        LocalDateTime now = LocalDateTime.now();
        Integer ageDays = complaint.getCreatedAt() == null
                ? null
                : (int) Math.min(Integer.MAX_VALUE, Math.max(0,
                ChronoUnit.DAYS.between(complaint.getCreatedAt(), now)));
        Boolean overdue = overdueAfterDays == 0
                ? null
                : ageDays != null
                && ACTIVE_STATUSES.contains(complaint.getStatus())
                && ageDays >= overdueAfterDays;
        List<StatusHistoryResponse> history = complaint.getStatusHistory().stream()
                .map(item -> new StatusHistoryResponse(
                        item.getId(),
                        item.getStatus(),
                        item.getRemarks(),
                        item.getUpdatedBy() == null ? null : item.getUpdatedBy().getId(),
                        item.getUpdatedBy() == null ? null : item.getUpdatedBy().getFullName(),
                        item.getUpdatedAt()))
                .toList();
        return new ComplaintResponse(
                complaint.getId(),
                complaint.getUser().getId(),
                complaint.getUser().getFullName(),
                complaint.getDepartment() == null ? null : complaint.getDepartment().getId(),
                complaint.getDepartment() == null ? null : complaint.getDepartment().getName(),
                complaint.getAssignedStaff() == null ? null : complaint.getAssignedStaff().getId(),
                complaint.getAssignedStaff() == null ? null : complaint.getAssignedStaff().getFullName(),
                complaint.getTitle(),
                complaint.getDescription(),
                complaint.getLocation(),
                complaint.getCategory(),
                complaint.getSeverity(),
                complaint.getPriority(),
                complaint.getStatus(),
                complaint.getCreatedAt(),
                complaint.getUpdatedAt(),
                history,
                ageDays,
                overdue,
                complaint.getCommunityArea(),
                complaint.isCommunityVisible()
        );
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
