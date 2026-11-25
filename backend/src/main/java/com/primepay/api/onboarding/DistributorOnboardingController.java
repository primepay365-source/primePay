package com.primepay.api.onboarding;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/onboarding/distributors")
public class DistributorOnboardingController {
    private final DistributorOnboardingService onboardingService;

    public DistributorOnboardingController(DistributorOnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    @PostMapping("/otp/request")
    public OtpResponse requestOtp(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody OtpRequest request) {
        return onboardingService.requestOtp(jwt, request);
    }

    @PostMapping("/otp/verify")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verifyOtp(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody OtpVerificationRequest request) {
        onboardingService.verifyOtp(jwt, request);
    }

    @PostMapping(consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public OnboardingResponse submit(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestPart("request") OnboardDistributorRequest request,
            @RequestPart("photo") MultipartFile photo) {
        return onboardingService.submit(jwt, request, photo);
    }
}
