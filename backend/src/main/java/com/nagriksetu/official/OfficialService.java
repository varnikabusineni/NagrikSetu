package com.nagriksetu.official;

import com.nagriksetu.audit.AuditService;
import com.nagriksetu.common.ApiException;
import com.nagriksetu.common.PageResponse;
import com.nagriksetu.community.DuplicateFlag;
import com.nagriksetu.community.DuplicateFlagRepository;
import com.nagriksetu.complaint.Assignment;
import com.nagriksetu.complaint.AssignmentRepository;
import com.nagriksetu.complaint.Complaint;
import com.nagriksetu.complaint.ComplaintAccessPolicy;
import com.nagriksetu.complaint.ComplaintDtos;
import com.nagriksetu.complaint.ComplaintMapper;
import com.nagriksetu.complaint.ComplaintNotifier;
import com.nagriksetu.complaint.ComplaintRepository;
import com.nagriksetu.complaint.ComplaintStatus;
import com.nagriksetu.complaint.ComplaintWorkflow;
import com.nagriksetu.complaint.HistoryEventType;
import com.nagriksetu.complaint.Priority;
import com.nagriksetu.complaint.Resolution;
import com.nagriksetu.complaint.ResolutionRepository;
import com.nagriksetu.complaint.StatusHistory;
import com.nagriksetu.complaint.StatusHistoryRepository;
import com.nagriksetu.complaint.WardMatch;
import com.nagriksetu.evidence.EvidenceService;
import com.nagriksetu.evidence.EvidenceType;
import com.nagriksetu.evidence.ImageValidator;
import com.nagriksetu.notification.NotificationType;
import com.nagriksetu.reference.Department;
import com.nagriksetu.reference.DepartmentRepository;
import com.nagriksetu.reference.Ward;
import com.nagriksetu.reference.WardRepository;
import com.nagriksetu.security.UserPrincipal;
import com.nagriksetu.user.RoleCode;
import com.nagriksetu.user.User;
import com.nagriksetu.user.UserRepository;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Official workflow. Every complaint keeps one identity, one evidence set and one history while it moves
 * from the ward queue to a department and an officer: officials see the same citizen photos, never copies.
 */
@Service
public class OfficialService {

    public record OfficerDto(Long id, String fullName, String email, String departmentName) {
    }

    public record DuplicateFlagDto(Long id, Long complaintId, String complaintReference, String complaintTitle,
                                   Long duplicateOfId, String duplicateOfReference, String duplicateOfTitle,
                                   String reason, String status, Instant createdAt) {
    }

    public record Stats(Map<ComplaintStatus, Long> byStatus, long pendingWardVerification, long pendingDuplicateFlags,
                        String scopeLabel) {
    }

    private final ComplaintRepository complaints;
    private final StatusHistoryRepository history;
    private final AssignmentRepository assignments;
    private final ResolutionRepository resolutions;
    private final WardRepository wards;
    private final DepartmentRepository departments;
    private final UserRepository users;
    private final DuplicateFlagRepository flags;
    private final ImageValidator imageValidator;
    private final EvidenceService evidenceService;
    private final ComplaintAccessPolicy accessPolicy;
    private final OfficialActionPolicy actions;
    private final ComplaintMapper mapper;
    private final ComplaintNotifier notifier;
    private final AuditService audit;

    public OfficialService(ComplaintRepository complaints, StatusHistoryRepository history,
                           AssignmentRepository assignments, ResolutionRepository resolutions, WardRepository wards,
                           DepartmentRepository departments, UserRepository users, DuplicateFlagRepository flags,
                           ImageValidator imageValidator, EvidenceService evidenceService,
                           ComplaintAccessPolicy accessPolicy, OfficialActionPolicy actions, ComplaintMapper mapper,
                           ComplaintNotifier notifier, AuditService audit) {
        this.complaints = complaints;
        this.history = history;
        this.assignments = assignments;
        this.resolutions = resolutions;
        this.wards = wards;
        this.departments = departments;
        this.users = users;
        this.flags = flags;
        this.imageValidator = imageValidator;
        this.evidenceService = evidenceService;
        this.accessPolicy = accessPolicy;
        this.actions = actions;
        this.mapper = mapper;
        this.notifier = notifier;
        this.audit = audit;
    }

    // ------------------------------------------------------------------ queue

    @Transactional(readOnly = true)
    public PageResponse<ComplaintDtos.Summary> queue(UserPrincipal user, Set<ComplaintStatus> statuses, String q,
                                                     String category, String view, int page, int size) {
        Specification<Complaint> spec = scope(user, view).and(queueFilters(q, category, statuses));
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50),
                Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id")));
        Page<Complaint> result = complaints.findAll(spec, pageable);
        return new PageResponse<>(mapper.summaries(result.getContent(), user), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public Stats stats(UserPrincipal user) {
        Map<ComplaintStatus, Long> byStatus = new EnumMap<>(ComplaintStatus.class);
        for (ComplaintStatus s : ComplaintStatus.values()) {
            byStatus.put(s, complaints.count(scope(user, "all").and(queueFilters(null, null, Set.of(s)))));
        }
        long pendingVerification = user.has("WARD_VERIFY") ? complaints.count(scope(user, "unverified")) : 0;
        long pendingFlags = pendingDuplicateFlags(user).size();
        String label = switch (user.role()) {
            case WARD_HEAD -> user.wardId() == null ? "No ward assigned"
                    : wards.findById(user.wardId()).map(w -> w.getName() + " ward").orElse("Ward");
            case DEPARTMENT_HEAD -> user.departmentId() == null ? "No department assigned"
                    : departments.findById(user.departmentId()).map(Department::getName).orElse("Department");
            case OFFICER -> "My assignments";
            case ADMIN -> "All wards and departments";
            case CITIZEN -> "";
        };
        return new Stats(byStatus, pendingVerification, pendingFlags, label);
    }

    @Transactional(readOnly = true)
    public List<OfficerDto> officers(UserPrincipal user, Long departmentId) {
        Long dept = user.is(RoleCode.ADMIN) ? departmentId : user.departmentId();
        if (!user.has("DEPARTMENT_QUEUE_MANAGE") || dept == null) {
            return List.of();
        }
        return users.findActiveByRoleAndDepartment(RoleCode.OFFICER, dept).stream()
                .map(u -> new OfficerDto(u.getId(), u.getFullName(), u.getEmail(), u.getDepartment().getName()))
                .toList();
    }

    // ------------------------------------------------------------------ workflow actions

    @Transactional
    public ComplaintDtos.Detail verifyWard(UserPrincipal user, Long id, Long wardId, String note) {
        Complaint c = load(id);
        actions.require(user, c, OfficialAction.VERIFY_WARD);
        Ward ward = wards.findById(wardId).orElseThrow(() -> ApiException.badRequest("INVALID_WARD", "Choose a valid ward"));
        User actor = actor(user);
        c.setWard(ward);
        c.setWardMatch(WardMatch.VERIFIED);
        endActive(c, Assignment.Type.WARD);
        assignments.save(Assignment.ward(c, ward, actor, clean(note)));
        record(c, HistoryEventType.WARD_VERIFIED, c.getStatus(), "Ward confirmed: " + ward.getName(), true, actor);
        notifier.wardHeads(c, user.id(), NotificationType.NEW_COMPLAINT, "New complaint " + c.getReferenceNo(),
                c.getCategory().getName() + " at " + c.getLocality() + " (ward verified manually)");
        audit.record(user.id(), "WARD_VERIFIED", "COMPLAINT", c.getId(), "ward=" + ward.getCode());
        return detail(user, c);
    }

    @Transactional
    public ComplaintDtos.Detail review(UserPrincipal user, Long id, String note) {
        Complaint c = load(id);
        actions.require(user, c, OfficialAction.REVIEW);
        User actor = actor(user);
        moveTo(c, ComplaintStatus.UNDER_REVIEW, actor, note == null || note.isBlank() ? "Complaint verified by the ward office" : note.trim());
        notifier.reporterAndFollowers(c, user.id(), NotificationType.COMPLAINT_VERIFIED,
                "Complaint " + c.getReferenceNo() + " verified", "The ward office has verified your complaint and is reviewing it.");
        audit.record(user.id(), "COMPLAINT_REVIEWED", "COMPLAINT", c.getId(), null);
        return detail(user, c);
    }

    @Transactional
    public ComplaintDtos.Detail route(UserPrincipal user, Long id, Long departmentId, String note) {
        Complaint c = load(id);
        actions.require(user, c, OfficialAction.ROUTE);
        Department dept = departments.findById(departmentId)
                .orElseThrow(() -> ApiException.badRequest("INVALID_DEPARTMENT", "Choose a valid department"));
        if (dept.getId().equals(c.departmentId()) && c.getStatus() != ComplaintStatus.SUBMITTED
                && c.getStatus() != ComplaintStatus.REOPENED) {
            throw ApiException.conflict("ALREADY_ROUTED", "The complaint is already with " + dept.getName());
        }
        User actor = actor(user);
        boolean rerouting = c.getDepartment() != null;
        if (c.getStatus() == ComplaintStatus.SUBMITTED) {
            moveTo(c, ComplaintStatus.UNDER_REVIEW, actor, "Complaint verified by the ward office");
            notifier.reporterAndFollowers(c, user.id(), NotificationType.COMPLAINT_VERIFIED,
                    "Complaint " + c.getReferenceNo() + " verified", "The ward office has verified your complaint.");
        } else if (c.getStatus() != ComplaintStatus.UNDER_REVIEW) {
            moveTo(c, ComplaintStatus.UNDER_REVIEW, actor, rerouting ? "Sent for re-routing" : null);
        }
        endActive(c, Assignment.Type.DEPARTMENT);
        endActive(c, Assignment.Type.OFFICER);
        c.setDepartment(dept);
        c.setAssignedOfficer(null);
        assignments.save(Assignment.department(c, dept, actor, clean(note)));
        record(c, HistoryEventType.ROUTED, c.getStatus(),
                "Routed to " + dept.getName() + (note == null || note.isBlank() ? "" : ": " + note.trim()), true, actor);
        notifier.reporterAndFollowers(c, user.id(), rerouting ? NotificationType.REASSIGNED : NotificationType.DEPARTMENT_ASSIGNED,
                (rerouting ? "Complaint reassigned to " : "Complaint assigned to ") + dept.getName(),
                c.getReferenceNo() + " is now with the " + dept.getName() + " department.");
        notifier.departmentHeads(c, user.id(), NotificationType.NEW_ASSIGNMENT, "New complaint for your department",
                c.getReferenceNo() + ": " + c.getTitle() + " (" + c.getLocality() + ")");
        audit.record(user.id(), rerouting ? "COMPLAINT_REROUTED" : "COMPLAINT_ROUTED", "COMPLAINT", c.getId(), "department=" + dept.getCode());
        return detail(user, c);
    }

    @Transactional
    public ComplaintDtos.Detail assignOfficer(UserPrincipal user, Long id, Long officerId, String note) {
        Complaint c = load(id);
        actions.require(user, c, OfficialAction.ASSIGN_OFFICER);
        User officer = users.findById(officerId)
                .filter(u -> u.isEnabled() && u.roleCode() == RoleCode.OFFICER && u.getDepartment() != null
                        && u.getDepartment().getId().equals(c.departmentId()))
                .orElseThrow(() -> ApiException.badRequest("INVALID_OFFICER", "Choose an active officer from this department"));
        if (officer.getId().equals(c.assignedOfficerId()) && c.getStatus() == ComplaintStatus.ASSIGNED) {
            throw ApiException.conflict("ALREADY_ASSIGNED", "This officer is already assigned");
        }
        User actor = actor(user);
        boolean reassignment = c.getAssignedOfficer() != null;
        endActive(c, Assignment.Type.OFFICER);
        c.setAssignedOfficer(officer);
        assignments.save(Assignment.officer(c, officer, actor, clean(note)));
        if (c.getStatus() != ComplaintStatus.ASSIGNED) {
            moveTo(c, ComplaintStatus.ASSIGNED, actor, null);
        }
        record(c, reassignment ? HistoryEventType.REASSIGNED : HistoryEventType.ASSIGNED, c.getStatus(),
                (reassignment ? "Reassigned to another field officer" : "Field officer assigned")
                        + (note == null || note.isBlank() ? "" : ": " + note.trim()), true, actor);
        notifier.officer(c, user.id(), NotificationType.NEW_ASSIGNMENT, "Complaint assigned to you",
                c.getReferenceNo() + ": " + c.getTitle() + " at " + c.getAddress());
        notifier.reporterAndFollowers(c, user.id(), reassignment ? NotificationType.REASSIGNED : NotificationType.OFFICER_ASSIGNED,
                reassignment ? "Field officer changed" : "Field officer assigned",
                "A field officer from " + c.getDepartment().getName() + " has been assigned to " + c.getReferenceNo() + ".");
        audit.record(user.id(), reassignment ? "OFFICER_REASSIGNED" : "OFFICER_ASSIGNED", "COMPLAINT", c.getId(), "officer=" + officer.getId());
        return detail(user, c);
    }

    @Transactional
    public ComplaintDtos.Detail startWork(UserPrincipal user, Long id, String note) {
        Complaint c = load(id);
        actions.require(user, c, OfficialAction.START_WORK);
        moveTo(c, ComplaintStatus.IN_PROGRESS, actor(user), note == null || note.isBlank() ? "Work has started" : note.trim());
        notifier.reporterAndFollowers(c, user.id(), NotificationType.STATUS_CHANGED, "Work started on " + c.getReferenceNo(),
                "The field team has started working on your complaint.");
        audit.record(user.id(), "WORK_STARTED", "COMPLAINT", c.getId(), null);
        return detail(user, c);
    }

    @Transactional
    public ComplaintDtos.Detail resolve(UserPrincipal user, Long id, String summary, List<MultipartFile> photos) {
        Complaint c = load(id);
        actions.require(user, c, OfficialAction.RESOLVE);
        String text = summary == null ? "" : summary.trim();
        if (text.length() < 10 || text.length() > 2000) {
            throw ApiException.badRequest("SUMMARY_REQUIRED", "Describe the resolution in 10-2000 characters");
        }
        List<ImageValidator.ValidatedImage> images = imageValidator.validateAll(photos, false);
        User actor = actor(user);
        Resolution resolution = resolutions.save(new Resolution(c, actor, text));
        evidenceService.storeAll(c, actor, images, EvidenceType.RESOLUTION, resolution);
        moveTo(c, ComplaintStatus.RESOLVED, actor, text);
        c.setResolvedAt(Instant.now());
        notifier.reporterAndFollowers(c, user.id(), NotificationType.COMPLAINT_RESOLVED, "Complaint " + c.getReferenceNo() + " resolved",
                text + (images.isEmpty() ? "" : " (" + images.size() + " resolution photo(s) attached)"));
        audit.record(user.id(), "COMPLAINT_RESOLVED", "COMPLAINT", c.getId(), "photos=" + images.size());
        return detail(user, c);
    }

    @Transactional
    public ComplaintDtos.Detail reject(UserPrincipal user, Long id, String reason) {
        Complaint c = load(id);
        actions.require(user, c, OfficialAction.REJECT);
        String text = requireReason(reason);
        c.setRejectionReason(text);
        moveTo(c, ComplaintStatus.REJECTED, actor(user), text);
        notifier.reporterAndFollowers(c, user.id(), NotificationType.COMPLAINT_REJECTED,
                "Complaint " + c.getReferenceNo() + " rejected", "Reason: " + text);
        audit.record(user.id(), "COMPLAINT_REJECTED", "COMPLAINT", c.getId(), text);
        return detail(user, c);
    }

    @Transactional
    public ComplaintDtos.Detail reopen(UserPrincipal user, Long id, String reason) {
        Complaint c = load(id);
        actions.require(user, c, OfficialAction.REOPEN);
        String text = requireReason(reason);
        c.setRejectionReason(null);
        c.setResolvedAt(null);
        moveTo(c, ComplaintStatus.REOPENED, actor(user), text);
        notifier.reporterAndFollowers(c, user.id(), NotificationType.COMPLAINT_REOPENED,
                "Complaint " + c.getReferenceNo() + " reopened", text);
        notifier.responsibleOfficials(c, user.id(), NotificationType.NEW_ASSIGNMENT,
                "Complaint " + c.getReferenceNo() + " reopened", text);
        audit.record(user.id(), "COMPLAINT_REOPENED", "COMPLAINT", c.getId(), text);
        return detail(user, c);
    }

    @Transactional
    public ComplaintDtos.Detail requestInfo(UserPrincipal user, Long id, String message) {
        Complaint c = load(id);
        actions.require(user, c, OfficialAction.REQUEST_INFO);
        String text = requireText(message, "Describe the information you need");
        record(c, HistoryEventType.INFO_REQUESTED, c.getStatus(), text, true, actor(user));
        c.touch();
        notifier.notifyReporterOnly(c, user.id(), NotificationType.INFO_REQUESTED,
                "More information needed for " + c.getReferenceNo(), text);
        audit.record(user.id(), "INFO_REQUESTED", "COMPLAINT", c.getId(), null);
        return detail(user, c);
    }

    @Transactional
    public ComplaintDtos.Detail postUpdate(UserPrincipal user, Long id, String message, boolean visibleToCitizen) {
        Complaint c = load(id);
        actions.require(user, c, OfficialAction.POST_UPDATE);
        String text = requireText(message, "Write the update");
        record(c, visibleToCitizen ? HistoryEventType.PUBLIC_UPDATE : HistoryEventType.INTERNAL_NOTE, c.getStatus(), text,
                visibleToCitizen, actor(user));
        c.touch();
        if (visibleToCitizen) {
            notifier.reporterAndFollowers(c, user.id(), NotificationType.PUBLIC_UPDATE, "Update on " + c.getReferenceNo(), text);
        }
        return detail(user, c);
    }

    @Transactional
    public ComplaintDtos.Detail setPriority(UserPrincipal user, Long id, Priority priority) {
        Complaint c = load(id);
        actions.require(user, c, OfficialAction.SET_PRIORITY);
        if (priority == null || priority == c.getPriority()) {
            return detail(user, c);
        }
        Priority old = c.getPriority();
        c.setPriority(priority);
        record(c, HistoryEventType.PRIORITY_CHANGED, c.getStatus(), "Priority changed from " + old + " to " + priority, false, actor(user));
        audit.record(user.id(), "PRIORITY_CHANGED", "COMPLAINT", c.getId(), old + "->" + priority);
        return detail(user, c);
    }

    // ------------------------------------------------------------------ duplicates

    @Transactional(readOnly = true)
    public List<DuplicateFlagDto> pendingDuplicateFlags(UserPrincipal user) {
        if (!user.has("DUPLICATE_CONFIRM")) {
            return List.of();
        }
        return flags.findByStatusWithComplaints(DuplicateFlag.Status.PENDING).stream()
                .filter(f -> accessPolicy.isOfficialInScope(user, f.getComplaint()))
                .map(f -> new DuplicateFlagDto(f.getId(), f.getComplaint().getId(), f.getComplaint().getReferenceNo(),
                        f.getComplaint().getTitle(), f.getDuplicateOf().getId(), f.getDuplicateOf().getReferenceNo(),
                        f.getDuplicateOf().getTitle(), f.getReason(), f.getStatus().name(), f.getCreatedAt()))
                .toList();
    }

    @Transactional
    public void reviewDuplicateFlag(UserPrincipal user, Long flagId, boolean confirm) {
        if (!user.has("DUPLICATE_CONFIRM")) {
            throw ApiException.forbidden("You cannot review duplicate reports");
        }
        DuplicateFlag flag = flags.findById(flagId).orElseThrow(() -> ApiException.notFound("Duplicate flag"));
        Complaint c = flag.getComplaint();
        accessPolicy.requireOfficialScope(user, c);
        if (flag.getStatus() != DuplicateFlag.Status.PENDING) {
            throw ApiException.conflict("ALREADY_REVIEWED", "This flag has already been reviewed");
        }
        User actor = actor(user);
        if (!confirm) {
            flag.review(DuplicateFlag.Status.DISMISSED, actor);
            audit.record(user.id(), "DUPLICATE_DISMISSED", "COMPLAINT", c.getId(), "flag=" + flagId);
            return;
        }
        // Point at the root of any existing duplicate chain and refuse cycles.
        Complaint original = flag.getDuplicateOf();
        while (original.getDuplicateOf() != null) {
            original = original.getDuplicateOf();
        }
        if (original.getId().equals(c.getId())) {
            throw ApiException.conflict("DUPLICATE_CYCLE", "These complaints already point at each other");
        }
        c.setDuplicateOf(original);
        flag.review(DuplicateFlag.Status.CONFIRMED, actor);
        for (DuplicateFlag other : flags.findByComplaintAndStatus(c.getId(), DuplicateFlag.Status.PENDING)) {
            if (!other.getId().equals(flag.getId())) {
                other.review(DuplicateFlag.Status.DISMISSED, actor);
            }
        }
        record(c, HistoryEventType.DUPLICATE_CONFIRMED, c.getStatus(),
                "Confirmed as a duplicate of " + original.getReferenceNo() + ". Progress is tracked on that complaint.", true, actor);
        c.touch();
        notifier.reporterAndFollowers(c, user.id(), NotificationType.DUPLICATE_CONFIRMED,
                "Complaint " + c.getReferenceNo() + " linked to " + original.getReferenceNo(),
                "Your complaint describes the same issue as " + original.getReferenceNo() + ". Follow that complaint for updates.");
        audit.record(user.id(), "DUPLICATE_CONFIRMED", "COMPLAINT", c.getId(), "original=" + original.getReferenceNo());
    }

    // ------------------------------------------------------------------ helpers

    private Specification<Complaint> scope(UserPrincipal user, String view) {
        return (root, query, cb) -> {
            if ("unverified".equals(view)) {
                return user.has("WARD_VERIFY")
                        ? cb.equal(root.get("wardMatch"), WardMatch.PENDING_VERIFICATION)
                        : cb.disjunction();
            }
            return switch (user.role()) {
                case ADMIN -> cb.conjunction();
                case WARD_HEAD -> user.wardId() == null ? cb.disjunction() : cb.equal(root.get("ward").get("id"), user.wardId());
                case DEPARTMENT_HEAD -> user.departmentId() == null ? cb.disjunction()
                        : cb.equal(root.get("department").get("id"), user.departmentId());
                case OFFICER -> cb.equal(root.get("assignedOfficer").get("id"), user.id());
                case CITIZEN -> cb.disjunction();
            };
        };
    }

    private static Specification<Complaint> queueFilters(String q, String category, Set<ComplaintStatus> statuses) {
        return (root, query, cb) -> {
            if (query != null && Long.class != query.getResultType() && long.class != query.getResultType()) {
                root.fetch("category", JoinType.INNER);
            }
            List<Predicate> p = new ArrayList<>();
            if (statuses != null && !statuses.isEmpty()) {
                p.add(root.get("status").in(statuses));
            }
            if (category != null && !category.isBlank()) {
                p.add(cb.equal(root.get("category").get("code"), category));
            }
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase(Locale.ROOT).replace("%", "\\%").replace("_", "\\_") + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("title")), like, '\\'),
                        cb.like(cb.lower(root.get("referenceNo")), like, '\\'),
                        cb.like(cb.lower(root.get("locality")), like, '\\')));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }

    private void moveTo(Complaint c, ComplaintStatus to, User actor, String note) {
        ComplaintStatus from = c.getStatus();
        ComplaintWorkflow.requireTransition(from, to);
        c.setStatus(to);
        history.save(new StatusHistory(c, HistoryEventType.STATUS_CHANGE, from, to, note, true, actor));
    }

    private void record(Complaint c, HistoryEventType type, ComplaintStatus status, String note, boolean visible, User actor) {
        history.save(new StatusHistory(c, type, status, status, note, visible, actor));
    }

    private void endActive(Complaint c, Assignment.Type type) {
        assignments.findActive(c.getId(), type).ifPresent(a -> {
            a.end();
            assignments.saveAndFlush(a);
        });
    }

    private Complaint load(Long id) {
        return complaints.findById(id).orElseThrow(() -> ApiException.notFound("Complaint"));
    }

    private User actor(UserPrincipal user) {
        return users.getReferenceById(user.id());
    }

    private ComplaintDtos.Detail detail(UserPrincipal user, Complaint c) {
        complaints.flush();
        ComplaintAccessPolicy.Access access = accessPolicy.accessOf(user, c);
        return access == ComplaintAccessPolicy.Access.NONE ? null : mapper.detail(c, user, access);
    }

    private static String requireReason(String reason) {
        String text = reason == null ? "" : reason.trim();
        if (text.length() < 10 || text.length() > 1000) {
            throw ApiException.badRequest("REASON_REQUIRED", "An official reason of 10-1000 characters is required");
        }
        return text;
    }

    private static String requireText(String message, String error) {
        String text = message == null ? "" : message.trim();
        if (text.length() < 3 || text.length() > 2000) {
            throw ApiException.badRequest("MESSAGE_REQUIRED", error + " (3-2000 characters)");
        }
        return text;
    }

    private static String clean(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
