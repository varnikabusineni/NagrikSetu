package com.nagriksetu.complaint;

import com.nagriksetu.evidence.EvidenceType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ComplaintDtos {

    private ComplaintDtos() {
    }

    public record EvidenceDto(UUID id, EvidenceType type, String url, String contentType, String filename,
                              long sizeBytes, Instant createdAt, String uploadedByRole) {
    }

    public record Summary(Long id, String referenceNo, String title, String categoryCode, String categoryName,
                          ComplaintStatus status, Priority priority, String locality, BigDecimal latitude,
                          BigDecimal longitude, Instant createdAt, Instant updatedAt, int upvoteCount,
                          int followerCount, String thumbnailUrl, int photoCount, boolean upvoted,
                          boolean followed, boolean mine, String wardName, String departmentName,
                          String duplicateOfReference, Boolean wardVerificationPending) {
    }

    public record TimelineEntry(Long id, HistoryEventType eventType, ComplaintStatus fromStatus,
                                ComplaintStatus toStatus, String note, String actorRole, String actorName,
                                boolean internal, Instant createdAt) {
    }

    public record ResolutionDto(String summary, Instant resolvedAt, String resolvedByRole, String resolvedByName) {
    }

    public record FeedbackDto(int rating, String comment, Instant createdAt, Instant updatedAt) {
    }

    public record ReporterDto(String fullName, String email, String phone) {
    }

    public record AssignmentDto(String type, String target, String assignedByName, String note, boolean active,
                                Instant createdAt, Instant endedAt) {
    }

    public record Detail(
            Long id, String referenceNo, String title, String description, String categoryCode, String categoryName,
            ComplaintStatus status, Priority priority, String locality, String address, BigDecimal latitude,
            BigDecimal longitude, boolean isPublic, Instant createdAt, Instant updatedAt, Instant resolvedAt,
            WardMatch wardMatch, String wardName, String departmentName, String assignedOfficerName,
            String rejectionReason, String duplicateOfReference, Long duplicateOfId,
            int upvoteCount, int followerCount, boolean upvoted, boolean followed,
            ComplaintAccessPolicy.Access access,
            List<EvidenceDto> citizenEvidence, List<EvidenceDto> resolutionEvidence,
            List<TimelineEntry> timeline, ResolutionDto resolution, FeedbackDto feedback,
            ReporterDto reporter, List<AssignmentDto> assignments,
            boolean canGiveFeedback, boolean canRespond, boolean canParticipate,
            List<String> allowedActions) {
    }

    public record MapMarker(Long id, String referenceNo, String title, String categoryCode, String categoryName,
                            ComplaintStatus status, BigDecimal latitude, BigDecimal longitude, String locality,
                            Instant createdAt, int upvoteCount, boolean mine) {
    }

    public record Nearby(Long id, String referenceNo, String title, String categoryName, ComplaintStatus status,
                         String locality, Instant createdAt, int upvoteCount, long distanceMeters, String thumbnailUrl) {
    }
}
