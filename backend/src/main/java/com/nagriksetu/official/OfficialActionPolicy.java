package com.nagriksetu.official;

import com.nagriksetu.common.ApiException;
import com.nagriksetu.complaint.Complaint;
import com.nagriksetu.complaint.ComplaintAccessPolicy;
import com.nagriksetu.complaint.ComplaintStatus;
import com.nagriksetu.complaint.WardMatch;
import com.nagriksetu.security.UserPrincipal;
import com.nagriksetu.user.RoleCode;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Decides which workflow actions an official may take on a complaint. Enforced on every action endpoint. */
@Component
public class OfficialActionPolicy {

    private static final Set<ComplaintStatus> ROUTABLE = EnumSet.of(ComplaintStatus.SUBMITTED,
            ComplaintStatus.UNDER_REVIEW, ComplaintStatus.REOPENED, ComplaintStatus.ASSIGNED, ComplaintStatus.IN_PROGRESS);
    private static final Set<ComplaintStatus> ASSIGNABLE = EnumSet.of(ComplaintStatus.UNDER_REVIEW,
            ComplaintStatus.REOPENED, ComplaintStatus.ASSIGNED, ComplaintStatus.IN_PROGRESS);

    private final ComplaintAccessPolicy accessPolicy;

    public OfficialActionPolicy(ComplaintAccessPolicy accessPolicy) {
        this.accessPolicy = accessPolicy;
    }

    public List<String> allowedActions(UserPrincipal user, Complaint c) {
        List<String> actions = new ArrayList<>();
        for (OfficialAction action : OfficialAction.values()) {
            if (isAllowed(user, c, action)) {
                actions.add(action.name());
            }
        }
        return actions;
    }

    public void require(UserPrincipal user, Complaint c, OfficialAction action) {
        if (!accessPolicy.isOfficialInScope(user, c)) {
            throw ApiException.forbidden("This complaint is outside your ward, department or assignments");
        }
        if (!isAllowed(user, c, action)) {
            throw ApiException.forbidden("You cannot " + action.name().toLowerCase().replace('_', ' ')
                    + " this complaint in its current state (" + c.getStatus().label() + ")");
        }
    }

    public boolean isAllowed(UserPrincipal user, Complaint c, OfficialAction action) {
        if (!user.isOfficial() || !accessPolicy.isOfficialInScope(user, c)) {
            return false;
        }
        ComplaintStatus s = c.getStatus();
        boolean closed = s.isClosed();
        boolean hasWard = c.getWard() != null;
        boolean hasDept = c.getDepartment() != null;
        boolean worksOnIt = user.has("COMPLAINT_WORK")
                && (user.id().equals(c.assignedOfficerId())
                    || (user.is(RoleCode.DEPARTMENT_HEAD) && user.departmentId() != null && user.departmentId().equals(c.departmentId())));
        return switch (action) {
            case VERIFY_WARD -> user.has("WARD_VERIFY") && c.getWardMatch() == WardMatch.PENDING_VERIFICATION && !closed;
            case REVIEW -> user.has("WARD_QUEUE_MANAGE") && hasWard && s == ComplaintStatus.SUBMITTED;
            case ROUTE -> user.has("WARD_QUEUE_MANAGE") && hasWard && ROUTABLE.contains(s);
            case ASSIGN_OFFICER -> user.has("DEPARTMENT_QUEUE_MANAGE") && hasDept && ASSIGNABLE.contains(s);
            case START_WORK -> worksOnIt && c.getAssignedOfficer() != null
                    && (s == ComplaintStatus.ASSIGNED || s == ComplaintStatus.REOPENED);
            case RESOLVE -> worksOnIt && s == ComplaintStatus.IN_PROGRESS;
            case REJECT -> user.has("COMPLAINT_REJECT") && !closed;
            case REOPEN -> user.has("COMPLAINT_REOPEN") && closed;
            case REQUEST_INFO -> !closed;
            case POST_UPDATE -> true;
            case SET_PRIORITY -> (user.has("WARD_QUEUE_MANAGE") || user.has("DEPARTMENT_QUEUE_MANAGE")) && !closed;
        };
    }
}
