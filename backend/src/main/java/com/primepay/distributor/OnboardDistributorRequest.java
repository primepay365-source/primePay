package com.primepay.distributor;

import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OnboardDistributorRequest(
        @NotBlank @Size(min = 3, max = 40) @Pattern(regexp = "[A-Za-z0-9_-]+") String accountCode,
        @NotBlank @Size(max = 160) String fullName,
        @NotBlank @Pattern(regexp = "\\+?[0-9]{7,15}") String mobile,
        @NotBlank @Email @Size(max = 160) String email,
        @NotBlank @Size(max = 500) String address,
        @NotBlank @Pattern(regexp = "PAN|AADHAAR|PASSPORT|DRIVING_LICENSE") String kycDocumentType,
        @NotBlank @Pattern(regexp = "[0-9A-Za-z]{4}") String kycLastFour,
        @NotBlank @Size(max = 160) String bankAccountHolder,
        @NotBlank @Size(max = 120) String bankName,
        @NotBlank @Pattern(regexp = "[A-Za-z]{4}0[A-Za-z0-9]{6}") String bankIfsc,
        @NotBlank @Pattern(regexp = "[0-9]{4}") String bankAccountLastFour,
        @NotNull @DecimalMin("0.01") BigDecimal velocityPerTransaction,
        @NotNull @DecimalMin("0.01") BigDecimal velocityDaily,
        @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal commissionPayin,
        @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal commissionPayout,
        @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal commissionBillPayment,
        @NotBlank @Size(max = 40) String agreementVersion) {
}
