package com.primepay.api.onboarding;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record OtpVerificationRequest(
        @NotBlank @Pattern(regexp = "\\+?[0-9]{7,15}") String mobile,
        @NotBlank @Pattern(regexp = "[0-9]{6}") String code) {
}
