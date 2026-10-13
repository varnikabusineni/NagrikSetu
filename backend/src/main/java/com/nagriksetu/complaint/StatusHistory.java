package com.nagriksetu.complaint;

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
@Table(name = "complaint_status_history")
public class StatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "complaint_id")
    private Complaint complaint;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private HistoryEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    private ComplaintStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private ComplaintStatus toStatus;

    private String note;

    @Column(name = "visible_to_citizen", nullable = false)
    private boolean visibleToCitizen = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected StatusHistory() {
    }

    public StatusHistory(Complaint complaint, HistoryEventType eventType, ComplaintStatus fromStatus,
                         ComplaintStatus toStatus, String note, boolean visibleToCitizen, User actor) {
        this.complaint = complaint;
        this.eventType = eventType;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.note = note;
        this.visibleToCitizen = visibleToCitizen;
        this.actor = actor;
    }

    public Long getId() {
        return id;
    }

    public Complaint getComplaint() {
        return complaint;
    }

    public HistoryEventType getEventType() {
        return eventType;
    }

    public ComplaintStatus getFromStatus() {
        return fromStatus;
    }

    public ComplaintStatus getToStatus() {
        return toStatus;
    }

    public String getNote() {
        return note;
    }

    public boolean isVisibleToCitizen() {
        return visibleToCitizen;
    }

    public User getActor() {
        return actor;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
