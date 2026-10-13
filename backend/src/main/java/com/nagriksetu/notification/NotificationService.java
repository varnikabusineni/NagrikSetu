package com.nagriksetu.notification;

import com.nagriksetu.common.ApiException;
import com.nagriksetu.common.PageResponse;
import com.nagriksetu.complaint.Complaint;
import com.nagriksetu.user.User;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists in-app notifications. Delivery is in-app only (the web client polls); no SMS or email
 * provider is configured in this project.
 */
@Service
public class NotificationService {

    public record NotificationDto(Long id, NotificationType type, String title, String message, Long complaintId,
                                  String complaintReference, boolean read, Instant createdAt) {

        static NotificationDto from(Notification n) {
            return new NotificationDto(n.getId(), n.getType(), n.getTitle(), n.getMessage(),
                    n.getComplaint() == null ? null : n.getComplaint().getId(),
                    n.getComplaint() == null ? null : n.getComplaint().getReferenceNo(),
                    n.getReadAt() != null, n.getCreatedAt());
        }
    }

    private final NotificationRepository repository;

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void notify(User user, Complaint complaint, NotificationType type, String title, String message) {
        if (user != null && user.isEnabled()) {
            repository.save(new Notification(user, complaint, type, title, message));
        }
    }

    /** Notifies each distinct user once, skipping the actor who caused the event. */
    @Transactional
    public void notifyAll(Collection<User> users, Long actorId, Complaint complaint, NotificationType type,
                          String title, String message) {
        Map<Long, User> distinct = new LinkedHashMap<>();
        users.forEach(u -> {
            if (u != null && !u.getId().equals(actorId)) {
                distinct.putIfAbsent(u.getId(), u);
            }
        });
        distinct.values().forEach(u -> notify(u, complaint, type, title, message));
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationDto> list(Long userId, boolean unreadOnly, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50));
        var result = unreadOnly ? repository.findUnreadForUser(userId, pageable) : repository.findForUser(userId, pageable);
        return PageResponse.of(result, NotificationDto::from);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return repository.countUnread(userId);
    }

    @Transactional
    public void markRead(Long userId, Long notificationId) {
        repository.findOwned(notificationId, userId)
                .orElseThrow(() -> ApiException.notFound("Notification"))
                .markRead();
    }

    @Transactional
    public int markAllRead(Long userId) {
        return repository.markAllRead(userId, Instant.now());
    }
}
