package com.nagriksetu.complaint;

import com.nagriksetu.common.ApiException;
import com.nagriksetu.security.UserPrincipal;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Single source of truth for who may see a complaint and its evidence. Evaluated on the server for
 * every request — hiding a button in the UI is never relied upon.
 */
@Component
public class ComplaintAccessPolicy {

    public enum Access {
        /** The citizen who reported the complaint. */
        OWNER,
        /** An official whose ward, department or assignment covers the complaint (or an administrator). */
        OFFICIAL,
        /** Any signed-in user viewing a public complaint: no personal details are exposed. */
        PUBLIC,
        NONE
    }

    public Access accessOf(UserPrincipal user, Complaint c) {
        if (user == null) {
            return Access.NONE;
        }
        if (Objects.equals(c.reporterId(), user.id())) {
            return Access.OWNER;
        }
        if (isOfficialInScope(user, c)) {
            return Access.OFFICIAL;
        }
        if (isPubliclyVisible(c)) {
            return Access.PUBLIC;
        }
        return Access.NONE;
    }

    public static boolean isPubliclyVisible(Complaint c) {
        return c.isPublicVisible() && c.getStatus() != ComplaintStatus.REJECTED;
    }

    public boolean isOfficialInScope(UserPrincipal user, Complaint c) {
        return switch (user.role()) {
            case ADMIN -> true;
            case WARD_HEAD -> user.wardId() != null && user.wardId().equals(c.wardId());
            case DEPARTMENT_HEAD -> user.departmentId() != null && user.departmentId().equals(c.departmentId());
            case OFFICER -> user.id().equals(c.assignedOfficerId());
            case CITIZEN -> false;
        };
    }

    public Access requireView(UserPrincipal user, Complaint c) {
        Access access = accessOf(user, c);
        if (access == Access.NONE) {
            // 404 rather than 403 so private complaint references cannot be probed.
            throw ApiException.notFound("Complaint");
        }
        return access;
    }

    public void requireOfficialScope(UserPrincipal user, Complaint c) {
        if (!isOfficialInScope(user, c)) {
            throw ApiException.forbidden("This complaint is outside your ward, department or assignments");
        }
    }
}
