package com.primepay.transaction;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Bank payout integration. Only the mock exists today: no real money moves. A real provider is added
 * behind this interface once sandbox credentials, approvals and webhook signature checks are in place.
 */
public interface PayoutProvider {
    Result send(String referenceNo, String accountNumber, String ifsc, String beneficiaryName, BigDecimal amount, String mode);

    record Result(boolean success, String providerRef, String failureReason) {
    }

    /**
     * Simulated provider. Deterministic for testing: an account number ending in 0000 is declined
     * ("beneficiary account closed"); everything else succeeds with a fake UTR.
     */
    @Component
    class MockPayoutProvider implements PayoutProvider {
        @Override
        public Result send(String referenceNo, String accountNumber, String ifsc, String beneficiaryName, BigDecimal amount, String mode) {
            if (accountNumber.endsWith("0000")) {
                return new Result(false, null, "Bank declined: beneficiary account closed (simulated)");
            }
            return new Result(true, "SIM" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(), null);
        }
    }
}
