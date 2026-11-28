package com.primepay.auth;

import java.time.OffsetDateTime;

/**
 * Result of step 1: the password was right and a code was sent. {@code challengeId} is an opaque reference for
 * step 2 (it is not a session and grants nothing on its own).
 */
public record LoginChallengeResponse(String challengeId, String channel, String destination, OffsetDateTime expiresAt,
                                     int codeLength, int resendAfterSeconds, int resendsLeft) {
}
