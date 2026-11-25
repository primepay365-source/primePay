package com.primepay.api.auth;

import com.primepay.api.account.AccountResponse;
import com.primepay.api.account.AccountUser;
import com.primepay.api.account.AccountUserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
    private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(30);

    private final AccountUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;

    public AuthService(AccountUserRepository users, PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        AccountUser user = users.findByUsername(username)
                .filter(AccountUser::isActive)
                .filter(candidate -> candidate.getAccount().isActive())
                .filter(candidate -> passwordEncoder.matches(request.password(), candidate.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password"));

        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(TOKEN_LIFETIME);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("primepay-api")
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(user.getUsername())
                .claim("account_id", user.getAccount().getId())
                .build();

        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

        return new LoginResponse(token, "Bearer", expiresAt, AccountResponse.from(user.getAccount()));
    }
}
