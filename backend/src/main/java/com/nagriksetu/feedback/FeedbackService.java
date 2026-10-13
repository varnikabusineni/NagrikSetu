package com.nagriksetu.feedback;

import com.nagriksetu.audit.AuditService;
import com.nagriksetu.common.ApiException;
import com.nagriksetu.complaint.Complaint;
import com.nagriksetu.complaint.ComplaintDtos;
import com.nagriksetu.complaint.ComplaintNotifier;
import com.nagriksetu.complaint.ComplaintRepository;
import com.nagriksetu.complaint.ComplaintStatus;
import com.nagriksetu.notification.NotificationType;
import com.nagriksetu.security.UserPrincipal;
import com.nagriksetu.user.UserRepository;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolution feedback from the reporting citizen. One feedback record per complaint; it can be
 * created once (POST) and then edited explicitly (PUT) while the complaint remains resolved.
 */
@Service
public class FeedbackService {

    public record FeedbackRequest(
            @NotNull(message = "Choose a rating") @Min(value = 1, message = "Rating must be 1-5") @Max(value = 5, message = "Rating must be 1-5")
            Integer rating,
            @Size(max = 1000, message = "Comment must be at most 1000 characters") String comment) {
    }

    private final FeedbackRepository repository;
    private final ComplaintRepository complaints;
    private final UserRepository users;
    private final ComplaintNotifier notifier;
    private final AuditService audit;

    public FeedbackService(FeedbackRepository repository, ComplaintRepository complaints, UserRepository users,
                           ComplaintNotifier notifier, AuditService audit) {
        this.repository = repository;
        this.complaints = complaints;
        this.users = users;
        this.notifier = notifier;
        this.audit = audit;
    }

    @Transactional
    public ComplaintDtos.FeedbackDto create(UserPrincipal user, Long complaintId, FeedbackRequest req) {
        Complaint c = ownResolvedComplaint(user, complaintId);
        if (repository.findByComplaintAndUser(c.getId(), user.id()).isPresent()) {
            throw ApiException.conflict("FEEDBACK_EXISTS", "You have already rated this resolution. Edit your feedback instead.");
        }
        Feedback f = repository.save(new Feedback(c, users.getReferenceById(user.id()), req.rating(), clean(req.comment())));
        notifier.departmentHeads(c, user.id(), NotificationType.FEEDBACK_RECEIVED,
                "Feedback on " + c.getReferenceNo(), "The citizen rated the resolution " + req.rating() + "/5");
        audit.record(user.id(), "FEEDBACK_SUBMITTED", "COMPLAINT", c.getId(), "rating=" + req.rating());
        return dto(f);
    }

    @Transactional
    public ComplaintDtos.FeedbackDto update(UserPrincipal user, Long complaintId, FeedbackRequest req) {
        Complaint c = ownResolvedComplaint(user, complaintId);
        Feedback f = repository.findByComplaintAndUser(c.getId(), user.id())
                .orElseThrow(() -> ApiException.notFound("Feedback"));
        f.update(req.rating(), clean(req.comment()));
        audit.record(user.id(), "FEEDBACK_UPDATED", "COMPLAINT", c.getId(), "rating=" + req.rating());
        return dto(f);
    }

    private Complaint ownResolvedComplaint(UserPrincipal user, Long complaintId) {
        Complaint c = complaints.findById(complaintId).orElseThrow(() -> ApiException.notFound("Complaint"));
        if (!c.reporterId().equals(user.id())) {
            throw ApiException.forbidden("Only the citizen who reported this complaint can rate its resolution");
        }
        if (c.getStatus() != ComplaintStatus.RESOLVED) {
            throw ApiException.conflict("NOT_RESOLVED", "Feedback can be given once the complaint is resolved");
        }
        return c;
    }

    private static String clean(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static ComplaintDtos.FeedbackDto dto(Feedback f) {
        return new ComplaintDtos.FeedbackDto(f.getRating(), f.getComment(), f.getCreatedAt(), f.getUpdatedAt());
    }
}
