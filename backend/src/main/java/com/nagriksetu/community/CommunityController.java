package com.nagriksetu.community;

import com.nagriksetu.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/complaints/{id}")
public class CommunityController {

    private final CommunityService service;

    public CommunityController(CommunityService service) {
        this.service = service;
    }

    @PostMapping("/upvote")
    public CommunityService.VoteState upvote(@PathVariable Long id) {
        return service.upvote(CurrentUser.require(), id);
    }

    @DeleteMapping("/upvote")
    public CommunityService.VoteState removeUpvote(@PathVariable Long id) {
        return service.removeUpvote(CurrentUser.require(), id);
    }

    @PostMapping("/follow")
    public CommunityService.FollowState follow(@PathVariable Long id) {
        return service.follow(CurrentUser.require(), id);
    }

    @DeleteMapping("/follow")
    public CommunityService.FollowState unfollow(@PathVariable Long id) {
        return service.unfollow(CurrentUser.require(), id);
    }

    @PostMapping("/duplicate-flags")
    @ResponseStatus(HttpStatus.CREATED)
    public CommunityService.FlagResult flag(@PathVariable Long id, @Valid @RequestBody CommunityService.FlagRequest req) {
        return service.flagDuplicate(CurrentUser.require(), id, req);
    }
}
