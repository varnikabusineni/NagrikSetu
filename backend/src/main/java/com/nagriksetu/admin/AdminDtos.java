package com.nagriksetu.admin;

import com.nagriksetu.user.AuthRequests;
import com.nagriksetu.user.RoleCode;
import com.nagriksetu.user.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class AdminDtos {

    private AdminDtos() {
    }

    static final String PHONE_REGEX = AuthRequests.PHONE_REGEX;
    static final String PASSWORD_REGEX = AuthRequests.PASSWORD_REGEX;
    static final String PASSWORD_MESSAGE = "Use 8-72 characters with at least one letter and one number";

    public record AccountDto(Long id, String fullName, String email, String phone, RoleCode role, String roleName,
                             Long wardId, String wardName, Long departmentId, String departmentName, boolean enabled,
                             Instant createdAt, Instant lastLoginAt) {

        static AccountDto from(User u) {
            return new AccountDto(u.getId(), u.getFullName(), u.getEmail(), u.getPhone(), u.roleCode(), u.getRole().getName(),
                    u.getWard() == null ? null : u.getWard().getId(),
                    u.getWard() == null ? null : u.getWard().getName(),
                    u.getDepartment() == null ? null : u.getDepartment().getId(),
                    u.getDepartment() == null ? null : u.getDepartment().getName(),
                    u.isEnabled(), u.getCreatedAt(), u.getLastLoginAt());
        }
    }

    public record CreateOfficial(
            @NotBlank(message = "Name is required") @Size(min = 2, max = 120) String fullName,
            @NotBlank(message = "Email is required") @Email(message = "Enter a valid email") @Size(max = 254) String email,
            @NotBlank(message = "Phone number is required")
            @Pattern(regexp = PHONE_REGEX, message = "Enter a valid 10-digit Indian mobile number") String phone,
            @NotBlank(message = "Set a temporary password") @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MESSAGE)
            String password,
            @NotNull(message = "Choose a role") RoleCode role,
            Long wardId,
            Long departmentId) {
    }

    public record UpdateAccount(
            @NotBlank(message = "Name is required") @Size(min = 2, max = 120) String fullName,
            @NotBlank(message = "Phone number is required")
            @Pattern(regexp = PHONE_REGEX, message = "Enter a valid 10-digit Indian mobile number") String phone,
            @NotNull(message = "Choose a role") RoleCode role,
            Long wardId,
            Long departmentId) {
    }

    public record StatusRequest(@NotNull Boolean enabled) {
    }

    public record ResetPassword(
            @NotBlank(message = "Set a new password") @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MESSAGE)
            String newPassword) {
    }

    public record AuditDto(Long id, Instant createdAt, String action, String entityType, String entityId,
                           String actorName, String actorEmail, String details, String ipAddress) {
    }
}
