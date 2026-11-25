package com.primepay.api.auth;

import com.primepay.api.account.AccountResponse;
import java.time.Instant;

public record LoginResponse(String accessToken, String tokenType, Instant expiresAt, AccountResponse account) {
}
