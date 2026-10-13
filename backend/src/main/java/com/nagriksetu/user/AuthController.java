package com.nagriksetu.user;

import com.nagriksetu.audit.AuditService;
import com.nagriksetu.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuditService audit;

    public AuthController(AuthService authService, AuditService audit) {
        this.authService = authService;
        this.audit = audit;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthRequests.AuthResponse register(@Valid @RequestBody AuthRequests.Register req) {
        return authService.register(req);
    }

    @PostMapping("/login")
    public AuthRequests.AuthResponse login(@Valid @RequestBody AuthRequests.Login req) {
        return authService.login(req);
    }

    /** Tokens are stateless; the client discards its token. Recorded for the audit trail. */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout() {
        Long id = CurrentUser.require().id();
        audit.record(id, "LOGOUT", "USER", id, null);
    }

    @GetMapping("/me")
    public UserDto me() {
        return authService.me(CurrentUser.require().id());
    }

    @PutMapping("/me")
    public UserDto updateProfile(@Valid @RequestBody AuthRequests.UpdateProfile req) {
        return authService.updateProfile(CurrentUser.require().id(), req);
    }

    @PostMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody AuthRequests.ChangePassword req) {
        authService.changePassword(CurrentUser.require().id(), req);
    }
}
