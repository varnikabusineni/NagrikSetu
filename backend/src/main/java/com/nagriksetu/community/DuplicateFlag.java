package com.nagriksetu.community;

import com.nagriksetu.complaint.Complaint;
import com.nagriksetu.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "duplicate_flags")
public class DuplicateFlag {

    public enum Status { PENDING, CONFIRMED, DISMISSED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "complaint_id")
    private Complaint complaint;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "duplicate_of_id")
    private Complaint duplicateOf;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "flagged_by")
    private User flaggedBy;

    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected DuplicateFlag() {
    }

    public DuplicateFlag(Complaint complaint, Complaint duplicateOf, User flaggedBy, String reason) {
        this.complaint = complaint;
        this.duplicateOf = duplicateOf;
        this.flaggedBy = flaggedBy;
        this.reason = reason;
    }

    public void review(Status outcome, User reviewer) {
        this.status = outcome;
        this.reviewedBy = reviewer;
        this.reviewedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Complaint getComplaint() {
        return complaint;
    }

    public Complaint getDuplicateOf() {
        return duplicateOf;
    }

    public User getFlaggedBy() {
        return flaggedBy;
    }

    public String getReason() {
        return reason;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
