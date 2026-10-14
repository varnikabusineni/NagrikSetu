package com.nagriksetu.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class AuthRequests {

    private AuthRequests() {
    }

    public static final String PHONE_REGEX = "^(\\+?91[\\s-]?)?[6-9]\\d{4}[\\s-]?\\d{5}$";
    public static final String PASSWORD_REGEX = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$";

    public record Register(
            @NotBlank(message = "Name is required") @Size(min = 2, max = 120) String fullName,
            @NotBlank(message = "Email is required") @Email(message = "Enter a valid email") @Size(max = 254) String email,
            @NotBlank(message = "Phone number is required")
            @Pattern(regexp = PHONE_REGEX, message = "Enter a valid 10-digit Indian mobile number") String phone,
            @NotBlank(message = "Password is required")
            @Pattern(regexp = PASSWORD_REGEX, message = "Use 8-72 characters with at least one letter and one number")
            String password,
            @NotBlank(message = "Locality is required") @Size(max = 120) String locality,
            @Size(max = 500) String address) {
    }

    public record Login(@NotBlank @Email String email, @NotBlank @Size(max = 72) String password) {
    }

    public record UpdateProfile(
            @NotBlank(message = "Name is required") @Size(min = 2, max = 120) String fullName,
            @NotBlank(message = "Phone number is required")
            @Pattern(regexp = PHONE_REGEX, message = "Enter a valid 10-digit Indian mobile number") String phone,
            @NotBlank(message = "Locality is required") @Size(max = 120) String locality,
            @Size(max = 500) String address) {
    }

    public record ChangePassword(
            @NotBlank String currentPassword,
            @NotBlank @Pattern(regexp = PASSWORD_REGEX, message = "Use 8-72 characters with at least one letter and one number")
            String newPassword) {
    }

    public record AuthResponse(String token, Instant expiresAt, UserDto user) {
    }
}
