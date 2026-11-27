package com.primepay.beneficiary;

import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Bank account verification (in production a penny-drop / name-lookup API). Only a simulation exists:
 * no bank is contacted. Deterministic for testing: an account number ending 9999 is reported as invalid;
 * otherwise the bank "returns" the holder name in upper case.
 */
public interface BankVerifier {

    record Result(boolean valid, String registeredName, String bankName, String reason) {
    }

    Result verify(String accountNumber, String ifsc, String holderName);

    @Component
    class MockBankVerifier implements BankVerifier {
        private static final Map<String, String> BANKS = Map.ofEntries(
                Map.entry("HDFC", "HDFC Bank"), Map.entry("ICIC", "ICICI Bank"), Map.entry("SBIN", "State Bank of India"),
                Map.entry("UTIB", "Axis Bank"), Map.entry("KKBK", "Kotak Mahindra Bank"), Map.entry("PUNB", "Punjab National Bank"),
                Map.entry("BARB", "Bank of Baroda"), Map.entry("CNRB", "Canara Bank"), Map.entry("FDRL", "Federal Bank"),
                Map.entry("IDFB", "IDFC FIRST Bank"), Map.entry("YESB", "Yes Bank"), Map.entry("UBIN", "Union Bank of India"));

        @Override
        public Result verify(String accountNumber, String ifsc, String holderName) {
            String bank = BANKS.getOrDefault(ifsc.substring(0, 4).toUpperCase(Locale.ROOT), ifsc.substring(0, 4).toUpperCase(Locale.ROOT) + " Bank");
            if (accountNumber.endsWith("9999")) {
                return new Result(false, null, bank, "The bank could not find this account (simulated)");
            }
            return new Result(true, holderName.trim().toUpperCase(Locale.ROOT), bank, null);
        }
    }
}
