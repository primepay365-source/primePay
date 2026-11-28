package com.primepay.transaction;

import com.primepay.audit.AuditService;
import com.primepay.commission.CommissionEngine;
import com.primepay.common.api.ApiResponse;
import com.primepay.common.api.PageResponse;
import com.primepay.common.error.BusinessException;
import com.primepay.common.web.CsvWriter;
import com.primepay.ledger.LedgerEntryRepository;
import com.primepay.security.CurrentActor;
import com.primepay.user.AgentAccount;
import com.primepay.wallet.TransferService;
import com.primepay.wallet.WalletRepository;
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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Transactions")
@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {
    private final CurrentActor currentActor;
    private final TransactionQueryService query;
    private final PayoutService payouts;
    private final ReversalService reversals;
    private final TransferService transfers;
    private final TransactionStatusHistoryRepository history;
    private final LedgerEntryRepository ledger;
    private final WalletRepository wallets;
    private final CommissionEngine commission;
    private final AuditService audit;

    public TransactionController(CurrentActor currentActor, TransactionQueryService query, PayoutService payouts,
                                 ReversalService reversals, TransferService transfers, TransactionStatusHistoryRepository history,
                                 LedgerEntryRepository ledger, WalletRepository wallets, CommissionEngine commission, AuditService audit) {
        this.currentActor = currentActor;
        this.query = query;
        this.payouts = payouts;
        this.reversals = reversals;
        this.transfers = transfers;
        this.history = history;
        this.ledger = ledger;
        this.wallets = wallets;
        this.commission = commission;
        this.audit = audit;
    }

    @GetMapping
    public ApiResponse<PageResponse<TransactionView>> search(@RequestParam(required = false) String q,
                                                             @RequestParam(required = false) TxnType type,
                                                             @RequestParam(required = false) TxnStatus status,
                                                             @RequestParam(required = false) LocalDate from,
                                                             @RequestParam(required = false) LocalDate to,
                                                             @RequestParam(required = false) Long accountId,
                                                             @RequestParam(defaultValue = "0") int page,
                                                             @RequestParam(defaultValue = "25") int size) {
        var result = query.search(currentActor.require(), new TransactionQueryService.Filter(q, type, status, from, to, accountId), page, size);
        return ApiResponse.ok(new PageResponse<>(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages()));
    }

    @GetMapping("/status-counts")
    public ApiResponse<Map<TxnStatus, Long>> counts(@RequestParam(required = false) String q,
                                                    @RequestParam(required = false) TxnType type,
                                                    @RequestParam(required = false) LocalDate from,
                                                    @RequestParam(required = false) LocalDate to) {
        return ApiResponse.ok(query.countsByStatus(currentActor.require(), new TransactionQueryService.Filter(q, type, null, from, to, null)));
    }

    @GetMapping(value = "/export.csv", produces = "text/csv")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String q,
                                         @RequestParam(required = false) TxnType type,
                                         @RequestParam(required = false) TxnStatus status,
                                         @RequestParam(required = false) LocalDate from,
                                         @RequestParam(required = false) LocalDate to) {
        var rows = query.search(currentActor.require(), new TransactionQueryService.Filter(q, type, status, from, to, null), 0, 5000);
        CsvWriter csv = new CsvWriter("Reference", "Date", "Type", "From", "To", "Amount", "Fee", "Status", "Remarks");
        rows.forEach(t -> csv.row(t.referenceNo(), t.createdAt(), t.typeLabel(), t.fromCode(), t.toCode(), t.amount(), t.fee(), t.status(), t.remarks()));
        return csv.download("primepay-transactions.csv");
    }

    public record LedgerLine(String entryRef, String walletCode, String direction, String entryType, BigDecimal amount,
                             BigDecimal balanceAfter, String description, OffsetDateTime createdAt) {
    }

    public record HistoryLine(TxnStatus fromStatus, TxnStatus toStatus, String note, OffsetDateTime changedAt) {
    }

    public record CommissionLine(String levelRole, String earnerCode, BigDecimal amount, String status) {
    }

    public record Detail(TransactionView transaction, List<HistoryLine> history, List<LedgerLine> ledger,
                         List<CommissionLine> commission, List<TransactionView> related) {
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ApiResponse<Detail> detail(@PathVariable Long id) {
        AgentAccount actor = currentActor.require();
        FinancialTransaction t = query.requireVisible(actor, id);
        Map<Long, AgentAccount> byId = query.accountMap();
        var walletCodes = new java.util.HashMap<Long, String>();
        wallets.findAll().forEach(w -> walletCodes.put(w.getId(), w.getWalletCode()));
        List<LedgerLine> lines = ledger.findByTransactionIdOrderByIdAsc(t.getId()).stream()
                .map(e -> new LedgerLine(e.getEntryRef(), walletCodes.get(e.getWalletId()), e.getDirection(), e.getEntryType(),
                        e.getAmount(), e.getBalanceAfter(), e.getDescription(), e.getCreatedAt()))
                .toList();
        List<CommissionLine> comm = commission.entriesFor(t.getId()).stream()
                .map(c -> new CommissionLine(c.getLevelRole(), c.getEarnerAccountId() == null ? "Platform" : byId.get(c.getEarnerAccountId()).getAccountCode(),
                        c.getCommissionAmount(), c.getStatus()))
                .toList();
        List<TransactionView> related = new java.util.ArrayList<>();
        if (t.getOriginalTxnId() != null) {
            related.add(TransactionView.of(query.requireVisible(actor, t.getOriginalTxnId()), byId));
        }
        query.childrenOf(t.getId()).forEach(c -> related.add(TransactionView.of(c, byId)));
        return ApiResponse.ok(new Detail(TransactionView.of(t, byId),
                history.findByTransactionIdOrderByIdAsc(t.getId()).stream()
                        .map(h -> new HistoryLine(h.getFromStatus(), h.getToStatus(), h.getNote(), h.getChangedAt())).toList(),
                lines, comm, related));
    }

    public record ReverseBody(@NotBlank @Size(max = 300) String reason) {
    }

    @PostMapping("/{id}/reverse")
    public ApiResponse<TransactionView> reverse(@PathVariable Long id, @Valid @RequestBody ReverseBody body) {
        FinancialTransaction reversal = reversals.reverse(currentActor.require(), id, body.reason());
        return ApiResponse.ok(TransactionView.of(reversal, query.accountMap()), "Transaction reversed");
    }

    public record DecisionBody(@Size(max = 300) String note) {
    }

    @PostMapping("/{id}/approve-load")
    public ApiResponse<TransactionView> approveLoad(@PathVariable Long id, @RequestBody(required = false) DecisionBody body) {
        FinancialTransaction txn = transfers.decideLoad(currentActor.require(), id, true, body == null ? null : body.note());
        return ApiResponse.ok(TransactionView.of(txn, query.accountMap()), "Load approved");
    }

    @PostMapping("/{id}/reject-load")
    public ApiResponse<TransactionView> rejectLoad(@PathVariable Long id, @RequestBody(required = false) DecisionBody body) {
        FinancialTransaction txn = transfers.decideLoad(currentActor.require(), id, false, body == null ? null : body.note());
        return ApiResponse.ok(TransactionView.of(txn, query.accountMap()), "Load rejected");
    }
}
