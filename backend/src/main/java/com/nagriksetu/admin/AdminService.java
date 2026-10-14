package com.nagriksetu.admin;

import com.nagriksetu.audit.AuditEvent;
import com.nagriksetu.audit.AuditEventRepository;
import com.nagriksetu.audit.AuditService;
import com.nagriksetu.common.ApiException;
import com.nagriksetu.common.PageResponse;
import com.nagriksetu.reference.Department;
import com.nagriksetu.reference.DepartmentRepository;
import com.nagriksetu.reference.Ward;
import com.nagriksetu.reference.WardRepository;
import com.nagriksetu.security.UserPrincipal;
import com.nagriksetu.user.AuthService;
import com.nagriksetu.user.RoleCode;
import com.nagriksetu.user.RoleRepository;
import com.nagriksetu.user.User;
import com.nagriksetu.user.UserRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account administration for the municipal control room: create official accounts, assign their
 * role and ward/department, activate or deactivate accounts, reset passwords, and read the audit log.
 * Every change is audited. Administrators cannot deactivate or demote themselves, so at least one
 * active administrator always remains.
 */
@Service
public class AdminService {

    private final UserRepository users;
    private final RoleRepository roles;
    private final WardRepository wards;
    private final DepartmentRepository departments;
    private final PasswordEncoder passwordEncoder;
    private final AuditEventRepository auditEvents;
    private final AuditService audit;

    public AdminService(UserRepository users, RoleRepository roles, WardRepository wards, DepartmentRepository departments,
                        PasswordEncoder passwordEncoder, AuditEventRepository auditEvents, AuditService audit) {
        this.users = users;
        this.roles = roles;
        this.wards = wards;
        this.departments = departments;
        this.passwordEncoder = passwordEncoder;
        this.auditEvents = auditEvents;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<AdminDtos.AccountDto> list(RoleCode role, String q, boolean includeCitizens) {
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        return users.findAll().stream()
                .filter(u -> role != null ? u.roleCode() == role : includeCitizens || u.roleCode() != RoleCode.CITIZEN)
                .filter(u -> needle.isEmpty() || u.getFullName().toLowerCase(Locale.ROOT).contains(needle)
                        || u.getEmail().toLowerCase(Locale.ROOT).contains(needle))
                .sorted(Comparator.comparing((User u) -> u.roleCode().ordinal()).thenComparing(User::getFullName))
                .map(AdminDtos.AccountDto::from)
                .toList();
    }

    @Transactional
    public AdminDtos.AccountDto createOfficial(UserPrincipal admin, AdminDtos.CreateOfficial req) {
        if (req.role() == RoleCode.CITIZEN) {
            throw ApiException.badRequest("INVALID_ROLE", "Citizens register themselves. Choose an official role.");
        }
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("EMAIL_IN_USE", "An account with this email already exists");
        }
        User u = new User();
        u.setFullName(req.fullName().trim());
        u.setEmail(email);
        u.setPhone(AuthService.normalisePhone(req.phone()));
        u.setPasswordHash(passwordEncoder.encode(req.password()));
        applyRole(u, req.role(), req.wardId(), req.departmentId());
        users.save(u);
        audit.record(admin.id(), "OFFICIAL_CREATED", "USER", u.getId(), describe(u));
        return AdminDtos.AccountDto.from(u);
    }

    @Transactional
    public AdminDtos.AccountDto update(UserPrincipal admin, Long id, AdminDtos.UpdateAccount req) {
        User u = load(id);
        if (u.roleCode() == RoleCode.CITIZEN && req.role() != RoleCode.CITIZEN) {
            throw ApiException.badRequest("INVALID_ROLE", "Citizen accounts cannot be converted to official accounts. Create a new official account instead.");
        }
        if (u.roleCode() != RoleCode.CITIZEN && req.role() == RoleCode.CITIZEN) {
            throw ApiException.badRequest("INVALID_ROLE", "Official accounts cannot be converted to citizen accounts. Deactivate the account instead.");
        }
        if (u.getId().equals(admin.id()) && req.role() != RoleCode.ADMIN) {
            throw ApiException.badRequest("SELF_DEMOTION", "You cannot remove your own administrator role");
        }
        u.setFullName(req.fullName().trim());
        u.setPhone(AuthService.normalisePhone(req.phone()));
        if (u.roleCode() != RoleCode.CITIZEN) {
            applyRole(u, req.role(), req.wardId(), req.departmentId());
        }
        audit.record(admin.id(), "ACCOUNT_UPDATED", "USER", u.getId(), describe(u));
        return AdminDtos.AccountDto.from(u);
    }

    @Transactional
    public AdminDtos.AccountDto setEnabled(UserPrincipal admin, Long id, boolean enabled) {
        User u = load(id);
        if (u.getId().equals(admin.id()) && !enabled) {
            throw ApiException.badRequest("SELF_DEACTIVATION", "You cannot deactivate your own account");
        }
        if (u.isEnabled() != enabled) {
            u.setEnabled(enabled);
            audit.record(admin.id(), enabled ? "ACCOUNT_ACTIVATED" : "ACCOUNT_DEACTIVATED", "USER", u.getId(), u.getEmail());
        }
        return AdminDtos.AccountDto.from(u);
    }

    /** Administrator-assisted password reset (the project has no email provider for self-service resets). */
    @Transactional
    public void resetPassword(UserPrincipal admin, Long id, String newPassword) {
        User u = load(id);
        u.setPasswordHash(passwordEncoder.encode(newPassword));
        audit.record(admin.id(), "PASSWORD_RESET_BY_ADMIN", "USER", u.getId(), u.getEmail());
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminDtos.AuditDto> auditLog(String action, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        Page<AuditEvent> events = action == null || action.isBlank()
                ? auditEvents.findAllByOrderByCreatedAtDescIdDesc(pageable)
                : auditEvents.findByActionOrderByCreatedAtDescIdDesc(action.trim(), pageable);
        Map<Long, User> actors = users.findAllById(events.getContent().stream()
                        .map(AuditEvent::getActorId).filter(java.util.Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return PageResponse.of(events, e -> {
            User actor = e.getActorId() == null ? null : actors.get(e.getActorId());
            return new AdminDtos.AuditDto(e.getId(), e.getCreatedAt(), e.getAction(), e.getEntityType(), e.getEntityId(),
                    actor == null ? "System" : actor.getFullName(), actor == null ? null : actor.getEmail(),
                    e.getDetails(), e.getIpAddress());
        });
    }

    /** Officials need exactly the assignment their role works from: a ward for ward heads, a department for department staff. */
    private void applyRole(User u, RoleCode role, Long wardId, Long departmentId) {
        Ward ward = null;
        Department department = null;
        switch (role) {
            case WARD_HEAD -> ward = wards.findById(requireId(wardId, "Choose the ward this ward head manages"))
                    .orElseThrow(() -> ApiException.badRequest("INVALID_WARD", "Choose a valid ward"));
            case DEPARTMENT_HEAD, OFFICER -> department = departments.findById(requireId(departmentId, "Choose the department"))
                    .orElseThrow(() -> ApiException.badRequest("INVALID_DEPARTMENT", "Choose a valid department"));
            case ADMIN, CITIZEN -> {
            }
        }
        u.setRole(roles.findByCode(role).orElseThrow());
        u.setWard(ward);
        u.setDepartment(department);
    }

    private static Long requireId(Long id, String message) {
        if (id == null) {
            throw ApiException.badRequest("ASSIGNMENT_REQUIRED", message);
        }
        return id;
    }

    private User load(Long id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("Account"));
    }

    private static String describe(User u) {
        return "email=" + u.getEmail() + " role=" + u.roleCode()
                + (u.getWard() == null ? "" : " ward=" + u.getWard().getCode())
                + (u.getDepartment() == null ? "" : " department=" + u.getDepartment().getCode());
    }
}
