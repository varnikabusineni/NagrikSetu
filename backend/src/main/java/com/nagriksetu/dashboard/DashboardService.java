package com.nagriksetu.dashboard;

import com.nagriksetu.complaint.ComplaintDtos;
import com.nagriksetu.complaint.ComplaintMapper;
import com.nagriksetu.complaint.ComplaintRepository;
import com.nagriksetu.complaint.ComplaintStatus;
import com.nagriksetu.notification.NotificationService;
import com.nagriksetu.security.UserPrincipal;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Dashboard figures, always computed from database records. */
@Service
public class DashboardService {

    public record CitizenTotals(long total, long awaitingReview, long inProgress, long resolved, long rejected) {
    }

    public record CitizenDashboard(CitizenTotals totals, Map<ComplaintStatus, Long> byStatus,
                                   List<ComplaintDtos.Summary> recentlyUpdated,
                                   List<NotificationService.NotificationDto> notifications, long unreadNotifications,
                                   List<ComplaintDtos.Summary> communityAttention) {
    }

    public record CommunityStats(Map<ComplaintStatus, Long> byStatus, Map<String, Long> byCategory,
                                 long resolvedLast30Days, long openIssues) {
    }

    private final ComplaintRepository complaints;
    private final ComplaintMapper mapper;
    private final NotificationService notifications;

    public DashboardService(ComplaintRepository complaints, ComplaintMapper mapper, NotificationService notifications) {
        this.complaints = complaints;
        this.mapper = mapper;
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public CitizenDashboard citizen(UserPrincipal user) {
        Map<ComplaintStatus, Long> byStatus = new EnumMap<>(ComplaintStatus.class);
        for (ComplaintStatus s : ComplaintStatus.values()) {
            byStatus.put(s, 0L);
        }
        for (Object[] row : complaints.countByStatusForReporter(user.id())) {
            byStatus.put((ComplaintStatus) row[0], (Long) row[1]);
        }
        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        CitizenTotals totals = new CitizenTotals(total,
                sum(byStatus, ComplaintStatus.AWAITING_REVIEW),
                sum(byStatus, ComplaintStatus.ACTIVE_WORK),
                byStatus.get(ComplaintStatus.RESOLVED),
                byStatus.get(ComplaintStatus.REJECTED));
        var recent = mapper.summaries(complaints.findRecentlyUpdatedForReporter(user.id(), PageRequest.of(0, 5)), user);
        var attention = mapper.summaries(complaints.findCommunityAttention(
                EnumSet.of(ComplaintStatus.RESOLVED, ComplaintStatus.REJECTED),
                Instant.now().minus(Duration.ofDays(60)), PageRequest.of(0, 5)), user);
        return new CitizenDashboard(totals, byStatus, recent, notifications.list(user.id(), false, 0, 5).items(),
                notifications.unreadCount(user.id()), attention);
    }

    /** Public, de-duplicated analytics: confirmed duplicates are never counted as separate incidents. */
    @Transactional(readOnly = true)
    public CommunityStats community() {
        Map<ComplaintStatus, Long> byStatus = new EnumMap<>(ComplaintStatus.class);
        for (Object[] row : complaints.communityCountsByStatus()) {
            byStatus.put((ComplaintStatus) row[0], (Long) row[1]);
        }
        Map<String, Long> byCategory = new LinkedHashMap<>();
        for (Object[] row : complaints.communityCountsByCategory()) {
            byCategory.put((String) row[0], (Long) row[1]);
        }
        long open = byStatus.entrySet().stream().filter(e -> !e.getKey().isClosed()).mapToLong(Map.Entry::getValue).sum();
        return new CommunityStats(byStatus, byCategory,
                complaints.countResolvedIndependentSince(Instant.now().minus(Duration.ofDays(30))), open);
    }

    private static long sum(Map<ComplaintStatus, Long> map, java.util.Set<ComplaintStatus> keys) {
        return keys.stream().mapToLong(k -> map.getOrDefault(k, 0L)).sum();
    }
}
