package com.nagriksetu.community;

import com.nagriksetu.audit.AuditService;
import com.nagriksetu.common.ApiException;
import com.nagriksetu.complaint.Complaint;
import com.nagriksetu.complaint.ComplaintAccessPolicy;
import com.nagriksetu.complaint.ComplaintNotifier;
import com.nagriksetu.complaint.ComplaintRepository;
import com.nagriksetu.notification.NotificationType;
import com.nagriksetu.security.UserPrincipal;
import com.nagriksetu.user.UserRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommunityService {

    public record VoteState(boolean upvoted, int upvoteCount) {
    }

    public record FollowState(boolean followed, int followerCount) {
    }

    public record FlagRequest(@NotBlank(message = "Enter the reference of the original complaint") String duplicateOfReference,
                              @Size(max = 500) String reason) {
    }

    public record FlagResult(Long id, String status, String message) {
    }

    private final ComplaintRepository complaints;
    private final CommunityRepository community;
    private final DuplicateFlagRepository flags;
    private final ComplaintAccessPolicy accessPolicy;
    private final UserRepository users;
    private final ComplaintNotifier notifier;
    private final AuditService audit;

    public CommunityService(ComplaintRepository complaints, CommunityRepository community, DuplicateFlagRepository flags,
                            ComplaintAccessPolicy accessPolicy, UserRepository users, ComplaintNotifier notifier,
                            AuditService audit) {
        this.complaints = complaints;
        this.community = community;
        this.flags = flags;
        this.accessPolicy = accessPolicy;
        this.users = users;
        this.notifier = notifier;
        this.audit = audit;
    }

    @Transactional
    public VoteState upvote(UserPrincipal user, Long complaintId) {
        Complaint c = participable(user, complaintId);
        if (c.reporterId().equals(user.id())) {
            throw ApiException.badRequest("OWN_COMPLAINT", "You cannot upvote your own complaint");
        }
        if (!community.addUpvote(c.getId(), user.id())) {
            throw ApiException.conflict("ALREADY_UPVOTED", "You have already upvoted this issue");
        }
        complaints.adjustUpvotes(c.getId(), 1);
        return new VoteState(true, community.countUpvotes(c.getId()));
    }

    @Transactional
    public VoteState removeUpvote(UserPrincipal user, Long complaintId) {
        Complaint c = participable(user, complaintId);
        if (community.removeUpvote(c.getId(), user.id())) {
            complaints.adjustUpvotes(c.getId(), -1);
        }
        return new VoteState(false, community.countUpvotes(c.getId()));
    }

    @Transactional
    public FollowState follow(UserPrincipal user, Long complaintId) {
        Complaint c = participable(user, complaintId);
        if (c.reporterId().equals(user.id())) {
            throw ApiException.badRequest("OWN_COMPLAINT", "You already receive every update on your own complaint");
        }
        int delta = community.addFollow(c.getId(), user.id()) ? 1 : 0;
        complaints.adjustFollowers(c.getId(), delta);
        return new FollowState(true, c.getFollowerCount() + delta);
    }

    @Transactional
    public FollowState unfollow(UserPrincipal user, Long complaintId) {
        Complaint c = participable(user, complaintId);
        int delta = community.removeFollow(c.getId(), user.id()) ? -1 : 0;
        complaints.adjustFollowers(c.getId(), delta);
        return new FollowState(false, Math.max(0, c.getFollowerCount() + delta));
    }

    /** Citizens may suggest a duplicate relationship; only officials can confirm it. */
    @Transactional
    public FlagResult flagDuplicate(UserPrincipal user, Long complaintId, FlagRequest req) {
        Complaint c = participable(user, complaintId);
        Complaint original = complaints.findByReferenceNoIgnoreCase(req.duplicateOfReference().trim())
                .filter(o -> accessPolicy.accessOf(user, o) != ComplaintAccessPolicy.Access.NONE)
                .orElseThrow(() -> ApiException.badRequest("ORIGINAL_NOT_FOUND",
                        "No visible complaint has reference " + req.duplicateOfReference().trim()));
        if (original.getId().equals(c.getId())) {
            throw ApiException.badRequest("SAME_COMPLAINT", "A complaint cannot duplicate itself");
        }
        if (c.getDuplicateOf() != null) {
            throw ApiException.conflict("ALREADY_DUPLICATE", "This complaint is already marked as a duplicate");
        }
        if (flags.existsFlag(c.getId(), original.getId(), user.id())) {
            throw ApiException.conflict("ALREADY_FLAGGED", "You have already flagged this pair of complaints");
        }
        DuplicateFlag flag = flags.save(new DuplicateFlag(c, original, users.getReferenceById(user.id()),
                req.reason() == null || req.reason().isBlank() ? null : req.reason().trim()));
        notifier.responsibleOfficials(c, user.id(), NotificationType.DUPLICATE_FLAGGED,
                "Possible duplicate: " + c.getReferenceNo(),
                "A citizen suggested " + c.getReferenceNo() + " duplicates " + original.getReferenceNo());
        audit.record(user.id(), "DUPLICATE_FLAGGED", "COMPLAINT", c.getId(), "duplicateOf=" + original.getReferenceNo());
        return new FlagResult(flag.getId(), flag.getStatus().name(),
                "Thank you. An official will review whether these complaints describe the same issue.");
    }

    private Complaint participable(UserPrincipal user, Long complaintId) {
        if (!user.has("COMMUNITY_PARTICIPATE")) {
            throw ApiException.forbidden("Only citizen accounts can upvote or follow issues");
        }
        Complaint c = complaints.findById(complaintId).orElseThrow(() -> ApiException.notFound("Complaint"));
        accessPolicy.requireView(user, c);
        return c;
    }
}
