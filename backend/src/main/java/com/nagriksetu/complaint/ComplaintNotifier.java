package com.nagriksetu.complaint;

import com.nagriksetu.community.CommunityRepository;
import com.nagriksetu.notification.NotificationService;
import com.nagriksetu.notification.NotificationType;
import com.nagriksetu.user.RoleCode;
import com.nagriksetu.user.User;
import com.nagriksetu.user.UserRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Fans complaint events out to the reporter, followers and the responsible officials. */
@Component
public class ComplaintNotifier {

    private final NotificationService notifications;
    private final CommunityRepository community;
    private final UserRepository users;

    public ComplaintNotifier(NotificationService notifications, CommunityRepository community, UserRepository users) {
        this.notifications = notifications;
        this.community = community;
        this.users = users;
    }

    public void reporterAndFollowers(Complaint c, Long actorId, NotificationType type, String title, String message) {
        if (!c.reporterId().equals(actorId)) {
            notifications.notify(c.getReporter(), c, type, title, message);
        }
        List<User> followers = users.findAllById(community.followerIds(c.getId())).stream()
                .filter(u -> !u.getId().equals(c.reporterId()))
                .toList();
        notifications.notifyAll(followers, actorId, c, NotificationType.FOLLOWED_UPDATE,
                "Update on " + c.getReferenceNo(), title + ": " + c.getTitle());
    }

    public void notifyReporterOnly(Complaint c, Long actorId, NotificationType type, String title, String message) {
        if (!c.reporterId().equals(actorId)) {
            notifications.notify(c.getReporter(), c, type, title, message);
        }
    }

    public void wardHeads(Complaint c, Long actorId, NotificationType type, String title, String message) {
        if (c.getWard() != null) {
            notifications.notifyAll(users.findActiveByRoleAndWard(RoleCode.WARD_HEAD, c.getWard().getId()),
                    actorId, c, type, title, message);
        }
    }

    public void departmentHeads(Complaint c, Long actorId, NotificationType type, String title, String message) {
        if (c.getDepartment() != null) {
            notifications.notifyAll(users.findActiveByRoleAndDepartment(RoleCode.DEPARTMENT_HEAD, c.getDepartment().getId()),
                    actorId, c, type, title, message);
        }
    }

    public void administrators(Complaint c, Long actorId, NotificationType type, String title, String message) {
        notifications.notifyAll(users.findActiveByRole(RoleCode.ADMIN), actorId, c, type, title, message);
    }

    public void officer(Complaint c, Long actorId, NotificationType type, String title, String message) {
        if (c.getAssignedOfficer() != null) {
            notifications.notifyAll(List.of(c.getAssignedOfficer()), actorId, c, type, title, message);
        }
    }

    /** Everyone currently responsible for the complaint: ward heads, department heads and the assigned officer. */
    public void responsibleOfficials(Complaint c, Long actorId, NotificationType type, String title, String message) {
        List<User> recipients = new ArrayList<>();
        if (c.getWard() != null) {
            recipients.addAll(users.findActiveByRoleAndWard(RoleCode.WARD_HEAD, c.getWard().getId()));
        } else {
            recipients.addAll(users.findActiveByRole(RoleCode.ADMIN));
        }
        if (c.getDepartment() != null) {
            recipients.addAll(users.findActiveByRoleAndDepartment(RoleCode.DEPARTMENT_HEAD, c.getDepartment().getId()));
        }
        if (c.getAssignedOfficer() != null) {
            recipients.add(c.getAssignedOfficer());
        }
        notifications.notifyAll(recipients, actorId, c, type, title, message);
    }
}
