package com.nagriksetu.complaint;

import com.nagriksetu.reference.Department;
import com.nagriksetu.reference.Ward;
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
@Table(name = "assignments")
public class Assignment {

    public enum Type { WARD, DEPARTMENT, OFFICER }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "complaint_id")
    private Complaint complaint;

    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_type", nullable = false)
    private Type type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ward_id")
    private Ward ward;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "officer_id")
    private User officer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by")
    private User assignedBy;

    private String note;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "ended_at")
    private Instant endedAt;

    protected Assignment() {
    }

    public static Assignment ward(Complaint c, Ward ward, User by, String note) {
        Assignment a = new Assignment(c, Type.WARD, by, note);
        a.ward = ward;
        return a;
    }

    public static Assignment department(Complaint c, Department department, User by, String note) {
        Assignment a = new Assignment(c, Type.DEPARTMENT, by, note);
        a.department = department;
        return a;
    }

    public static Assignment officer(Complaint c, User officer, User by, String note) {
        Assignment a = new Assignment(c, Type.OFFICER, by, note);
        a.officer = officer;
        return a;
    }

    private Assignment(Complaint complaint, Type type, User assignedBy, String note) {
        this.complaint = complaint;
        this.type = type;
        this.assignedBy = assignedBy;
        this.note = note;
    }

    public void end() {
        active = false;
        endedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Type getType() {
        return type;
    }

    public Ward getWard() {
        return ward;
    }

    public Department getDepartment() {
        return department;
    }

    public User getOfficer() {
        return officer;
    }

    public User getAssignedBy() {
        return assignedBy;
    }

    public String getNote() {
        return note;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }
}
