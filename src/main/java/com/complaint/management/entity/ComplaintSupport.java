package com.complaint.management.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "complaint_supports", uniqueConstraints = {
        @UniqueConstraint(name = "uk_complaint_support_user_complaint", columnNames = {"user_id", "complaint_id"})
}, indexes = {
        @Index(name = "idx_complaint_support_complaint", columnList = "complaint_id")
})
public class ComplaintSupport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "complaint_id", nullable = false)
    private Complaint complaint;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    protected ComplaintSupport() {
    }

    public ComplaintSupport(Complaint complaint, User user) {
        this.complaint = complaint;
        this.user = user;
    }
}
