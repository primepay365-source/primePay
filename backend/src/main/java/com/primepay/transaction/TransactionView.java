package com.primepay.transaction;

import com.primepay.user.AgentAccount;
import com.primepay.user.AgentAccountRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

/** Transaction as returned by the API. Bank account numbers are only ever shown as the last 4 digits. */
public record TransactionView(Long id, String referenceNo, TxnType type, String typeLabel, TxnStatus status, String serviceCode,
                              BigDecimal amount, BigDecimal fee, String initiatorCode, String fromCode, String fromName,
                              String toCode, String toName, String remarks, String externalRef, String payoutMode,
                              String beneficiaryName, String beneficiaryAccountLast4, String beneficiaryIfsc, String beneficiaryBank,
                              String failureReason, Long originalTxnId, OffsetDateTime createdAt, OffsetDateTime updatedAt,
                              String billerName, String customerName, String cardLast4, String cardNetwork, String gatewayCategory,
                              String customerMobileMasked) {

    public static TransactionView of(FinancialTransaction t, AgentAccountRepository accounts) {
        return of(t, id -> id == null ? null : accounts.findById(id).orElse(null));
    }

    public static TransactionView of(FinancialTransaction t, Map<Long, AgentAccount> byId) {
        return of(t, id -> id == null ? null : byId.get(id));
    }

    private static TransactionView of(FinancialTransaction t, java.util.function.Function<Long, AgentAccount> lookup) {
        AgentAccount initiator = lookup.apply(t.getInitiatorAccountId());
        AgentAccount from = lookup.apply(t.getSourceAccountId());
        AgentAccount to = lookup.apply(t.getTargetAccountId());
        String fromCode = from == null ? (t.getType() == TxnType.WALLET_LOAD ? "Bank " + t.getPayoutMode()
                : t.getType() == TxnType.ADD_FUNDS ? ("UPI".equals(t.getPayoutMode()) ? "UPI" : t.getCardNetwork() + " card ••" + t.getCardLast4())
                : t.getType() == TxnType.COMMISSION ? "Platform" : null) : from.getAccountCode();
        String toCode = to == null ? (t.getType() == TxnType.PAYOUT ? "Bank a/c ••" + t.getBeneficiaryAccountLast4()
                : t.getType() == TxnType.BILL_PAYMENT ? t.getBillerName() + " ••" + t.getCardLast4()
                : t.getType() == TxnType.COMMISSION ? "Network shares" : null) : to.getAccountCode();
        return new TransactionView(t.getId(), t.getReferenceNo(), t.getType(), t.getType().label(), t.getStatus(), t.getServiceCode(),
                t.getAmount(), t.getFee(), initiator == null ? null : initiator.getAccountCode(), fromCode,
                from == null ? null : from.getDisplayName(), toCode, to == null ? t.getBeneficiaryName() : to.getDisplayName(),
                t.getRemarks(), t.getExternalRef(), t.getPayoutMode(), t.getBeneficiaryName(), t.getBeneficiaryAccountLast4(),
                t.getBeneficiaryIfsc(), t.getBeneficiaryBank(), t.getFailureReason(), t.getOriginalTxnId(), t.getCreatedAt(), t.getUpdatedAt(),
                t.getBillerName(), t.getCustomerName(), t.getCardLast4(), t.getCardNetwork(), t.getGatewayCategory(), t.getCustomerMobileMasked());
    }
}
