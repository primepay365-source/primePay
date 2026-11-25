package com.primepay.api.auth;

import com.primepay.api.account.AccountRole;
import com.primepay.api.account.AccountUser;
import com.primepay.api.account.AccountUserRepository;
import com.primepay.api.account.AgentAccount;
import com.primepay.api.account.AgentAccountRepository;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
public class DevAdminBootstrap implements ApplicationRunner {
    private static final Logger logger = LoggerFactory.getLogger(DevAdminBootstrap.class);
    private static final String ACCOUNT_CODE = "LOCAL-ADMIN-001";

    private final AgentAccountRepository accounts;
    private final AccountUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;

    public DevAdminBootstrap(
            AgentAccountRepository accounts,
            AccountUserRepository users,
            PasswordEncoder passwordEncoder,
            @Value("${app.dev.bootstrap.username:admin}") String username,
            @Value("${app.dev.bootstrap.password:Admin@123456}") String password) {
        this.accounts = accounts;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.username = username.trim().toLowerCase();
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AgentAccount admin = accounts.findByAccountCode(ACCOUNT_CODE)
                .orElseGet(() -> accounts.save(new AgentAccount(
                        ACCOUNT_CODE, "Development Admin", AccountRole.ADMIN, null)));

        AccountUser user = users.findByUsername(username).orElse(null);
        if (user == null) {
            users.save(new AccountUser(username, passwordEncoder.encode(password), admin));
        } else {
            user.updatePassword(passwordEncoder.encode(password));
        }

        logger.info("Development login ready: username='{}'", username);
    }
}
