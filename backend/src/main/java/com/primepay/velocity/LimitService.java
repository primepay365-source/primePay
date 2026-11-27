package com.primepay.velocity;

import com.primepay.common.error.BusinessException;
import com.primepay.common.error.ErrorCode;
import com.primepay.transaction.FinancialTransactionRepository;
import com.primepay.user.AgentAccount;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Velocity checks before any financial transaction. Day and month boundaries use the business time zone
 * (Asia/Kolkata by default). Call {@link #check} while holding the payer's wallet row lock: two concurrent
 * requests from the same payer are then serialised, so they can't both slip under a limit.
 */
@Service
public class LimitService {
    private final TransactionLimitRepository limits;
    private final FinancialTransactionRepository transactions;
    private final ZoneId zone;

    public LimitService(TransactionLimitRepository limits, FinancialTransactionRepository transactions,
                        @Value("${app.business-timezone:Asia/Kolkata}") String zone) {
        this.limits = limits;
        this.transactions = transactions;
        this.zone = ZoneId.of(zone);
    }

    /** The limit that applies: a current user override, else the role default, else none. */
    @Transactional(readOnly = true)
    public Optional<TransactionLimit> effective(AgentAccount account, String serviceCode) {
        Optional<TransactionLimit> override = limits.findByScopeAndAccountIdAndServiceCode("USER", account.getId(), serviceCode)
                .filter(l -> l.getValidUntil() == null || !l.getValidUntil().isBefore(LocalDate.now(zone)));
        if (override.isPresent()) {
            return override;
        }
        return limits.findByScopeAndRoleCodeAndServiceCode("ROLE", account.getRole().name(), serviceCode);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void check(AgentAccount account, String serviceCode, BigDecimal amount) {
        Optional<TransactionLimit> found = effective(account, serviceCode);
        if (found.isEmpty()) {
            return;
        }
        TransactionLimit limit = found.get();
        if (amount.compareTo(limit.getMinTxn()) < 0) {
            throw new BusinessException(ErrorCode.LIMIT_EXCEEDED, "The minimum amount is " + limit.getMinTxn().toPlainString() + ".");
        }
        if (amount.compareTo(limit.getMaxTxn()) > 0) {
            throw new BusinessException(ErrorCode.LIMIT_EXCEEDED, "The maximum per transaction is " + limit.getMaxTxn().toPlainString() + ".");
        }
        Usage usage = usage(account.getId(), serviceCode);
        if (usage.dayAmount().add(amount).compareTo(limit.getDailyAmount()) > 0) {
            throw new BusinessException(ErrorCode.LIMIT_EXCEEDED, "Daily limit reached: " + usage.dayAmount().toPlainString()
                    + " of " + limit.getDailyAmount().toPlainString() + " already used today. It resets at midnight (" + zone + ").");
        }
        if (usage.monthAmount().add(amount).compareTo(limit.getMonthlyAmount()) > 0) {
            throw new BusinessException(ErrorCode.LIMIT_EXCEEDED, "Monthly limit reached: " + usage.monthAmount().toPlainString()
                    + " of " + limit.getMonthlyAmount().toPlainString() + " already used this month.");
        }
        if (usage.dayCount() + 1 > limit.getDailyCount()) {
            throw new BusinessException(ErrorCode.LIMIT_EXCEEDED, "Daily transaction count limit (" + limit.getDailyCount() + ") reached.");
        }
        if (usage.monthCount() + 1 > limit.getMonthlyCount()) {
            throw new BusinessException(ErrorCode.LIMIT_EXCEEDED, "Monthly transaction count limit (" + limit.getMonthlyCount() + ") reached.");
        }
    }

    @Transactional(readOnly = true)
    public Usage usage(Long accountId, String serviceCode) {
        LocalDate today = LocalDate.now(zone);
        OffsetDateTime dayStart = today.atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime dayEnd = today.plusDays(1).atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime monthStart = today.withDayOfMonth(1).atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime monthEnd = today.withDayOfMonth(1).plusMonths(1).atStartOfDay(zone).toOffsetDateTime();
        return new Usage(
                transactions.sumUsage(accountId, serviceCode, dayStart, dayEnd),
                transactions.countUsage(accountId, serviceCode, dayStart, dayEnd),
                transactions.sumUsage(accountId, serviceCode, monthStart, monthEnd),
                transactions.countUsage(accountId, serviceCode, monthStart, monthEnd));
    }

    public record Usage(BigDecimal dayAmount, long dayCount, BigDecimal monthAmount, long monthCount) {
    }
}
