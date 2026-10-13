package com.nagriksetu.complaint;

import com.nagriksetu.common.ApiException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Complaint lifecycle: Submitted → Under Review → Assigned → In Progress → Resolved, with Rejected
 * (requires an official reason) and Reopened (by an authorised official) as side paths.
 * Only officials drive transitions; citizens have no endpoint that changes status.
 */
public final class ComplaintWorkflow {

    private static final Map<ComplaintStatus, Set<ComplaintStatus>> TRANSITIONS = new EnumMap<>(ComplaintStatus.class);

    static {
        TRANSITIONS.put(ComplaintStatus.SUBMITTED, EnumSet.of(ComplaintStatus.UNDER_REVIEW, ComplaintStatus.REJECTED));
        TRANSITIONS.put(ComplaintStatus.UNDER_REVIEW, EnumSet.of(ComplaintStatus.ASSIGNED, ComplaintStatus.REJECTED));
        TRANSITIONS.put(ComplaintStatus.ASSIGNED, EnumSet.of(ComplaintStatus.ASSIGNED, ComplaintStatus.IN_PROGRESS,
                ComplaintStatus.UNDER_REVIEW, ComplaintStatus.REJECTED));
        TRANSITIONS.put(ComplaintStatus.IN_PROGRESS, EnumSet.of(ComplaintStatus.RESOLVED, ComplaintStatus.ASSIGNED,
                ComplaintStatus.UNDER_REVIEW, ComplaintStatus.REJECTED));
        TRANSITIONS.put(ComplaintStatus.RESOLVED, EnumSet.of(ComplaintStatus.REOPENED));
        TRANSITIONS.put(ComplaintStatus.REJECTED, EnumSet.of(ComplaintStatus.REOPENED));
        TRANSITIONS.put(ComplaintStatus.REOPENED, EnumSet.of(ComplaintStatus.UNDER_REVIEW, ComplaintStatus.ASSIGNED,
                ComplaintStatus.IN_PROGRESS, ComplaintStatus.REJECTED));
    }

    private ComplaintWorkflow() {
    }

    public static boolean canTransition(ComplaintStatus from, ComplaintStatus to) {
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public static void requireTransition(ComplaintStatus from, ComplaintStatus to) {
        if (!canTransition(from, to)) {
            throw ApiException.conflict("INVALID_TRANSITION",
                    "A complaint that is " + from.label() + " cannot move to " + to.label());
        }
    }
}
