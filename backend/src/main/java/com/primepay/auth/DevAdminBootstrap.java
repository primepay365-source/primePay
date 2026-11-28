package com.primepay.auth;

import com.primepay.transaction.FinancialTransaction;
import com.primepay.beneficiary.BeneficiaryService;
import com.primepay.billpay.BillPaymentService;
import com.primepay.user.AccountRole;
import com.primepay.user.AccountUser;
import com.primepay.user.AccountUserRepository;
import com.primepay.user.AgentAccount;
import com.primepay.user.AgentAccountRepository;
import com.primepay.user.CreateAccountRequest;
import com.primepay.user.UserService;
import com.primepay.wallet.TransferService;
import com.primepay.addfunds.AddFundsService;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * DEVELOPMENT ONLY. Creates fictional test accounts for all five roles (plus a second admin for
 * maker-checker approvals) and a little simulated activity, by calling the same services the API uses, so
 * every screen has real data. Passwords are generated per start and printed once to the log, unless
 * DEV_ADMIN_PASSWORD / DEV_SEED_PASSWORD are set. Runs only when app.dev.bootstrap.enabled=true.
 */
@Component
@ConditionalOnProperty(name = "app.dev.bootstrap.enabled", havingValue = "true")
public class DevAdminBootstrap implements ApplicationRunner {
    private static final Logger logger = LoggerFactory.getLogger(DevAdminBootstrap.class);
    private static final String ACCOUNT_CODE = "LOCAL-ADMIN-001";
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final String SYMBOLS = "@#%&*!";

    private final AgentAccountRepository accounts;
    private final AccountUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final UserService userService;
    private final TransferService transfers;
    private final AddFundsService addFunds;
    private final BeneficiaryService beneficiaries;
    private final BillPaymentService bills;
    private final TransactionTemplate tx;
    private final String username;
    private final String configuredPassword;
    private final String seedPassword;
    private final boolean seedSampleData;

    public DevAdminBootstrap(
            AgentAccountRepository accounts,
            AccountUserRepository users,
            PasswordEncoder passwordEncoder,
            UserService userService,
            TransferService transfers,
            AddFundsService addFunds,
            BeneficiaryService beneficiaries,
            BillPaymentService bills,
            TransactionTemplate tx,
            @Value("${app.dev.bootstrap.username:admin@primepay.local}") String username,
            @Value("${app.dev.bootstrap.password:}") String configuredPassword,
            @Value("${app.dev.seed.password:}") String seedPassword,
            @Value("${app.dev.seed.sample-data:true}") boolean seedSampleData) {
        this.accounts = accounts;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.userService = userService;
        this.transfers = transfers;
        this.addFunds = addFunds;
        this.beneficiaries = beneficiaries;
        this.bills = bills;
        this.tx = tx;
        this.username = username.trim().toLowerCase(Locale.ROOT);
        this.configuredPassword = configuredPassword;
        this.seedPassword = seedPassword;
        this.seedSampleData = seedSampleData;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> printed = new ArrayList<>();
        AgentAccount admin = tx.execute(status -> ensureAdmin(ACCOUNT_CODE, "Development Admin", username, configuredPassword, printed));
        tx.executeWithoutResult(status -> ensureAdmin("LOCAL-ADMIN-002", "Second Admin (checker)", "admin2@primepay.local", seedPassword, printed));

        if (seedSampleData && accounts.findByAccountCode("PP-SD-1001").isEmpty()) {
            AgentAccount sd = create(admin, "PP-SD-1001", "Rohan Mehta", AccountRole.SUPER_DISTRIBUTOR, "super@primepay.local", "Mehta Payments", printed);
            AgentAccount md = create(sd, "PP-MD-2014", "Meera Iyer", AccountRole.MASTER_DISTRIBUTOR, "master@primepay.local", "Shree Ganesh Traders", printed);
            AgentAccount dt = create(md, "PP-DT-3307", "Arjun Rao", AccountRole.DISTRIBUTOR, "distributor@primepay.local", "Om Sai Distributors", printed);
            AgentAccount rt = create(dt, "PP-RT-48210", "Priya Nair", AccountRole.RETAILER, "retailer@primepay.local", "City Mobile Point", printed);
            create(dt, "PP-RT-48177", "Vikram Singh", AccountRole.RETAILER, "retailer2@primepay.local", "Lakshmi Kirana", printed);
            seedActivity(admin, sd, md, dt, rt);
        }

        logger.warn("""

                ==================== DEV ONLY: test logins (fictional data, simulated money) ====================
                {}
                ==================================================================================================""",
                String.join("\n", printed));
        printed.forEach(line -> logger.warn("DEV ONLY: {}", line));
    }

    private AgentAccount ensureAdmin(String code, String name, String login, String fixedPassword, List<String> printed) {
        String password = blank(fixedPassword) ? generatePassword() : fixedPassword;
        AgentAccount admin = accounts.findByAccountCode(code)
                .orElseGet(() -> accounts.save(new AgentAccount(code, name, AccountRole.ADMIN, null)));
        AccountUser user = users.findByUsername(login).orElse(null);
        if (admin.getEmail() == null && login.contains("@")) {
            userService.assignContacts(admin, login, demoMobile(code));
        }
        if (user == null) {
            users.save(new AccountUser(login, passwordEncoder.encode(password), admin));
        } else {
            user.updatePassword(passwordEncoder.encode(password));
        }
        printed.add(String.format("%-20s username='%s' password='%s'", "Platform Admin", login, blank(fixedPassword) ? password : "(from environment)"));
        return admin;
    }

    private AgentAccount create(AgentAccount parent, String code, String name, AccountRole role, String login, String business, List<String> printed) {
        String password = blank(seedPassword) ? generatePassword() : seedPassword;
        var row = userService.create(parent, new CreateAccountRequest(code, name, role, parent.getId(), login, password),
                login, demoMobile(code),
                business, "Sample address, India", false); // fictional addresses: no welcome email
        printed.add(String.format("%-20s username='%s' password='%s'", role.name(), login, blank(seedPassword) ? password : "(from environment)"));
        return accounts.findById(row.id()).orElseThrow();
    }

    /** A little real activity through the real services: a load, transfers down the chain, payouts. */
    private void seedActivity(AgentAccount admin, AgentAccount sd, AgentAccount md, AgentAccount dt, AgentAccount rt) {
        try {
            FinancialTransaction load = transfers.requestLoad(sd, new BigDecimal("1000000.00"), "NEFT", "SEEDUTR0001", key());
            transfers.decideLoad(admin, load.getId(), true, "Seed data: bank payment matched");
            transfers.requestLoad(sd, new BigDecimal("250000.00"), "RTGS", "SEEDUTR0002", key());
            // Each account funds its own wallet through the (simulated) gateway; the levels above earn their margins.
            topUp(md, new BigDecimal("200000.00"));
            topUp(dt, new BigDecimal("100000.00"));
            topUp(rt, new BigDecimal("50000.00"));
            // Saved bank accounts (verified by the simulated bank), then withdrawals to them.
            Long suresh = beneficiaries.add(rt, "123456784410", "SBIN0001234", "Suresh Kumar").beneficiary().id();
            Long anita = beneficiaries.add(rt, "998877660921", "FDRL0001234", "Anita George").beneficiary().id();
            Long joseph = beneficiaries.add(rt, "445566770000", "CNRB0001234", "Joseph Mathew").beneficiary().id();
            beneficiaries.withdraw(rt, suresh, new BigDecimal("5000.00"), "IMPS", key());
            beneficiaries.withdraw(rt, anita, new BigDecimal("12000.00"), "NEFT", key());
            beneficiaries.withdraw(rt, joseph, new BigDecimal("3200.00"), "IMPS", key()); // simulated bank declines a/c ending 0000
            Long hdfc = bills.billers("CREDIT_CARD").get(0).id();
            bills.pay(rt, bills.fetch(rt, hdfc, "4321", "9876543210").fetchRef(), new BigDecimal("2500.00"), key());
        } catch (RuntimeException e) {
            logger.warn("DEV ONLY: sample activity could not be created: {}", e.getMessage());
        }
    }

    private void topUp(AgentAccount account, BigDecimal amount) {
        String ref = addFunds.start(account, "EDU", "VISA", account.getDisplayName(), amount).sessionRef();
        addFunds.choosePaymentMode(account, ref, "UPI", null, "seed.topup@okbank");
        addFunds.complete(account, ref, null, "seed.topup@okbank");
    }

    /** A valid, unique, fictional Indian mobile per demo account (+919XXXXXXXXX), stable across restarts. */
    private static String demoMobile(String code) {
        return "+919" + String.format("%09d", Math.floorMod(code.hashCode(), 1_000_000_000));
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    static String generatePassword() {
        SecureRandom random = new SecureRandom();
        StringBuilder password = new StringBuilder();
        for (int i = 0; i < 14; i++) {
            password.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        password.insert(random.nextInt(password.length()), SYMBOLS.charAt(random.nextInt(SYMBOLS.length())));
        password.insert(random.nextInt(password.length()), (char) ('2' + random.nextInt(8)));
        return password.toString();
    }
}
