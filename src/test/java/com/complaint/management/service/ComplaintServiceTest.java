package com.complaint.management.service;

import com.complaint.management.dto.ComplaintRequest;
import com.complaint.management.dto.ComplaintResponse;
import com.complaint.management.dto.StatusUpdateRequest;
import com.complaint.management.entity.Complaint;
import com.complaint.management.entity.ComplaintStatus;
import com.complaint.management.entity.ComplaintStatusHistory;
import com.complaint.management.entity.Role;
import com.complaint.management.entity.User;
import com.complaint.management.repository.ComplaintRepository;
import com.complaint.management.repository.ComplaintStatusHistoryRepository;
import com.complaint.management.repository.DepartmentRepository;
import com.complaint.management.repository.UserRepository;
import com.complaint.management.dto.ComplaintSimilarityResponse;
import com.complaint.management.dto.ResolutionSuggestionsResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplaintServiceTest {

    @Mock
    private ComplaintRepository complaintRepository;

    @Mock
    private ComplaintStatusHistoryRepository historyRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ComplaintSimilarityService complaintSimilarityService;

    @Mock
    private ResolutionSuggestionService resolutionSuggestionService;

    @InjectMocks
    private ComplaintService complaintService;

    @Test
    void creatingComplaintUsesAuthenticatedUserAndRecordsSubmittedHistory() {
        User user = new User("Test User", "user@example.test", "encoded-password",
                com.complaint.management.entity.Role.USER);
        user.setId(17L);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(complaintRepository.save(any(Complaint.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(historyRepository.save(any(ComplaintStatusHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ComplaintResponse response = complaintService.createComplaint(
                new ComplaintRequest("Road damage", "Large pothole", "Main road", null, null),
                user.getEmail());

        assertEquals(ComplaintStatus.SUBMITTED, response.status());
        assertEquals(user.getId(), response.userId());
        assertEquals("Main road", response.location());
        assertNull(response.priority());
        assertEquals(1, response.statusHistory().size());
        assertEquals(ComplaintStatus.SUBMITTED, response.statusHistory().getFirst().status());
        verify(historyRepository).save(any(ComplaintStatusHistory.class));
    }

    @Test
    void creatingComplaintSavesProvidedPriority() {
        User user = new User("Test User", "user@example.test", "encoded-password",
                com.complaint.management.entity.Role.USER);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(complaintRepository.save(any(Complaint.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(historyRepository.save(any(ComplaintStatusHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        complaintService.createComplaint(
                new ComplaintRequest("Road damage", "Large pothole", "Main road", "TECHNICAL", null, "HIGH"),
                user.getEmail());

        ArgumentCaptor<Complaint> complaintCaptor = ArgumentCaptor.forClass(Complaint.class);
        verify(complaintRepository).save(complaintCaptor.capture());
        assertEquals("TECHNICAL", complaintCaptor.getValue().getCategory());
        assertEquals("HIGH", complaintCaptor.getValue().getPriority());
    }

    @Test
    void changingStatusRecordsWhoChangedItAndTheRemarks() {
        User admin = new User("Admin", "admin@example.test", "encoded-password",
                com.complaint.management.entity.Role.ADMIN);
        admin.setId(8L);
        User submitter = new User("Submitter", "user@example.test", "encoded-password",
                com.complaint.management.entity.Role.USER);
        Complaint complaint = new Complaint();
        complaint.setUser(submitter);
        complaint.setTitle("Water leak");
        complaint.setDescription("Leak at the street");
        complaint.setStatus(ComplaintStatus.ASSIGNED);

        when(complaintRepository.findById(42L)).thenReturn(Optional.of(complaint));
        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(complaintRepository.save(any(Complaint.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(historyRepository.save(any(ComplaintStatusHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ComplaintResponse response = complaintService.updateAdminStatus(
                42L, new StatusUpdateRequest(ComplaintStatus.RESOLVED, "Repair completed"), admin.getEmail());

        assertEquals(ComplaintStatus.RESOLVED, response.status());
        ArgumentCaptor<ComplaintStatusHistory> historyCaptor = ArgumentCaptor.forClass(ComplaintStatusHistory.class);
        verify(historyRepository).save(historyCaptor.capture());
        assertEquals(ComplaintStatus.RESOLVED, historyCaptor.getValue().getStatus());
        assertEquals("Repair completed", historyCaptor.getValue().getRemarks());
        assertEquals(admin, historyCaptor.getValue().getUpdatedBy());
    }

    @Test
    void similaritySearchForStaffOnlyUsesComplaintsAssignedToThatStaffMember() {
        User staff = new User("Staff", "staff@example.test", "encoded-password", Role.STAFF);
        staff.setId(12L);
        Complaint target = new Complaint();
        target.setUser(new User("Submitter", "user@example.test", "encoded-password", Role.USER));
        target.setAssignedStaff(staff);
        ReflectionTestUtils.setField(target, "id", 42L);
        when(complaintRepository.findById(42L)).thenReturn(Optional.of(target));
        when(userRepository.findByEmail(staff.getEmail())).thenReturn(Optional.of(staff));
        when(complaintRepository.findByAssignedStaffIdOrderByUpdatedAtDesc(staff.getId()))
                .thenReturn(java.util.List.of(target));
        when(complaintSimilarityService.findSimilarComplaints(same(target), any()))
                .thenReturn(java.util.List.of());

        complaintService.getSimilarComplaints(42L, staff.getEmail());

        verify(complaintRepository).findByAssignedStaffIdOrderByUpdatedAtDesc(staff.getId());
        verify(complaintRepository, never()).findAll();
        verify(complaintRepository, never()).findByUserIdOrderByCreatedAtDesc(staff.getId());
        verify(complaintSimilarityService).findSimilarComplaints(same(target), any());
    }

    @Test
    void similaritySearchForStaffRejectsComplaintsNotAssignedToThem() {
        User staff = new User("Staff", "staff@example.test", "encoded-password", Role.STAFF);
        staff.setId(12L);
        User otherStaff = new User("Other Staff", "other@example.test", "encoded-password", Role.STAFF);
        otherStaff.setId(13L);
        Complaint target = new Complaint();
        target.setUser(new User("Submitter", "user@example.test", "encoded-password", Role.USER));
        target.setAssignedStaff(otherStaff);
        ReflectionTestUtils.setField(target, "id", 42L);
        when(complaintRepository.findById(42L)).thenReturn(Optional.of(target));
        when(userRepository.findByEmail(staff.getEmail())).thenReturn(Optional.of(staff));

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> complaintService.getSimilarComplaints(42L, staff.getEmail()));

        verify(complaintRepository, never()).findByAssignedStaffIdOrderByUpdatedAtDesc(staff.getId());
        verify(complaintSimilarityService, never()).findSimilarComplaints(any(), any());
    }

    @Test
    void resolutionSuggestionsForStaffUseOnlyTheirAssignedComplaints() {
        User staff = new User("Staff", "staff@example.test", "encoded-password", Role.STAFF);
        staff.setId(12L);
        Complaint target = new Complaint();
        target.setUser(new User("Submitter", "user@example.test", "encoded-password", Role.USER));
        target.setAssignedStaff(staff);
        ReflectionTestUtils.setField(target, "id", 42L);
        java.util.List<Complaint> assignedComplaints = java.util.List.of(target);
        ResolutionSuggestionsResponse expected = new ResolutionSuggestionsResponse(java.util.List.of(), java.util.List.of("Check safely."));
        when(complaintRepository.findById(42L)).thenReturn(Optional.of(target));
        when(complaintRepository.findByAssignedStaffIdOrderByUpdatedAtDesc(staff.getId()))
                .thenReturn(assignedComplaints);
        when(resolutionSuggestionService.suggest(same(target), same(assignedComplaints))).thenReturn(expected);

        ResolutionSuggestionsResponse response = complaintService.getResolutionSuggestions(42L, staff.getEmail());

        assertEquals(expected, response);
        verify(complaintRepository).findByAssignedStaffIdOrderByUpdatedAtDesc(staff.getId());
        verify(complaintRepository, never()).findAll();
        verify(resolutionSuggestionService).suggest(same(target), same(assignedComplaints));
    }

    @Test
    void resolutionSuggestionsRejectUnassignedComplaintBeforeLoadingCandidates() {
        User staff = new User("Staff", "staff@example.test", "encoded-password", Role.STAFF);
        staff.setId(12L);
        User otherStaff = new User("Other Staff", "other@example.test", "encoded-password", Role.STAFF);
        otherStaff.setId(13L);
        Complaint target = new Complaint();
        target.setUser(new User("Submitter", "user@example.test", "encoded-password", Role.USER));
        target.setAssignedStaff(otherStaff);
        ReflectionTestUtils.setField(target, "id", 42L);
        when(complaintRepository.findById(42L)).thenReturn(Optional.of(target));

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> complaintService.getResolutionSuggestions(42L, staff.getEmail()));

        verify(complaintRepository, never()).findByAssignedStaffIdOrderByUpdatedAtDesc(staff.getId());
        verify(resolutionSuggestionService, never()).suggest(any(), any());
    }

    @Test
    void similaritySearchForUsersOnlyUsesTheirOwnComplaints() {
        User user = new User("User", "user@example.test", "encoded-password", Role.USER);
        user.setId(17L);
        Complaint target = new Complaint();
        target.setUser(user);
        ReflectionTestUtils.setField(target, "id", 42L);
        java.util.List<Complaint> visibleComplaints = java.util.List.of(target);
        when(complaintRepository.findById(42L)).thenReturn(Optional.of(target));
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(complaintRepository.findByUserIdOrderByCreatedAtDesc(user.getId())).thenReturn(visibleComplaints);
        when(complaintSimilarityService.findSimilarComplaints(same(target), same(visibleComplaints)))
                .thenReturn(java.util.List.of());

        complaintService.getSimilarComplaints(42L, user.getEmail());

        verify(complaintRepository).findByUserIdOrderByCreatedAtDesc(user.getId());
        verify(complaintRepository, never()).findAll();
        verify(complaintSimilarityService).findSimilarComplaints(same(target), same(visibleComplaints));
    }

    @Test
    void similaritySearchForAdminsCanUseAllComplaints() {
        User admin = new User("Admin", "admin@example.test", "encoded-password", Role.ADMIN);
        Complaint target = new Complaint();
        target.setUser(new User("Submitter", "user@example.test", "encoded-password", Role.USER));
        ReflectionTestUtils.setField(target, "id", 42L);
        java.util.List<Complaint> visibleComplaints = java.util.List.of(target);
        when(complaintRepository.findById(42L)).thenReturn(Optional.of(target));
        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(complaintRepository.findAll()).thenReturn(visibleComplaints);
        when(complaintSimilarityService.findSimilarComplaints(same(target), same(visibleComplaints)))
                .thenReturn(java.util.List.<ComplaintSimilarityResponse>of());

        complaintService.getSimilarComplaints(42L, admin.getEmail());

        verify(complaintRepository).findAll();
        verify(complaintSimilarityService).findSimilarComplaints(same(target), same(visibleComplaints));
    }

    @Test
    void responseCalculatesAgeAndConfiguredOverdueForActiveComplaints() {
        User user = new User("User", "user@example.test", "encoded-password", Role.USER);
        user.setId(17L);
        Complaint complaint = new Complaint();
        complaint.setUser(user);
        complaint.setStatus(ComplaintStatus.IN_PROGRESS);
        ReflectionTestUtils.setField(complaint, "id", 42L);
        ReflectionTestUtils.setField(complaint, "createdAt", LocalDateTime.now().minusDays(9));
        when(complaintRepository.findById(42L)).thenReturn(Optional.of(complaint));
        ReflectionTestUtils.setField(complaintService, "overdueAfterDays", 7);

        ComplaintResponse response = complaintService.getMyComplaint(42L, user.getEmail());

        assertTrue(response.ageDays() >= 9);
        assertTrue(response.overdue());
    }

    @Test
    void terminalComplaintsAreNotOverdueAndDisabledTrackingIsUnreported() {
        User user = new User("User", "user@example.test", "encoded-password", Role.USER);
        user.setId(17L);
        Complaint complaint = new Complaint();
        complaint.setUser(user);
        complaint.setStatus(ComplaintStatus.RESOLVED);
        ReflectionTestUtils.setField(complaint, "id", 42L);
        ReflectionTestUtils.setField(complaint, "createdAt", LocalDateTime.now().minusDays(30));
        when(complaintRepository.findById(42L)).thenReturn(Optional.of(complaint));
        ReflectionTestUtils.setField(complaintService, "overdueAfterDays", 7);

        ComplaintResponse resolvedResponse = complaintService.getMyComplaint(42L, user.getEmail());
        assertFalse(resolvedResponse.overdue());

        ReflectionTestUtils.setField(complaintService, "overdueAfterDays", 0);
        ComplaintResponse disabledResponse = complaintService.getMyComplaint(42L, user.getEmail());
        assertNull(disabledResponse.overdue());
    }
}
