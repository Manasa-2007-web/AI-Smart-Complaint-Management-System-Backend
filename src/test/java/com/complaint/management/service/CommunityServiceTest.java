package com.complaint.management.service;

import com.complaint.management.dto.CommunityCommentRequest;
import com.complaint.management.dto.CommunityComplaintResponse;
import com.complaint.management.entity.*;
import com.complaint.management.exception.ResourceNotFoundException;
import com.complaint.management.repository.CommunityCommentRepository;
import com.complaint.management.repository.ComplaintRepository;
import com.complaint.management.repository.ComplaintSupportRepository;
import com.complaint.management.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import jakarta.persistence.Table;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommunityServiceTest {

    @Mock
    private ComplaintRepository complaintRepository;
    @Mock
    private CommunityCommentRepository commentRepository;
    @Mock
    private ComplaintSupportRepository supportRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ComplaintSimilarityService similarityService;

    private CommunityService service;

    @BeforeEach
    void setUp() {
        service = new CommunityService(
                complaintRepository,
                commentRepository,
                supportRepository,
                userRepository,
                similarityService
        );
    }

    @Test
    void privateComplaintIsNotAvailableInCommunityDetails() {
        User requester = user(5L, Role.USER);
        Complaint complaint = complaint(42L, false, requester, null);
        when(userRepository.findByEmail("user@example.test")).thenReturn(Optional.of(requester));
        when(complaintRepository.findById(42L)).thenReturn(Optional.of(complaint));

        assertThrows(ResourceNotFoundException.class, () -> service.getComplaint(42L, "user@example.test"));
        verifyNoInteractions(commentRepository, supportRepository);
    }

    @Test
    void staffCannotOpenCommunityComplaintThatIsNotAssignedToThem() {
        User staff = user(6L, Role.STAFF);
        User otherStaff = user(7L, Role.STAFF);
        Complaint complaint = complaint(42L, true, user(8L, Role.USER), otherStaff);
        when(userRepository.findByEmail("staff@example.test")).thenReturn(Optional.of(staff));
        when(complaintRepository.findById(42L)).thenReturn(Optional.of(complaint));

        assertThrows(AccessDeniedException.class, () -> service.getComplaint(42L, "staff@example.test"));
        verifyNoInteractions(commentRepository, supportRepository);
    }

    @Test
    void repeatedSupportDoesNotInsertAnotherRecord() {
        User requester = user(5L, Role.USER);
        Complaint complaint = complaint(42L, true, requester, null);
        when(userRepository.findByEmail("user@example.test")).thenReturn(Optional.of(requester));
        when(complaintRepository.findById(42L)).thenReturn(Optional.of(complaint));
        when(supportRepository.existsByComplaintIdAndUserId(42L, 5L)).thenReturn(true);
        when(supportRepository.countByComplaintId(42L)).thenReturn(1L);
        when(commentRepository.countByComplaintId(42L)).thenReturn(0L);
        when(commentRepository.findByComplaintIdOrderByCreatedAtAsc(42L)).thenReturn(List.of());

        CommunityComplaintResponse response = service.setSupport(42L, "user@example.test", true);

        assertEquals(1L, response.supportCount());
        assertTrue(response.supportedByCurrentUser());
        verify(supportRepository, never()).save(any(ComplaintSupport.class));
    }

    @Test
    void supportTableDeclaresUniqueUserComplaintPair() {
        Table table = ComplaintSupport.class.getAnnotation(Table.class);
        assertNotNull(table);
        assertTrue(List.of(table.uniqueConstraints()).stream()
                .anyMatch(constraint -> List.of(constraint.columnNames()).containsAll(List.of("user_id", "complaint_id"))));
    }

    @Test
    void communitySearchDelegatesTextIdAndCombinedFiltersToVisibleComplaintQuery() {
        User requester = user(5L, Role.USER);
        when(userRepository.findByEmail("user@example.test")).thenReturn(Optional.of(requester));
        when(complaintRepository.searchCommunityVisible("42", "Roads and Transport", "IN_PROGRESS", "North Campus"))
                .thenReturn(List.of());

        List<CommunityComplaintResponse> result = service.listComplaints(
                "user@example.test",
                " 42 ",
                "Roads and Transport",
                "in_progress",
                " North Campus ",
                "newest"
        );

        assertTrue(result.isEmpty());
        verify(complaintRepository).searchCommunityVisible("42", "Roads and Transport", "IN_PROGRESS", "North Campus");
        verify(complaintRepository, never()).searchAssignedCommunityVisible(
                anyLong(), any(), any(), any(), any());
    }

    @Test
    void staffCommunitySearchUsesAssignedVisibleComplaintQuery() {
        User staff = user(6L, Role.STAFF);
        when(userRepository.findByEmail("staff@example.test")).thenReturn(Optional.of(staff));
        when(complaintRepository.searchAssignedCommunityVisible(6L, "leak", null, null, null))
                .thenReturn(List.of());

        List<CommunityComplaintResponse> result = service.listComplaints(
                "staff@example.test", "leak", null, null, null, "newest");

        assertTrue(result.isEmpty());
        verify(complaintRepository).searchAssignedCommunityVisible(6L, "leak", null, null, null);
        verify(complaintRepository, never()).searchCommunityVisible(any(), any(), any(), any());
    }

    @Test
    void userCannotChangeVisibilityForAnotherUsersComplaint() {
        User requester = user(5L, Role.USER);
        Complaint complaint = complaint(42L, false, user(8L, Role.USER), null);
        when(userRepository.findByEmail("user@example.test")).thenReturn(Optional.of(requester));
        when(complaintRepository.findById(42L)).thenReturn(Optional.of(complaint));

        assertThrows(AccessDeniedException.class,
                () -> service.setComplaintVisibilityAsOwner(42L, true, "user@example.test"));
        verify(complaintRepository, never()).save(any(Complaint.class));
    }

    @Test
    void userCannotDeleteAnotherUsersComment() {
        User requester = user(5L, Role.USER);
        User author = user(8L, Role.USER);
        Complaint complaint = complaint(42L, true, requester, null);
        CommunityComment comment = mock(CommunityComment.class);
        when(userRepository.findByEmail("user@example.test")).thenReturn(Optional.of(requester));
        when(commentRepository.findById(77L)).thenReturn(Optional.of(comment));
        when(complaintRepository.findById(42L)).thenReturn(Optional.of(complaint));
        when(comment.getComplaint()).thenReturn(complaint);
        when(comment.getAuthor()).thenReturn(author);
        when(complaint.getId()).thenReturn(42L);

        assertThrows(AccessDeniedException.class,
                () -> service.deleteComment(77L, "user@example.test", false));
        verify(commentRepository, never()).delete(comment);
    }

    private User user(Long id, Role role) {
        User user = mock(User.class);
        lenient().when(user.getId()).thenReturn(id);
        lenient().when(user.getRole()).thenReturn(role);
        return user;
    }

    private Complaint complaint(Long id, boolean visible, User owner, User assignedStaff) {
        Complaint complaint = mock(Complaint.class);
        lenient().when(complaint.getId()).thenReturn(id);
        lenient().when(complaint.isCommunityVisible()).thenReturn(visible);
        lenient().when(complaint.getUser()).thenReturn(owner);
        lenient().when(complaint.getAssignedStaff()).thenReturn(assignedStaff);
        return complaint;
    }
}
