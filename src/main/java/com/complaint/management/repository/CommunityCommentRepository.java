package com.complaint.management.repository;

import com.complaint.management.entity.CommunityComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommunityCommentRepository extends JpaRepository<CommunityComment, Long> {
    List<CommunityComment> findByComplaintIdOrderByCreatedAtAsc(Long complaintId);
    List<CommunityComment> findByReportedTrueOrderByCreatedAtAsc();
    long countByComplaintId(Long complaintId);
}
