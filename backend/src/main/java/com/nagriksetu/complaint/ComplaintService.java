package com.nagriksetu.complaint;

import com.nagriksetu.audit.AuditService;
import com.nagriksetu.common.ApiException;
import com.nagriksetu.common.PageResponse;
import com.nagriksetu.complaint.ComplaintAccessPolicy.Access;
import com.nagriksetu.evidence.EvidenceService;
import com.nagriksetu.evidence.EvidenceType;
import com.nagriksetu.evidence.ImageValidator;
import com.nagriksetu.notification.NotificationType;
import com.nagriksetu.reference.Category;
import com.nagriksetu.reference.CategoryRepository;
import com.nagriksetu.reference.Ward;
import com.nagriksetu.reference.WardLocator;
import com.nagriksetu.security.UserPrincipal;
import com.nagriksetu.user.User;
import com.nagriksetu.user.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ComplaintService {

    public static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter REF_DATE = DateTimeFormatter.ofPattern("yyMMdd");

    public record CreateResult(ComplaintDtos.Detail complaint, boolean replayed) {
    }

    public record FeedQuery(String q, String category, Set<ComplaintStatus> statuses, String locality,
                            LocalDate from, LocalDate to, String sort, boolean mineOnly, int page, int size) {
    }

    private final ComplaintRepository complaints;
    private final CategoryRepository categories;
    private final UserRepository users;
    private final StatusHistoryRepository history;
    private final AssignmentRepository assignments;
    private final WardLocator wardLocator;
    private final ImageValidator imageValidator;
    private final EvidenceService evidenceService;
    private final ComplaintAccessPolicy accessPolicy;
    private final ComplaintMapper mapper;
    private final ComplaintNotifier notifier;
    private final AuditService audit;

    public ComplaintService(ComplaintRepository complaints, CategoryRepository categories, UserRepository users,
                            StatusHistoryRepository history, AssignmentRepository assignments, WardLocator wardLocator,
                            ImageValidator imageValidator, EvidenceService evidenceService,
                            ComplaintAccessPolicy accessPolicy, ComplaintMapper mapper, ComplaintNotifier notifier,
                            AuditService audit) {
        this.complaints = complaints;
        this.categories = categories;
        this.users = users;
        this.history = history;
        this.assignments = assignments;
        this.wardLocator = wardLocator;
        this.imageValidator = imageValidator;
        this.evidenceService = evidenceService;
        this.accessPolicy = accessPolicy;
        this.mapper = mapper;
        this.notifier = notifier;
        this.audit = audit;
    }

    /**
     * Creates a complaint and stores its photos atomically: the complaint row, evidence metadata,
     * timeline, ward assignment and notifications commit together; if anything fails the stored
     * files are deleted (see {@link EvidenceService}).
     */
    @Transactional
    public CreateResult create(UserPrincipal principal, CreateComplaintRequest req, List<MultipartFile> photos) {
        if (!principal.has("COMPLAINT_CREATE")) {
            throw ApiException.forbidden("Only citizen accounts can report complaints");
        }
        if (req.clientRequestId() != null) {
            Optional<Complaint> existing = complaints.findByReporterAndClientRequestId(principal.id(), req.clientRequestId());
            if (existing.isPresent()) {
                return new CreateResult(mapper.detail(existing.get(), principal, Access.OWNER), true);
            }
        }
        List<ImageValidator.ValidatedImage> images = imageValidator.validateAll(photos, false);
        Category category = categories.findByCode(req.categoryCode())
                .orElseThrow(() -> ApiException.badRequest("INVALID_CATEGORY", "Choose a valid category"));
        User reporter = users.getReferenceById(principal.id());

        BigDecimal lat = req.latitude().setScale(6, RoundingMode.HALF_UP);
        BigDecimal lng = req.longitude().setScale(6, RoundingMode.HALF_UP);
        Optional<Ward> ward = wardLocator.locate(lat.doubleValue(), lng.doubleValue());

        Complaint c = new Complaint();
        c.setReferenceNo(nextReference());
        c.setClientRequestId(req.clientRequestId());
        c.setTitle(req.title().trim());
        c.setDescription(req.description().trim());
        c.setCategory(category);
        c.setStatus(ComplaintStatus.SUBMITTED);
        c.setPriority(Priority.NORMAL);
        c.setLocality(req.locality().trim());
        c.setAddress(req.address().trim());
        c.setLatitude(lat);
        c.setLongitude(lng);
        c.setPublicVisible(req.isPublic() == null || req.isPublic());
        c.setReporter(reporter);
        c.setWard(ward.orElse(null));
        c.setWardMatch(ward.isPresent() ? WardMatch.AUTO_MATCHED : WardMatch.PENDING_VERIFICATION);
        complaints.saveAndFlush(c);

        evidenceService.storeAll(c, reporter, images, EvidenceType.CITIZEN_REPORT, null);

        history.save(new StatusHistory(c, HistoryEventType.CREATED, null, ComplaintStatus.SUBMITTED,
                images.isEmpty() ? "Complaint submitted" : "Complaint submitted with " + images.size() + " photo(s)",
                true, reporter));
        if (ward.isPresent()) {
            assignments.save(Assignment.ward(c, ward.get(), null, "Matched automatically from the reported location"));
            history.save(new StatusHistory(c, HistoryEventType.ROUTED, ComplaintStatus.SUBMITTED, ComplaintStatus.SUBMITTED,
                    "Sent to the " + ward.get().getName() + " ward office", true, null));
            notifier.wardHeads(c, principal.id(), NotificationType.NEW_COMPLAINT, "New complaint " + c.getReferenceNo(),
                    category.getName() + " reported at " + c.getLocality() + ": " + c.getTitle());
        } else {
            history.save(new StatusHistory(c, HistoryEventType.INTERNAL_NOTE, ComplaintStatus.SUBMITTED, ComplaintStatus.SUBMITTED,
                    "Location did not match a configured ward boundary; awaiting manual ward verification", false, null));
            notifier.administrators(c, principal.id(), NotificationType.WARD_VERIFICATION_NEEDED,
                    "Ward verification needed: " + c.getReferenceNo(),
                    "Confirm the ward for \"" + c.getTitle() + "\" at " + c.getLocality());
        }
        notifier.reporterAndFollowers(c, null, NotificationType.COMPLAINT_SUBMITTED,
                "Complaint " + c.getReferenceNo() + " submitted",
                "We received your complaint \"" + c.getTitle() + "\". You will be notified as it progresses.");
        audit.record(principal.id(), "COMPLAINT_CREATED", "COMPLAINT", c.getId(),
                "ref=" + c.getReferenceNo() + " photos=" + images.size() + " wardMatch=" + c.getWardMatch());
        return new CreateResult(mapper.detail(c, principal, Access.OWNER), false);
    }

    @Transactional(readOnly = true)
    public ComplaintDtos.Detail get(UserPrincipal principal, Long id) {
        Complaint c = complaints.findById(id).orElseThrow(() -> ApiException.notFound("Complaint"));
        return mapper.detail(c, principal, accessPolicy.requireView(principal, c));
    }

    @Transactional(readOnly = true)
    public ComplaintDtos.Detail getByReference(UserPrincipal principal, String reference) {
        Complaint c = complaints.findByReferenceNoIgnoreCase(reference.trim())
                .orElseThrow(() -> ApiException.notFound("Complaint"));
        return mapper.detail(c, principal, accessPolicy.requireView(principal, c));
    }

    @Transactional(readOnly = true)
    public PageResponse<ComplaintDtos.Summary> feed(UserPrincipal principal, FeedQuery q) {
        Specification<Complaint> spec = (q.mineOnly()
                ? ComplaintSpecifications.reportedBy(principal.id())
                : ComplaintSpecifications.visibleTo(principal.id()))
                .and(ComplaintSpecifications.filters(q.q(), q.category(), q.statuses(), q.locality(),
                        q.from() == null ? null : q.from().atStartOfDay(INDIA).toInstant(),
                        q.to() == null ? null : q.to().plusDays(1).atStartOfDay(INDIA).toInstant()));
        Sort sort = switch (q.sort() == null ? "newest" : q.sort()) {
            case "activity" -> Sort.by(Sort.Order.desc("upvoteCount"), Sort.Order.desc("followerCount"), Sort.Order.desc("createdAt"));
            case "updated" -> Sort.by(Sort.Order.desc("updatedAt"));
            case "oldest" -> Sort.by(Sort.Order.asc("createdAt"));
            default -> Sort.by(Sort.Order.desc("createdAt"));
        };
        PageRequest pageable = PageRequest.of(Math.max(q.page(), 0), Math.min(Math.max(q.size(), 1), 50), sort.and(Sort.by("id")));
        Page<Complaint> page = complaints.findAll(spec, pageable);
        List<ComplaintDtos.Summary> items = mapper.summaries(page.getContent(), principal);
        return new PageResponse<>(items, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    @Transactional(readOnly = true)
    public List<ComplaintDtos.MapMarker> mapMarkers(UserPrincipal principal, String category, Set<ComplaintStatus> statuses,
                                                    Double minLat, Double maxLat, Double minLng, Double maxLng) {
        Specification<Complaint> spec = ComplaintSpecifications.visibleTo(principal.id())
                .and(ComplaintSpecifications.filters(null, category, statuses, null, null, null))
                .and(ComplaintSpecifications.inBounds(minLat, maxLat, minLng, maxLng));
        return complaints.findAll(spec, PageRequest.of(0, 2000, Sort.by(Sort.Order.desc("createdAt"))))
                .map(c -> mapper.marker(c, principal)).getContent();
    }

    /** Suggests open public complaints of the same category near a location, to discourage duplicates. */
    @Transactional(readOnly = true)
    public List<ComplaintDtos.Nearby> nearby(UserPrincipal principal, double lat, double lng, String categoryCode, int radiusMeters) {
        int radius = Math.min(Math.max(radiusMeters, 50), 2000);
        double dLat = radius / 111_320.0;
        double dLng = radius / (111_320.0 * Math.cos(Math.toRadians(lat)));
        List<Complaint> candidates = complaints.findCandidatesInBox(
                BigDecimal.valueOf(lat - dLat), BigDecimal.valueOf(lat + dLat),
                BigDecimal.valueOf(lng - dLng), BigDecimal.valueOf(lng + dLng),
                EnumSet.of(ComplaintStatus.RESOLVED, ComplaintStatus.REJECTED), Instant.now().minus(Duration.ofDays(90)));
        List<ComplaintDtos.Summary> summaries = mapper.summaries(candidates, principal);
        return candidates.stream()
                .filter(c -> categoryCode == null || categoryCode.isBlank() || c.getCategory().getCode().equals(categoryCode))
                .map(c -> new Object[] {c, distanceMeters(lat, lng, c.getLatitude().doubleValue(), c.getLongitude().doubleValue())})
                .filter(pair -> (long) pair[1] <= radius)
                .sorted(Comparator.comparingLong(pair -> (long) pair[1]))
                .limit(5)
                .map(pair -> {
                    Complaint c = (Complaint) pair[0];
                    String thumb = summaries.stream().filter(s -> s.id().equals(c.getId())).findFirst()
                            .map(ComplaintDtos.Summary::thumbnailUrl).orElse(null);
                    return new ComplaintDtos.Nearby(c.getId(), c.getReferenceNo(), c.getTitle(), c.getCategory().getName(),
                            c.getStatus(), c.getLocality(), c.getCreatedAt(), c.getUpvoteCount(), (long) pair[1], thumb);
                })
                .toList();
    }

    /** The reporter adds information (text and optional photos), typically after an official request. */
    @Transactional
    public ComplaintDtos.Detail respond(UserPrincipal principal, Long id, String message, List<MultipartFile> photos) {
        Complaint c = complaints.findById(id).orElseThrow(() -> ApiException.notFound("Complaint"));
        Access access = accessPolicy.requireView(principal, c);
        if (access != Access.OWNER) {
            throw ApiException.forbidden("Only the citizen who reported this complaint can add information to it");
        }
        if (c.getStatus().isClosed()) {
            throw ApiException.conflict("COMPLAINT_CLOSED", "This complaint is closed. Information can no longer be added.");
        }
        String text = message == null ? "" : message.trim();
        List<ImageValidator.ValidatedImage> images = imageValidator.validateAll(photos, false);
        if (text.length() < 3 && images.isEmpty()) {
            throw ApiException.badRequest("EMPTY_RESPONSE", "Add a message or at least one photo");
        }
        if (text.length() > 2000) {
            throw ApiException.badRequest("MESSAGE_TOO_LONG", "Message must be at most 2000 characters");
        }
        User reporter = users.getReferenceById(principal.id());
        evidenceService.storeAll(c, reporter, images, EvidenceType.CITIZEN_REPORT, null);
        String note = (text.isEmpty() ? "Additional photos provided" : text)
                + (images.isEmpty() || text.isEmpty() ? "" : " (" + images.size() + " photo(s) attached)");
        history.save(new StatusHistory(c, HistoryEventType.CITIZEN_RESPONSE, c.getStatus(), c.getStatus(), note, true, reporter));
        c.touch();
        notifier.responsibleOfficials(c, principal.id(), NotificationType.CITIZEN_RESPONSE,
                "Citizen added information to " + c.getReferenceNo(), note);
        audit.record(principal.id(), "COMPLAINT_INFO_ADDED", "COMPLAINT", c.getId(), "photos=" + images.size());
        return mapper.detail(c, principal, Access.OWNER);
    }

    private String nextReference() {
        long seq = complaints.nextReferenceSequence();
        return "NS-" + LocalDate.now(INDIA).format(REF_DATE) + "-" + String.format("%06d", seq);
    }

    static long distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        double r = 6_371_000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return Math.round(2 * r * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)));
    }
}
