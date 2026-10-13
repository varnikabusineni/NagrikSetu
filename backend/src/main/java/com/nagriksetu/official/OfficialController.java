package com.nagriksetu.official;

import com.nagriksetu.common.PageResponse;
import com.nagriksetu.complaint.ComplaintDtos;
import com.nagriksetu.complaint.ComplaintStatus;
import com.nagriksetu.complaint.Priority;
import com.nagriksetu.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.EnumSet;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Official portal API. Restricted to official roles in SecurityConfig; each action re-checks scope and state. */
@RestController
@RequestMapping("/api/official")
public class OfficialController {

    public record NoteRequest(String note) {
    }

    public record VerifyWardRequest(@NotNull Long wardId, String note) {
    }

    public record RouteRequest(@NotNull(message = "Choose a department") Long departmentId, String note) {
    }

    public record AssignRequest(@NotNull(message = "Choose an officer") Long officerId, String note) {
    }

    public record ReasonRequest(String reason) {
    }

    public record MessageRequest(String message, Boolean visibleToCitizen) {
    }

    public record PriorityRequest(@NotNull Priority priority) {
    }

    private final OfficialService service;

    public OfficialController(OfficialService service) {
        this.service = service;
    }

    @GetMapping("/queue")
    public PageResponse<ComplaintDtos.Summary> queue(
            @RequestParam(required = false) List<ComplaintStatus> status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "all") String view,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.queue(CurrentUser.require(), status == null || status.isEmpty() ? null : EnumSet.copyOf(status),
                q, category, view, page, size);
    }

    @GetMapping("/stats")
    public OfficialService.Stats stats() {
        return service.stats(CurrentUser.require());
    }

    @GetMapping("/officers")
    public List<OfficialService.OfficerDto> officers(@RequestParam(required = false) Long departmentId) {
        return service.officers(CurrentUser.require(), departmentId);
    }

    @PostMapping("/complaints/{id}/verify-ward")
    public ComplaintDtos.Detail verifyWard(@PathVariable Long id, @Valid @RequestBody VerifyWardRequest req) {
        return service.verifyWard(CurrentUser.require(), id, req.wardId(), req.note());
    }

    @PostMapping("/complaints/{id}/review")
    public ComplaintDtos.Detail review(@PathVariable Long id, @RequestBody(required = false) NoteRequest req) {
        return service.review(CurrentUser.require(), id, req == null ? null : req.note());
    }

    @PostMapping("/complaints/{id}/route")
    public ComplaintDtos.Detail route(@PathVariable Long id, @Valid @RequestBody RouteRequest req) {
        return service.route(CurrentUser.require(), id, req.departmentId(), req.note());
    }

    @PostMapping("/complaints/{id}/assign")
    public ComplaintDtos.Detail assign(@PathVariable Long id, @Valid @RequestBody AssignRequest req) {
        return service.assignOfficer(CurrentUser.require(), id, req.officerId(), req.note());
    }

    @PostMapping("/complaints/{id}/start")
    public ComplaintDtos.Detail start(@PathVariable Long id, @RequestBody(required = false) NoteRequest req) {
        return service.startWork(CurrentUser.require(), id, req == null ? null : req.note());
    }

    @PostMapping(path = "/complaints/{id}/resolve", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ComplaintDtos.Detail resolve(@PathVariable Long id,
                                        @RequestParam("summary") String summary,
                                        @RequestPart(name = "photos", required = false) List<MultipartFile> photos) {
        return service.resolve(CurrentUser.require(), id, summary, photos);
    }

    @PostMapping("/complaints/{id}/reject")
    public ComplaintDtos.Detail reject(@PathVariable Long id, @RequestBody ReasonRequest req) {
        return service.reject(CurrentUser.require(), id, req.reason());
    }

    @PostMapping("/complaints/{id}/reopen")
    public ComplaintDtos.Detail reopen(@PathVariable Long id, @RequestBody ReasonRequest req) {
        return service.reopen(CurrentUser.require(), id, req.reason());
    }

    @PostMapping("/complaints/{id}/request-info")
    public ComplaintDtos.Detail requestInfo(@PathVariable Long id, @RequestBody MessageRequest req) {
        return service.requestInfo(CurrentUser.require(), id, req.message());
    }

    @PostMapping("/complaints/{id}/updates")
    public ComplaintDtos.Detail postUpdate(@PathVariable Long id, @RequestBody MessageRequest req) {
        return service.postUpdate(CurrentUser.require(), id, req.message(),
                req.visibleToCitizen() == null || req.visibleToCitizen());
    }

    @PostMapping("/complaints/{id}/priority")
    public ComplaintDtos.Detail priority(@PathVariable Long id, @Valid @RequestBody PriorityRequest req) {
        return service.setPriority(CurrentUser.require(), id, req.priority());
    }

    @GetMapping("/duplicate-flags")
    public List<OfficialService.DuplicateFlagDto> duplicateFlags() {
        return service.pendingDuplicateFlags(CurrentUser.require());
    }

    @PostMapping("/duplicate-flags/{flagId}/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmDuplicate(@PathVariable Long flagId) {
        service.reviewDuplicateFlag(CurrentUser.require(), flagId, true);
    }

    @PostMapping("/duplicate-flags/{flagId}/dismiss")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void dismissDuplicate(@PathVariable Long flagId) {
        service.reviewDuplicateFlag(CurrentUser.require(), flagId, false);
    }
}
