package com.primepay.beneficiary;

import com.primepay.audit.AuditService;
import com.primepay.common.api.ApiResponse;
import com.primepay.common.error.BusinessException;
import com.primepay.security.CurrentActor;
import com.primepay.transaction.TransactionQueryService;
import com.primepay.transaction.TransactionView;
import com.primepay.user.AgentAccount;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Withdraw funds")
@RestController
@RequestMapping("/api/v1")
public class BeneficiaryController {
    private final BeneficiaryService service;
    private final CurrentActor currentActor;
    private final TransactionQueryService query;
    private final AuditService audit;

    public BeneficiaryController(BeneficiaryService service, CurrentActor currentActor, TransactionQueryService query, AuditService audit) {
        this.service = service;
        this.currentActor = currentActor;
        this.query = query;
        this.audit = audit;
    }

    @GetMapping("/beneficiaries")
    public ApiResponse<List<BeneficiaryService.BeneficiaryView>> list() {
        return ApiResponse.ok(service.list(currentActor.require()));
    }

    public record AddBody(@NotBlank @Pattern(regexp = "[0-9]{9,18}", message = "Account number must be 9 to 18 digits") String accountNumber,
                          @NotBlank @Pattern(regexp = "[A-Za-z]{4}0[A-Za-z0-9]{6}", message = "Enter a valid IFSC, e.g. HDFC0001234") String ifsc,
                          @NotBlank @Size(min = 2, max = 120) String holderName) {
    }

    @Operation(summary = "Add a bank account; it is verified with the bank and saved only as VERIFIED or REJECTED")
    @PostMapping("/beneficiaries")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BeneficiaryService.AddResult> add(@Valid @RequestBody AddBody body) {
        BeneficiaryService.AddResult r = service.add(currentActor.require(), body.accountNumber(), body.ifsc(), body.holderName());
        return ApiResponse.ok(r, r.verified() ? "Bank account verified and added" : "Bank account could not be verified");
    }

    @DeleteMapping("/beneficiaries/{id}")
    public ApiResponse<Void> remove(@PathVariable Long id) {
        service.remove(currentActor.require(), id);
        return ApiResponse.ok(null, "Bank account removed");
    }

    public record WithdrawBody(@NotNull Long beneficiaryId,
                               @NotNull @DecimalMin("1.00") @Digits(integer = 17, fraction = 2) BigDecimal amount,
                               @NotBlank @Pattern(regexp = "IMPS|NEFT") String mode) {
    }

    @Operation(summary = "Transfer from the main wallet to a verified saved bank account. Requires an Idempotency-Key header.")
    @PostMapping("/withdraw")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TransactionView> withdraw(@RequestHeader("Idempotency-Key") String idempotencyKey, @Valid @RequestBody WithdrawBody body) {
        AgentAccount me = currentActor.require();
        try {
            return ApiResponse.ok(TransactionView.of(service.withdraw(me, body.beneficiaryId(), body.amount(), body.mode(), idempotencyKey), query.accountMap()));
        } catch (BusinessException e) {
            audit.recordIndependent(me.getId(), me.getAccountCode(), "PAYOUT", "TRANSACTION", null, AuditService.FAILED, e.code() + ": " + e.getMessage());
            throw e;
        }
    }
}
