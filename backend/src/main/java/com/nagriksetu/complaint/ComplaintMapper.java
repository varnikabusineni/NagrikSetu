package com.nagriksetu.complaint;

import com.nagriksetu.community.CommunityRepository;
import com.nagriksetu.complaint.ComplaintAccessPolicy.Access;
import com.nagriksetu.evidence.Evidence;
import com.nagriksetu.evidence.EvidenceRepository;
import com.nagriksetu.evidence.EvidenceType;
import com.nagriksetu.evidence.EvidenceUrlSigner;
import com.nagriksetu.feedback.FeedbackRepository;
import com.nagriksetu.official.OfficialActionPolicy;
import com.nagriksetu.security.UserPrincipal;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Converts complaints to API responses, exposing only what the viewer's access level allows.
 * Public viewers never receive reporter identity, internal notes, official names or file names.
 */
@Component
public class ComplaintMapper {

    private static final Set<HistoryEventType> PUBLIC_EVENTS = EnumSet.of(HistoryEventType.CREATED,
            HistoryEventType.STATUS_CHANGE, HistoryEventType.ROUTED, HistoryEventType.PUBLIC_UPDATE,
            HistoryEventType.DUPLICATE_CONFIRMED);

    private final EvidenceRepository evidenceRepository;
    private final EvidenceUrlSigner urlSigner;
    private final StatusHistoryRepository historyRepository;
    private final ResolutionRepository resolutionRepository;
    private final FeedbackRepository feedbackRepository;
    private final AssignmentRepository assignmentRepository;
    private final CommunityRepository communityRepository;
    private final OfficialActionPolicy actionPolicy;

    public ComplaintMapper(EvidenceRepository evidenceRepository, EvidenceUrlSigner urlSigner,
                           StatusHistoryRepository historyRepository, ResolutionRepository resolutionRepository,
                           FeedbackRepository feedbackRepository, AssignmentRepository assignmentRepository,
                           CommunityRepository communityRepository, OfficialActionPolicy actionPolicy) {
        this.evidenceRepository = evidenceRepository;
        this.urlSigner = urlSigner;
        this.historyRepository = historyRepository;
        this.resolutionRepository = resolutionRepository;
        this.feedbackRepository = feedbackRepository;
        this.assignmentRepository = assignmentRepository;
        this.communityRepository = communityRepository;
        this.actionPolicy = actionPolicy;
    }

    public List<ComplaintDtos.Summary> summaries(Collection<Complaint> complaints, UserPrincipal viewer) {
        if (complaints.isEmpty()) {
            return List.of();
        }
        List<Long> ids = complaints.stream().map(Complaint::getId).toList();
        Map<Long, List<Evidence>> photos = new HashMap<>();
        evidenceRepository.findByComplaintsAndType(ids, EvidenceType.CITIZEN_REPORT)
                .forEach(e -> photos.computeIfAbsent(e.getComplaint().getId(), k -> new java.util.ArrayList<>()).add(e));
        Set<Long> upvoted = communityRepository.upvotedAmong(viewer.id(), ids);
        Set<Long> followed = communityRepository.followedAmong(viewer.id(), ids);
        return complaints.stream().map(c -> {
            List<Evidence> list = photos.getOrDefault(c.getId(), List.of());
            boolean mine = c.reporterId().equals(viewer.id());
            boolean privileged = mine || viewer.isOfficial();
            return new ComplaintDtos.Summary(c.getId(), c.getReferenceNo(), c.getTitle(), c.getCategory().getCode(),
                    c.getCategory().getName(), c.getStatus(), c.getPriority(), c.getLocality(), c.getLatitude(),
                    c.getLongitude(), c.getCreatedAt(), c.getUpdatedAt(), c.getUpvoteCount(), c.getFollowerCount(),
                    list.isEmpty() ? null : urlSigner.signedUrl(list.get(0).getId(), viewer.id()), list.size(),
                    upvoted.contains(c.getId()), followed.contains(c.getId()), mine,
                    c.getWard() == null ? null : c.getWard().getName(),
                    c.getDepartment() == null ? null : c.getDepartment().getName(),
                    c.getDuplicateOf() == null ? null : c.getDuplicateOf().getReferenceNo(),
                    privileged ? c.getWardMatch() == WardMatch.PENDING_VERIFICATION : null);
        }).toList();
    }

    public ComplaintDtos.Detail detail(Complaint c, UserPrincipal viewer, Access access) {
        boolean owner = access == Access.OWNER;
        boolean official = access == Access.OFFICIAL;

        List<Evidence> evidence = evidenceRepository.findByComplaint(c.getId());
        List<ComplaintDtos.EvidenceDto> citizenEvidence = evidence.stream()
                .filter(e -> e.getType() == EvidenceType.CITIZEN_REPORT).map(e -> evidenceDto(e, viewer, access)).toList();
        List<ComplaintDtos.EvidenceDto> resolutionEvidence = evidence.stream()
                .filter(e -> e.getType() == EvidenceType.RESOLUTION).map(e -> evidenceDto(e, viewer, access)).toList();

        List<ComplaintDtos.TimelineEntry> timeline = historyRepository.findTimeline(c.getId()).stream()
                .filter(h -> official || (owner ? h.isVisibleToCitizen() : PUBLIC_EVENTS.contains(h.getEventType()) && h.isVisibleToCitizen()))
                .map(h -> new ComplaintDtos.TimelineEntry(h.getId(), h.getEventType(), h.getFromStatus(), h.getToStatus(),
                        publicNote(h, access), RoleLabels.of(h.getActor()),
                        official && h.getActor() != null ? h.getActor().getFullName() : null,
                        !h.isVisibleToCitizen(), h.getCreatedAt()))
                .toList();

        ComplaintDtos.ResolutionDto resolution = c.getStatus() == ComplaintStatus.RESOLVED || official || owner
                ? resolutionRepository.findFirstByComplaintIdOrderByCreatedAtDesc(c.getId())
                    .map(r -> new ComplaintDtos.ResolutionDto(r.getSummary(), r.getCreatedAt(), RoleLabels.of(r.getResolvedBy()),
                            official ? r.getResolvedBy().getFullName() : null))
                    .orElse(null)
                : null;

        ComplaintDtos.FeedbackDto feedback = owner || official
                ? feedbackRepository.findReporterFeedback(c.getId())
                    .map(f -> new ComplaintDtos.FeedbackDto(f.getRating(), f.getComment(), f.getCreatedAt(), f.getUpdatedAt()))
                    .orElse(null)
                : null;

        ComplaintDtos.ReporterDto reporter = official
                ? new ComplaintDtos.ReporterDto(c.getReporter().getFullName(), c.getReporter().getEmail(), c.getReporter().getPhone())
                : null;

        List<ComplaintDtos.AssignmentDto> assignments = official
                ? assignmentRepository.findHistory(c.getId()).stream().map(a -> new ComplaintDtos.AssignmentDto(
                        a.getType().name(),
                        switch (a.getType()) {
                            case WARD -> a.getWard().getName();
                            case DEPARTMENT -> a.getDepartment().getName();
                            case OFFICER -> a.getOfficer().getFullName();
                        },
                        a.getAssignedBy() == null ? "System" : a.getAssignedBy().getFullName(),
                        a.getNote(), a.isActive(), a.getCreatedAt(), a.getEndedAt())).toList()
                : null;

        Set<Long> upvoted = communityRepository.upvotedAmong(viewer.id(), List.of(c.getId()));
        Set<Long> followed = communityRepository.followedAmong(viewer.id(), List.of(c.getId()));
        boolean infoRequested = owner && !c.getStatus().isClosed();

        return new ComplaintDtos.Detail(
                c.getId(), c.getReferenceNo(), c.getTitle(), c.getDescription(), c.getCategory().getCode(),
                c.getCategory().getName(), c.getStatus(), c.getPriority(), c.getLocality(),
                owner || official ? c.getAddress() : c.getLocality(),
                c.getLatitude(), c.getLongitude(), c.isPublicVisible(), c.getCreatedAt(), c.getUpdatedAt(), c.getResolvedAt(),
                owner || official ? c.getWardMatch() : null,
                c.getWard() == null ? null : c.getWard().getName(),
                c.getDepartment() == null ? null : c.getDepartment().getName(),
                official && c.getAssignedOfficer() != null ? c.getAssignedOfficer().getFullName()
                        : (owner && c.getAssignedOfficer() != null ? "Field officer assigned" : null),
                c.getRejectionReason(),
                c.getDuplicateOf() == null ? null : c.getDuplicateOf().getReferenceNo(),
                c.getDuplicateOf() == null ? null : c.getDuplicateOf().getId(),
                c.getUpvoteCount(), c.getFollowerCount(),
                upvoted.contains(c.getId()), followed.contains(c.getId()), access,
                citizenEvidence, resolutionEvidence, timeline, resolution, feedback, reporter, assignments,
                owner && c.getStatus() == ComplaintStatus.RESOLVED,
                infoRequested,
                !owner && viewer.has("COMMUNITY_PARTICIPATE"),
                official ? actionPolicy.allowedActions(viewer, c) : List.of());
    }

    public ComplaintDtos.MapMarker marker(Complaint c, UserPrincipal viewer) {
        return new ComplaintDtos.MapMarker(c.getId(), c.getReferenceNo(), c.getTitle(), c.getCategory().getCode(),
                c.getCategory().getName(), c.getStatus(), c.getLatitude(), c.getLongitude(), c.getLocality(),
                c.getCreatedAt(), c.getUpvoteCount(), c.reporterId().equals(viewer.id()));
    }

    public String thumbnailUrl(Evidence e, UserPrincipal viewer) {
        return urlSigner.signedUrl(e.getId(), viewer.id());
    }

    private ComplaintDtos.EvidenceDto evidenceDto(Evidence e, UserPrincipal viewer, Access access) {
        return new ComplaintDtos.EvidenceDto(e.getId(), e.getType(), urlSigner.signedUrl(e.getId(), viewer.id()),
                e.getContentType(), access == Access.PUBLIC ? null : e.getOriginalFilename(), e.getSizeBytes(),
                e.getCreatedAt(), RoleLabels.of(e.getUploader()));
    }

    private static String publicNote(StatusHistory h, Access access) {
        if (access != Access.PUBLIC) {
            return h.getNote();
        }
        // Public viewers see official public updates and status notes, but not citizen-written text.
        return h.getEventType() == HistoryEventType.CREATED ? "Complaint submitted" : h.getNote();
    }
}
