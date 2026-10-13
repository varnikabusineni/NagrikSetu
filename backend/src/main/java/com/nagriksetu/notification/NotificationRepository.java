package com.nagriksetu.notification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query(value = "select n from Notification n left join fetch n.complaint where n.user.id = :userId order by n.createdAt desc, n.id desc",
            countQuery = "select count(n) from Notification n where n.user.id = :userId")
    Page<Notification> findForUser(Long userId, Pageable pageable);

    @Query(value = "select n from Notification n left join fetch n.complaint where n.user.id = :userId and n.readAt is null order by n.createdAt desc, n.id desc",
            countQuery = "select count(n) from Notification n where n.user.id = :userId and n.readAt is null")
    Page<Notification> findUnreadForUser(Long userId, Pageable pageable);

    @Query("select count(n) from Notification n where n.user.id = :userId and n.readAt is null")
    long countUnread(Long userId);

    @Query("select n from Notification n where n.id = :id and n.user.id = :userId")
    Optional<Notification> findOwned(Long id, Long userId);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.user.id = :userId and n.readAt is null")
    int markAllRead(Long userId, Instant now);

    List<Notification> findByUserIdAndComplaintId(Long userId, Long complaintId);
}
