package com.nagriksetu.feedback;

import com.nagriksetu.complaint.ComplaintDtos;
import com.nagriksetu.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/complaints/{id}/feedback")
public class FeedbackController {

    private final FeedbackService service;

    public FeedbackController(FeedbackService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ComplaintDtos.FeedbackDto create(@PathVariable Long id, @Valid @RequestBody FeedbackService.FeedbackRequest req) {
        return service.create(CurrentUser.require(), id, req);
    }

    @PutMapping
    public ComplaintDtos.FeedbackDto update(@PathVariable Long id, @Valid @RequestBody FeedbackService.FeedbackRequest req) {
        return service.update(CurrentUser.require(), id, req);
    }
}
