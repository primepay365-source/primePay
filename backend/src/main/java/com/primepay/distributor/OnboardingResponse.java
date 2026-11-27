package com.primepay.distributor;

import java.time.OffsetDateTime;

public record OnboardingResponse(
        Long onboardingId,
        Long distributorAccountId,
        String accountCode,
        String username,
        String initialPassword,
        String fullName,
        String status,
        OffsetDateTime submittedAt) {

    public static OnboardingResponse from(DistributorOnboarding onboarding, String initialPassword) {
        return new OnboardingResponse(
                onboarding.getId(),
                onboarding.getDistributor().getId(),
                onboarding.getDistributor().getAccountCode(),
                onboarding.getDistributor().getAccountCode().toLowerCase(java.util.Locale.ROOT),
                initialPassword,
                onboarding.getFullName(),
                onboarding.getStatus(),
                onboarding.getSubmittedAt());
    }
}
