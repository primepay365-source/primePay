package com.primepay.auth;

import com.primepay.user.AccountResponse;
import java.time.Instant;

public record LoginResponse(String accessToken, String tokenType, Instant expiresAt, AccountResponse account) {
}
