package com.primepay.addfunds;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Pay-in gateway integration. Only a simulated gateway exists: no card is charged and no real money moves.
 * A real gateway is added behind this interface with sandbox keys, its hosted checkout page and verified
 * webhook signatures.
 */
public interface PaymentGateway {

    record Charge(boolean success, String gatewayRef, String reason) {
    }

    Charge charge(String sessionRef, String mode, String cardNetwork, String cardLast4, String upiId, BigDecimal amount);

    /**
     * Deterministic simulation: a card ending 0000, or a UPI ID starting with "fail", is declined;
     * everything else is approved.
     */
    @Component
    class MockPaymentGateway implements PaymentGateway {
        @Override
        public Charge charge(String sessionRef, String mode, String cardNetwork, String cardLast4, String upiId, BigDecimal amount) {
            String ref = "SIMPG" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
            if ("CARD".equals(mode) && "0000".equals(cardLast4)) {
                return new Charge(false, ref, "Card declined by the issuing bank (simulated)");
            }
            if ("UPI".equals(mode) && upiId != null && upiId.toLowerCase().startsWith("fail")) {
                return new Charge(false, ref, "UPI payment declined (simulated)");
            }
            return new Charge(true, ref, null);
        }
    }
}
