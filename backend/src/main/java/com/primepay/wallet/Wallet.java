package com.primepay.wallet;

import com.primepay.common.error.BusinessException;
import com.primepay.common.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Wallet balances. Changed only through LedgerService, which writes a ledger entry for every change.
 * Ledger balance = available + blocked.
 */
@Getter
@Entity
@Table(name = "pp_wallets")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Wallet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "wallet_code", nullable = false, unique = true, length = 40)
    private String walletCode;

    @Column(name = "account_id", nullable = false, unique = true)
    private Long accountId;

    @Column(name = "available_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal availableBalance = BigDecimal.ZERO;

    @Column(name = "blocked_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal blockedBalance = BigDecimal.ZERO;

    @Column(name = "total_credit", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalCredit = BigDecimal.ZERO;

    @Column(name = "total_debit", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalDebit = BigDecimal.ZERO;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Version
    private Long version;

    public Wallet(String walletCode, Long accountId) {
        this.walletCode = walletCode;
        this.accountId = accountId;
        this.createdAt = OffsetDateTime.now();
    }

    public BigDecimal getLedgerBalance() {
        return availableBalance.add(blockedBalance);
    }

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    /** Called only by LedgerService. */
    public void applyCredit(BigDecimal amount) {
        availableBalance = availableBalance.add(amount);
        totalCredit = totalCredit.add(amount);
    }

    /** Called only by LedgerService. Never lets the balance go below zero. */
    public void applyDebit(BigDecimal amount) {
        if (availableBalance.compareTo(amount) < 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE,
                    "Available balance is " + availableBalance.toPlainString() + ", which is less than " + amount.toPlainString() + ".");
        }
        availableBalance = availableBalance.subtract(amount);
        totalDebit = totalDebit.add(amount);
    }
}
