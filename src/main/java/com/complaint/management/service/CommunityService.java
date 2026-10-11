package com.complaint.management.service;

import com.complaint.management.dto.*;
import com.complaint.management.entity.*;
import com.complaint.management.exception.ResourceNotFoundException;
import com.complaint.management.repository.CommunityCommentRepository;
import com.complaint.management.repository.ComplaintRepository;
import com.complaint.management.repository.ComplaintSupportRepository;
import com.complaint.management.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class CommunityService {

    private final ComplaintRepository complaintRepository;
    private final CommunityCommentRepository commentRepository;
    private final ComplaintSupportRepository supportRepository;
    private final UserRepository userRepository;
    private final ComplaintSimilarityService similarityService;

    public CommunityService(
            ComplaintRepository complaintRepository,
            CommunityCommentRepository commentRepository,
            ComplaintSupportRepository supportRepository,
            UserRepository userRepository,
            ComplaintSimilarityService similarityService
    ) {
        this.complaintRepository = complaintRepository;
        this.commentRepository = commentRepository;
        this.supportRepository = supportRepository;
        this.userRepository = userRepository;
        this.similarityService = similarityService;
    }

    @Transactional(readOnly = true)
    public List<CommunityComplaintResponse> listComplaints(
            String email,
            String search,
            String category,
            String status,
            String area,
            String sort
    ) {
        User requester = findUser(email);
        String query = nullableTrimmed(search);
        String selectedCategory = nullableTrimmed(category);
        String selectedStatus = status == null || status.isBlank()
                ? null
                : status.trim().toUpperCase(Locale.ROOT);
        String selectedArea = nullableTrimmed(area);
        List<Complaint> filtered = (requester.getRole() == Role.STAFF
                ? complaintRepository.searchAssignedCommunityVisible(
                        requester.getId(), query, selectedCategory, selectedStatus, selectedArea)
                : complaintRepository.searchCommunityVisible(
                        query, selectedCategory, selectedStatus, selectedArea))
                .stream()
                .sorted(comparatorFor(sort))
                .toList();
        return filtered.stream().map(complaint -> toResponse(complaint, requester, false)).toList();
    }

    @Transactional(readOnly = true)
    public CommunityComplaintResponse getComplaint(Long complaintId, String email) {
        User requester = findUser(email);
        Complaint complaint = findVisibleComplaint(complaintId, requester);
        return toResponse(complaint, requester, true);
    }

    @Transactional
    public CommunityCommentResponse addComment(Long complaintId, CommunityCommentRequest request, String email) {
        User requester = findUser(email);
        Complaint complaint = findVisibleComplaint(complaintId, requester);
        String text = request.text().trim();
        CommunityComment comment = commentRepository.save(new CommunityComment(complaint, requester, text));
        return toCommentResponse(comment, requester);
    }

    @Transactional
    public CommunityComplaintResponse setSupport(Long complaintId, String email, boolean supported) {
        User requester = findUser(email);
        Complaint complaint = findVisibleComplaint(complaintId, requester);
        boolean alreadySupported = supportRepository.existsByComplaintIdAndUserId(complaintId, requester.getId());
        if (supported && !alreadySupported) {
            try {
                supportRepository.save(new ComplaintSupport(complaint, requester));
            } catch (DataIntegrityViolationException exception) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "You have already supported this complaint", exception);
            }
        } else if (!supported && alreadySupported) {
            supportRepository.deleteByComplaintIdAndUserId(complaintId, requester.getId());
        }
        return toResponse(complaint, requester, true);
    }

    @Transactional
    public void reportComment(Long commentId, String email) {
        User requester = findUser(email);
        CommunityComment comment = findComment(commentId);
        findVisibleComplaint(comment.getComplaint().getId(), requester);
        comment.setReported(true);
        commentRepository.save(comment);
    }

    @Transactional
    public void deleteComment(Long commentId, String email, boolean administrator) {
        User requester = findUser(email);
        CommunityComment comment = findComment(commentId);
        if (administrator) {
            requireAdmin(requester);
        } else {
            boolean authorOfOwnComment = comment.getAuthor().getId().equals(requester.getId());
            if (!(requester.getRole() == Role.USER && authorOfOwnComment)) {
                findVisibleComplaint(comment.getComplaint().getId(), requester);
            }
            if (!authorOfOwnComment) {
                throw new AccessDeniedException("You can only remove your own community comments");
            }
        }
        commentRepository.delete(comment);
    }

    @Transactional(readOnly = true)
    public List<ReportedCommunityCommentResponse> getReportedComments(String email) {
        User admin = findUser(email);
        requireAdmin(admin);
        return commentRepository.findByReportedTrueOrderByCreatedAtAsc().stream()
                .map(comment -> new ReportedCommunityCommentResponse(
                        comment.getId(),
                        comment.getComplaint().getId(),
                        comment.getComplaint().getTitle(),
                        comment.getText(),
                        comment.getAuthor().getFullName(),
                        comment.getCreatedAt()
                ))
                .toList();
    }

    @Transactional
    public boolean setComplaintVisibilityAsOwner(Long complaintId, boolean visible, String email) {
        User requester = findUser(email);
        Complaint complaint = findComplaint(complaintId);
        if (requester.getRole() != Role.USER || !complaint.getUser().getId().equals(requester.getId())) {
            throw new AccessDeniedException("You can only change community visibility on your own complaint");
        }
        complaint.setCommunityVisible(visible);
        complaintRepository.save(complaint);
        return complaint.isCommunityVisible();
    }

    @Transactional
    public boolean setComplaintVisibilityAsAdmin(Long complaintId, boolean visible, String email) {
        User admin = findUser(email);
        requireAdmin(admin);
        Complaint complaint = findComplaint(complaintId);
        complaint.setCommunityVisible(visible);
        complaintRepository.save(complaint);
        return complaint.isCommunityVisible();
    }

    @Transactional(readOnly = true)
    public List<ComplaintSimilarityResponse> findSimilarCommunityComplaints(CommunitySimilarRequest request, String email) {
        User requester = findUser(email);
        Complaint target = new Complaint();
        target.setTitle(request.title().trim());
        target.setDescription(request.description().trim());
        target.setCategory(request.category());
        return similarityService.findSimilarComplaints(target, visibleComplaintsFor(requester));
    }

    private List<Complaint> visibleComplaintsFor(User requester) {
        if (requester.getRole() == Role.STAFF) {
            return complaintRepository.findByCommunityVisibleTrueAndAssignedStaffIdOrderByCreatedAtDesc(requester.getId());
        }
        return complaintRepository.findByCommunityVisibleTrueOrderByCreatedAtDesc();
    }

    private Complaint findVisibleComplaint(Long id, User requester) {
        Complaint complaint = findComplaint(id);
        if (!complaint.isCommunityVisible()) {
            throw new ResourceNotFoundException("Community complaint not found: " + id);
        }
        if (requester.getRole() == Role.STAFF
                && (complaint.getAssignedStaff() == null
                || !complaint.getAssignedStaff().getId().equals(requester.getId()))) {
            throw new AccessDeniedException("This complaint is not assigned to you");
        }
        return complaint;
    }

    private Comparator<Complaint> comparatorFor(String sort) {
        String selectedSort = sort == null || sort.isBlank() ? "newest" : sort.trim().toLowerCase(Locale.ROOT);
        return switch (selectedSort) {
            case "newest" -> Comparator.comparing(Complaint::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
            case "supported" -> Comparator.<Complaint>comparingLong(
                    complaint -> supportRepository.countByComplaintId(complaint.getId())).reversed()
                    .thenComparing(Complaint::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
            case "updated" -> Comparator.comparing(Complaint::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
            default -> throw new IllegalArgumentException("Unsupported community complaint sort order");
        };
    }

    private CommunityComplaintResponse toResponse(Complaint complaint, User requester, boolean includeComments) {
        List<CommunityCommentResponse> comments = includeComments
                ? commentRepository.findByComplaintIdOrderByCreatedAtAsc(complaint.getId()).stream()
                .map(comment -> toCommentResponse(comment, requester))
                .toList()
                : List.of();
        return new CommunityComplaintResponse(
                complaint.getId(),
                complaint.getTitle(),
                complaint.getDescription(),
                complaint.getCommunityArea(),
                complaint.getCategory(),
                complaint.getStatus(),
                complaint.getPriority(),
                complaint.getCreatedAt(),
                complaint.getUpdatedAt(),
                supportRepository.countByComplaintId(complaint.getId()),
                supportRepository.existsByComplaintIdAndUserId(complaint.getId(), requester.getId()),
                commentRepository.countByComplaintId(complaint.getId()),
                comments
        );
    }

    private CommunityCommentResponse toCommentResponse(CommunityComment comment, User requester) {
        return new CommunityCommentResponse(
                comment.getId(),
                comment.getComplaint().getId(),
                comment.getText(),
                comment.getAuthor().getFullName(),
                comment.getCreatedAt(),
                comment.getAuthor().getId().equals(requester.getId())
        );
    }

    private CommunityComment findComment(Long id) {
        return commentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Community comment not found: " + id));
    }

    private Complaint findComplaint(Long id) {
        return complaintRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Complaint not found: " + id));
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private void requireAdmin(User user) {
        if (user.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Administrator permissions are required");
        }
    }

    private String nullableTrimmed(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
