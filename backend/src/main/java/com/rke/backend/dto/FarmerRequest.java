package com.rke.backend.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record FarmerRequest(
        @NotBlank(message = "Farmer name is required")
        String name,

        String fatherName,

        @NotNull(message = "Village is required")
        UUID villageId,

        String address,

        // Optional; when provided must be a 10-digit Indian mobile number (starts 6-9).
        @Pattern(regexp = "^$|^[6-9][0-9]{9}$",
                message = "Mobile number must be 10 digits and start with 6-9")
        String mobileNumber,

        String reference
) {
}
