package com.nagriksetu.user;

import com.nagriksetu.audit.AuditService;
import com.nagriksetu.common.ApiException;
import com.nagriksetu.security.JwtService;
import java.time.Instant;
import java.util.Locale;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    // Compared against when the email is unknown, so login timing does not reveal which emails exist.
    private final String dummyHash;

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService audit;

    public AuthService(UserRepository users, RoleRepository roles, PasswordEncoder passwordEncoder,
                       JwtService jwtService, AuditService audit) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.audit = audit;
        this.dummyHash = passwordEncoder.encode("nagrik-setu-timing-equaliser");
    }

    /** Public registration always creates a CITIZEN account. Official accounts are provisioned by administrators. */
    @Transactional
    public AuthRequests.AuthResponse register(AuthRequests.Register req) {
        String email = normaliseEmail(req.email());
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("EMAIL_IN_USE", "An account with this email already exists");
        }
        User user = new User();
        user.setFullName(req.fullName().trim());
        user.setEmail(email);
        user.setPhone(normalisePhone(req.phone()));
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setLocality(req.locality().trim());
        user.setAddress(blankToNull(req.address()));
        user.setRole(roles.findByCode(RoleCode.CITIZEN).orElseThrow());
        user.setLastLoginAt(Instant.now());
        users.save(user);
        audit.record(user.getId(), "USER_REGISTERED", "USER", user.getId(), null);
        return respond(user);
    }

    @Transactional
    public AuthRequests.AuthResponse login(AuthRequests.Login req) {
        User user = users.findByEmailIgnoreCase(normaliseEmail(req.email())).orElse(null);
        if (user == null) {
            passwordEncoder.matches(req.password(), dummyHash);
            throw new BadCredentialsException("bad credentials");
        }
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            audit.record(user.getId(), "LOGIN_FAILED", "USER", user.getId(), null);
            throw new BadCredentialsException("bad credentials");
        }
        if (!user.isEnabled()) {
            throw new DisabledException("disabled");
        }
        user.setLastLoginAt(Instant.now());
        audit.record(user.getId(), "LOGIN", "USER", user.getId(), null);
        return respond(user);
    }

    @Transactional(readOnly = true)
    public UserDto me(Long userId) {
        return UserDto.from(load(userId));
    }

    @Transactional
    public UserDto updateProfile(Long userId, AuthRequests.UpdateProfile req) {
        User user = load(userId);
        user.setFullName(req.fullName().trim());
        user.setPhone(normalisePhone(req.phone()));
        user.setLocality(req.locality().trim());
        user.setAddress(blankToNull(req.address()));
        audit.record(userId, "PROFILE_UPDATED", "USER", userId, null);
        return UserDto.from(user);
    }

    @Transactional
    public void changePassword(Long userId, AuthRequests.ChangePassword req) {
        User user = load(userId);
        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("INVALID_PASSWORD", "Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        audit.record(userId, "PASSWORD_CHANGED", "USER", userId, null);
    }

    private AuthRequests.AuthResponse respond(User user) {
        JwtService.IssuedToken token = jwtService.issue(user.getId(), user.roleCode().name());
        return new AuthRequests.AuthResponse(token.token(), token.expiresAt(), UserDto.from(user));
    }

    private User load(Long id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
    }

    static String normaliseEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    static String normalisePhone(String phone) {
        String digits = phone.replaceAll("[^0-9]", "");
        return digits.length() == 12 && digits.startsWith("91") ? "+91" + digits.substring(2) : "+91" + digits;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
