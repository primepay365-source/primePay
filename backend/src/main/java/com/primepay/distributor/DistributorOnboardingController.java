package com.primepay.distributor;

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
    private final ApprovalService approvals;
    private final com.primepay.security.CurrentActor currentActor;

    public DistributorOnboardingController(DistributorOnboardingService onboardingService, ApprovalService approvals,
                                           com.primepay.security.CurrentActor currentActor) {
        this.onboardingService = onboardingService;
        this.approvals = approvals;
        this.currentActor = currentActor;
    }

    @org.springframework.web.bind.annotation.GetMapping("/applications")
    public com.primepay.common.api.ApiResponse<java.util.List<ApprovalService.Application>> applications() {
        return com.primepay.common.api.ApiResponse.ok(approvals.list(currentActor.require()));
    }

    public record DecisionBody(@jakarta.validation.constraints.Size(max = 500) String note) {
    }

    @PostMapping("/applications/{id}/approve")
    public com.primepay.common.api.ApiResponse<ApprovalService.Application> approve(@org.springframework.web.bind.annotation.PathVariable Long id,
            @RequestBody(required = false) DecisionBody body) {
        return com.primepay.common.api.ApiResponse.ok(approvals.decide(currentActor.require(), id, true, body == null ? null : body.note()), "Application approved");
    }

    @PostMapping("/applications/{id}/reject")
    public com.primepay.common.api.ApiResponse<ApprovalService.Application> reject(@org.springframework.web.bind.annotation.PathVariable Long id,
            @RequestBody DecisionBody body) {
        return com.primepay.common.api.ApiResponse.ok(approvals.decide(currentActor.require(), id, false, body.note()), "Application rejected");
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
