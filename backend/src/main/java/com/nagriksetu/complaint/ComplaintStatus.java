package com.nagriksetu.complaint;

import java.util.EnumSet;
import java.util.Set;

public enum ComplaintStatus {
    SUBMITTED, UNDER_REVIEW, ASSIGNED, IN_PROGRESS, RESOLVED, REJECTED, REOPENED;

    public static final Set<ComplaintStatus> AWAITING_REVIEW = EnumSet.of(SUBMITTED, UNDER_REVIEW, REOPENED);
    public static final Set<ComplaintStatus> ACTIVE_WORK = EnumSet.of(ASSIGNED, IN_PROGRESS);
    public static final Set<ComplaintStatus> CLOSED = EnumSet.of(RESOLVED, REJECTED);

    public boolean isClosed() {
        return CLOSED.contains(this);
    }

    public String label() {
        return switch (this) {
            case SUBMITTED -> "Submitted";
            case UNDER_REVIEW -> "Under Review";
            case ASSIGNED -> "Assigned";
            case IN_PROGRESS -> "In Progress";
            case RESOLVED -> "Resolved";
            case REJECTED -> "Rejected";
            case REOPENED -> "Reopened";
        };
    }
}
