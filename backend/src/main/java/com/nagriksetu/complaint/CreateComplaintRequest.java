package com.nagriksetu.complaint;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Multipart form fields for a new complaint. Fields such as status, priority, ward, department or
 * assignee are deliberately absent: the server decides them, and any such extra fields are ignored.
 */
public record CreateComplaintRequest(
        @NotBlank(message = "Title is required") @Size(min = 5, max = 150, message = "Title must be 5-150 characters")
        String title,
        @NotBlank(message = "Description is required")
        @Size(min = 10, max = 4000, message = "Description must be 10-4000 characters")
        String description,
        @NotBlank(message = "Choose a category") String categoryCode,
        @NotBlank(message = "Locality is required") @Size(max = 120) String locality,
        @NotBlank(message = "Address or landmark is required") @Size(max = 500) String address,
        // Service area: India. Rejects swapped coordinates and the (0,0) "null island" default.
        @NotNull(message = "Pick the location on the map")
        @DecimalMin(value = "6.0", message = "Location must be within India")
        @DecimalMax(value = "37.5", message = "Location must be within India")
        BigDecimal latitude,
        @NotNull(message = "Pick the location on the map")
        @DecimalMin(value = "68.0", message = "Location must be within India")
        @DecimalMax(value = "97.5", message = "Location must be within India")
        BigDecimal longitude,
        Boolean isPublic,
        UUID clientRequestId) {
}
