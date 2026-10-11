package com.complaint.management.repository;

import com.complaint.management.entity.ComplaintSupport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComplaintSupportRepository extends JpaRepository<ComplaintSupport, Long> {
    boolean existsByComplaintIdAndUserId(Long complaintId, Long userId);
    long countByComplaintId(Long complaintId);
    void deleteByComplaintIdAndUserId(Long complaintId, Long userId);
}
