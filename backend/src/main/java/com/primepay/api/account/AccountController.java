package com.primepay.api.account;

import java.util.List;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {
    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public List<AccountResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return accountService.listVisibleAccounts(jwt);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateAccountRequest request) {
        return accountService.createAccount(jwt, request);
    }

    @PutMapping("/{accountId}")
    public AccountResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long accountId,
            @Valid @RequestBody UpdateAccountRequest request) {
        return accountService.updateAccount(jwt, accountId, request);
    }
}
