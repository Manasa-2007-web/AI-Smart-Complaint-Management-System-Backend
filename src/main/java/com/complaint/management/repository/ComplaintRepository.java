package com.complaint.management.repository;

import com.complaint.management.entity.Complaint;
import com.complaint.management.entity.ComplaintStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ComplaintRepository extends JpaRepository<Complaint, Long> {
    List<Complaint> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<Complaint> findByAssignedStaffIdOrderByUpdatedAtDesc(Long staffId);
    List<Complaint> findByCommunityVisibleTrueOrderByCreatedAtDesc();
    List<Complaint> findByCommunityVisibleTrueAndAssignedStaffIdOrderByCreatedAtDesc(Long staffId);

    @Query(value = """
            SELECT c.*
            FROM complaints c
            WHERE c.community_visible = TRUE
              AND (:search IS NULL
                   OR LOWER(c.title) LIKE CONCAT('%', LOWER(:search), '%')
                   OR LOWER(c.description) LIKE CONCAT('%', LOWER(:search), '%')
                   OR CAST(c.id AS CHAR) LIKE CONCAT('%', :search, '%'))
              AND (:category IS NULL OR LOWER(c.category) = LOWER(:category))
              AND (:status IS NULL OR c.status = :status)
              AND (:area IS NULL OR LOWER(c.community_area) = LOWER(:area))
            ORDER BY c.created_at DESC
            """, nativeQuery = true)
    List<Complaint> searchCommunityVisible(
            @Param("search") String search,
            @Param("category") String category,
            @Param("status") String status,
            @Param("area") String area
    );

    @Query(value = """
            SELECT c.*
            FROM complaints c
            WHERE c.community_visible = TRUE
              AND c.assigned_staff_id = :staffId
              AND (:search IS NULL
                   OR LOWER(c.title) LIKE CONCAT('%', LOWER(:search), '%')
                   OR LOWER(c.description) LIKE CONCAT('%', LOWER(:search), '%')
                   OR CAST(c.id AS CHAR) LIKE CONCAT('%', :search, '%'))
              AND (:category IS NULL OR LOWER(c.category) = LOWER(:category))
              AND (:status IS NULL OR c.status = :status)
              AND (:area IS NULL OR LOWER(c.community_area) = LOWER(:area))
            ORDER BY c.created_at DESC
            """, nativeQuery = true)
    List<Complaint> searchAssignedCommunityVisible(
            @Param("staffId") Long staffId,
            @Param("search") String search,
            @Param("category") String category,
            @Param("status") String status,
            @Param("area") String area
    );

    long countByStatus(ComplaintStatus status);
}
