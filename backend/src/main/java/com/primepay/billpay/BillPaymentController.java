package com.primepay.billpay;

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
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Bill payment")
@RestController
@RequestMapping("/api/v1/bills")
public class BillPaymentController {
    private final BillPaymentService bills;
    private final CurrentActor currentActor;
    private final TransactionQueryService query;
    private final AuditService audit;

    public BillPaymentController(BillPaymentService bills, CurrentActor currentActor, TransactionQueryService query, AuditService audit) {
        this.bills = bills;
        this.currentActor = currentActor;
        this.query = query;
        this.audit = audit;
    }

    @GetMapping("/categories")
    public ApiResponse<List<Map<String, String>>> categories() {
        currentActor.require();
        return ApiResponse.ok(List.of(Map.of("code", "CREDIT_CARD", "name", "Credit card")));
    }

    @GetMapping("/billers")
    public ApiResponse<List<BillPaymentService.BillerView>> billers(@RequestParam(defaultValue = "CREDIT_CARD") String category) {
        currentActor.require();
        return ApiResponse.ok(bills.billers(category));
    }

    public record FetchBody(@NotNull Long billerId,
                            @NotBlank @Pattern(regexp = "[0-9]{4}", message = "Enter the last 4 digits of the card") String cardLast4,
                            @NotBlank @Pattern(regexp = "[6-9][0-9]{9}", message = "Enter the 10-digit mobile registered with the card") String mobile) {
    }

    @Operation(summary = "Fetch the bill for a credit card (last 4 digits + registered mobile)")
    @PostMapping("/fetch")
    public ApiResponse<BillPaymentService.FetchedBillView> fetch(@Valid @RequestBody FetchBody body) {
        return ApiResponse.ok(bills.fetch(currentActor.require(), body.billerId(), body.cardLast4(), body.mobile()));
    }

    public record PayBody(@NotBlank String fetchRef,
                          @NotNull @DecimalMin("1.00") @Digits(integer = 17, fraction = 2) BigDecimal amount) {
    }

    @Operation(summary = "Pay a fetched bill from the main wallet. Requires an Idempotency-Key header.")
    @PostMapping("/pay")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TransactionView> pay(@RequestHeader("Idempotency-Key") String idempotencyKey, @Valid @RequestBody PayBody body) {
        AgentAccount me = currentActor.require();
        try {
            return ApiResponse.ok(TransactionView.of(bills.pay(me, body.fetchRef(), body.amount(), idempotencyKey), query.accountMap()));
        } catch (BusinessException e) {
            audit.recordIndependent(me.getId(), me.getAccountCode(), "BILL_PAYMENT", "TRANSACTION", null, AuditService.FAILED, e.code() + ": " + e.getMessage());
            throw e;
        }
    }

    @Operation(summary = "Ask the biller again for the status of a PENDING payment")
    @PostMapping("/{transactionId}/recheck")
    public ApiResponse<TransactionView> recheck(@PathVariable Long transactionId) {
        return ApiResponse.ok(TransactionView.of(bills.recheck(currentActor.require(), transactionId), query.accountMap()));
    }
}
