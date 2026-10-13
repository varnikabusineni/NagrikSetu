package com.nagriksetu.complaint;

import com.nagriksetu.common.PageResponse;
import com.nagriksetu.security.CurrentUser;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/complaints")
public class ComplaintController {

    private final ComplaintService service;

    public ComplaintController(ComplaintService service) {
        this.service = service;
    }

    /** multipart/form-data: complaint fields plus zero or more "photos" parts. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ComplaintDtos.Detail> create(
            @Valid @ModelAttribute CreateComplaintRequest request,
            @RequestPart(name = "photos", required = false) List<MultipartFile> photos) {
        ComplaintService.CreateResult result = service.create(CurrentUser.require(), request, photos);
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(result.complaint());
    }

    @GetMapping
    public PageResponse<ComplaintDtos.Summary> feed(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) List<ComplaintStatus> status,
            @RequestParam(required = false) String locality,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "false") boolean mine,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return service.feed(CurrentUser.require(), new ComplaintService.FeedQuery(q, category, toSet(status), locality,
                from, to, sort, mine, page, size));
    }

    @GetMapping("/map")
    public List<ComplaintDtos.MapMarker> map(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) List<ComplaintStatus> status,
            @RequestParam(required = false) Double minLat,
            @RequestParam(required = false) Double maxLat,
            @RequestParam(required = false) Double minLng,
            @RequestParam(required = false) Double maxLng) {
        return service.mapMarkers(CurrentUser.require(), category, toSet(status), minLat, maxLat, minLng, maxLng);
    }

    @GetMapping("/nearby")
    public List<ComplaintDtos.Nearby> nearby(
            @RequestParam double lat, @RequestParam double lng,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "300") int radius) {
        return service.nearby(CurrentUser.require(), lat, lng, category, radius);
    }

    @GetMapping("/{id}")
    public ComplaintDtos.Detail get(@PathVariable Long id) {
        return service.get(CurrentUser.require(), id);
    }

    @GetMapping("/by-reference/{reference}")
    public ComplaintDtos.Detail byReference(@PathVariable String reference) {
        return service.getByReference(CurrentUser.require(), reference);
    }

    @PostMapping(path = "/{id}/responses", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ComplaintDtos.Detail respond(
            @PathVariable Long id,
            @RequestParam(name = "message", required = false) String message,
            @RequestPart(name = "photos", required = false) List<MultipartFile> photos) {
        return service.respond(CurrentUser.require(), id, message, photos);
    }

    private static Set<ComplaintStatus> toSet(List<ComplaintStatus> statuses) {
        return statuses == null || statuses.isEmpty() ? null : EnumSet.copyOf(statuses);
    }
}
