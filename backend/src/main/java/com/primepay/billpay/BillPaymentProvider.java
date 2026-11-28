package com.primepay.billpay;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Bill fetch and payment integration (e.g. a BBPS operating unit). Only a simulated provider exists; a real
 * one is added behind this interface once approved credentials are available. No real bill is paid.
 */
public interface BillPaymentProvider {

    record FetchedBill(String customerName, BigDecimal totalDue, BigDecimal minimumDue, LocalDate dueDate, String providerRef) {
    }

    /** SUCCESS, FAILED or PENDING, as a bill aggregator reports it. */
    record PaymentResult(String status, String providerRef, String reason) {
    }

    FetchedBill fetch(String billerCode, String cardLast4, String mobile);

    PaymentResult pay(String billerCode, String fetchProviderRef, String cardLast4, BigDecimal amount);

    /** Status enquiry for a PENDING payment. */
    PaymentResult status(String providerRef);

    /**
     * Deterministic simulation so every outcome can be tried:
     * card ending 9999 → no bill found; 0000 → payment fails; 1111 → payment stays PENDING until
     * rechecked (then succeeds); 2222 → PENDING then FAILED on recheck; anything else succeeds.
     */
    @Component
    class MockBillPaymentProvider implements BillPaymentProvider {
        private static final String[] NAMES = {"Asha Verma", "Rahul Menon", "Neha Kapoor", "Imran Shaikh", "Divya Rao", "Karan Gill"};

        @Override
        public FetchedBill fetch(String billerCode, String cardLast4, String mobile) {
            if ("9999".equals(cardLast4)) {
                return null;
            }
            int seed = Math.abs((billerCode + cardLast4 + mobile).hashCode());
            BigDecimal total = BigDecimal.valueOf(2_000 + seed % 48_000).add(BigDecimal.valueOf(seed % 100, 2));
            BigDecimal minimum = total.multiply(new BigDecimal("0.05")).setScale(2, java.math.RoundingMode.HALF_UP).max(new BigDecimal("200.00"));
            return new FetchedBill(NAMES[seed % NAMES.length], total, minimum.min(total), LocalDate.now().plusDays(3 + seed % 15),
                    "SIMBILL" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }

        @Override
        public PaymentResult pay(String billerCode, String fetchProviderRef, String cardLast4, BigDecimal amount) {
            String ref = ("0000".equals(cardLast4) ? "SIMFAIL" : "1111".equals(cardLast4) ? "SIMPEND" : "2222".equals(cardLast4) ? "SIMPNDF" : "SIMPAID")
                    + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
            return switch (cardLast4) {
                case "0000" -> new PaymentResult("FAILED", ref, "Biller declined the payment (simulated)");
                case "1111", "2222" -> new PaymentResult("PENDING", ref, "Awaiting confirmation from the biller (simulated)");
                default -> new PaymentResult("SUCCESS", ref, null);
            };
        }

        @Override
        public PaymentResult status(String providerRef) {
            if (providerRef.startsWith("SIMPNDF")) {
                return new PaymentResult("FAILED", providerRef, "Biller could not post the payment (simulated)");
            }
            return new PaymentResult("SUCCESS", providerRef, null);
        }
    }
}
