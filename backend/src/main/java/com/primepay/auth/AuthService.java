package com.primepay.auth;

import com.primepay.audit.AuditService;
import com.primepay.auth.otp.OtpChannel;
import com.primepay.auth.otp.OtpDeliveryService;
import com.primepay.common.ContactNormalizer;
import com.primepay.common.error.BusinessException;
import com.primepay.common.error.ErrorCode;
import com.primepay.notification.NotificationService;
import com.primepay.user.AccountResponse;
import com.primepay.user.AccountUser;
import com.primepay.user.AccountUserRepository;
import com.primepay.user.AgentAccount;
import com.primepay.user.AgentAccountRepository;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sign-in is two steps: (1) email or mobile number + password, then (2) a one-time code sent to that same email
 * (SMTP) or mobile (MSG91). A session token is issued only after the code is verified. Codes are stored only as
 * BCrypt hashes, expire, allow limited attempts and resends, and the second step is bound to an opaque challenge.
 */
@Service
public class AuthService {
    static final String LOGIN = "LOGIN";
    private static final String RESET_PURPOSE = "PASSWORD_RESET";
    private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(30);
    private static final Duration LOCK_DURATION = Duration.ofMinutes(30);
    private static final Duration CHALLENGE_WINDOW = Duration.ofMinutes(15);
    private static final Pattern POLICY = Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{10,200}$");

    private final AccountUserRepository users;
    private final AgentAccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final OtpVerificationRepository otps;
    private final OtpDeliveryService delivery;
    private final AuditService audit;
    private final NotificationService notifications;
    private final int codeLength;
    private final Duration codeTtl;
    private final int maxAttempts;
    private final Duration resendInterval;
    private final int maxResends;
    private final int maxChallengesPerWindow;
    private final SecureRandom random = new SecureRandom();
    /** Spends BCrypt time on unknown identifiers so the response time doesn't reveal which accounts exist. */
    private final String dummyHash;

    public AuthService(AccountUserRepository users, AgentAccountRepository accounts, PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder,
                       OtpVerificationRepository otps, OtpDeliveryService delivery, AuditService audit, NotificationService notifications,
                       @Value("${app.otp.length:6}") int codeLength,
                       @Value("${app.otp.ttl:PT5M}") Duration codeTtl,
                       @Value("${app.otp.max-attempts:5}") int maxAttempts,
                       @Value("${app.otp.resend-interval:PT60S}") Duration resendInterval,
                       @Value("${app.otp.max-resends:3}") int maxResends,
                       @Value("${app.otp.max-sign-ins-per-15-min:5}") int maxChallengesPerWindow) {
        this.users = users;
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.otps = otps;
        this.delivery = delivery;
        this.audit = audit;
        this.notifications = notifications;
        this.codeLength = codeLength;
        this.codeTtl = codeTtl;
        this.maxAttempts = maxAttempts;
        this.resendInterval = resendInterval;
        this.maxResends = maxResends;
        this.maxChallengesPerWindow = maxChallengesPerWindow;
        this.dummyHash = passwordEncoder.encode("timing-equaliser-" + random.nextLong());
    }

    // ------------------------------------------------------------------ sign-in step 1

    /**
     * Checks the password and sends a code to the identifier the user typed. Wrong passwords count towards a
     * 5-attempt lockout; unknown identifiers and wrong passwords get the same message.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public LoginChallengeResponse login(LoginRequest request, String ipAddress) {
        OffsetDateTime now = OffsetDateTime.now();
        Contact contact = contactOf(request.identifier());
        Optional<AccountUser> found = contact == null ? Optional.empty() : findUser(contact);
        String label = contact == null ? request.identifier().trim() : contact.value();
        if (found.isEmpty()) {
            passwordEncoder.matches(request.password(), dummyHash);
            audit.recordIndependent(null, label, "LOGIN", "USER", label, AuditService.FAILED, "unknown email or mobile");
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "Invalid email/mobile or password.");
        }
        AccountUser user = found.get();
        AgentAccount account = user.getAccount();
        requireNotLocked(user, now);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            boolean locked = user.registerFailedLogin(now, LOCK_DURATION);
            audit.recordIndependent(account.getId(), label, "LOGIN", "USER", account.getAccountCode(),
                    AuditService.FAILED, locked ? "wrong password; account locked" : "wrong password");
            if (locked) {
                notifications.notify(account.getId(), NotificationService.SECURITY, "Account locked",
                        "Your account was locked for 30 minutes after 5 incorrect passwords. Reset your password if this wasn't you.", null);
                throw new BusinessException(ErrorCode.ACCOUNT_LOCKED, "Too many incorrect passwords. Your account is locked for 30 minutes, or reset your password now.");
            }
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "Invalid email/mobile or password. "
                    + user.remainingAttempts() + " attempts left before the account is locked.");
        }
        requireActive(user);
        if (otps.countByAccountIdAndPurposeAndCreatedAtAfter(account.getId(), LOGIN, now.minus(CHALLENGE_WINDOW)) >= maxChallengesPerWindow) {
            throw new BusinessException(ErrorCode.RATE_LIMITED, "Too many sign-in codes were requested. Wait 15 minutes and try again.");
        }

        String code = newCode();
        String masked = OtpDeliveryService.mask(contact.channel(), contact.value());
        OtpVerification challenge = otps.save(OtpVerification.loginChallenge(account.getId(), newChallengeRef(), contact.channel().name(),
                masked, passwordEncoder.encode(code), now.plus(codeTtl), ipAddress));
        delivery.deliver(contact.channel(), contact.value(), code, "sign-in", codeTtl);
        audit.recordIndependent(account.getId(), account.getAccountCode(), "LOGIN_OTP_SENT", "USER", account.getAccountCode(),
                AuditService.SUCCESS, "channel=" + contact.channel() + " to " + masked);
        return challengeView(challenge);
    }

    // ------------------------------------------------------------------ sign-in step 2

    /** Verifies the code for a challenge and only then issues the session token. */
    @Transactional(noRollbackFor = BusinessException.class)
    public LoginResponse verifyLogin(String challengeId, String code) {
        OffsetDateTime now = OffsetDateTime.now();
        OtpVerification challenge = otps.findByChallengeRefAndPurpose(challengeId, LOGIN)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_FAILED, "This sign-in has expired. Enter your email or mobile and password again."));
        if (!challenge.usable(now, maxAttempts)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "This code has expired or was tried too many times. Sign in again to get a new code.");
        }
        AccountUser user = users.findByAccount_Id(challenge.getAccountId())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
        AgentAccount account = user.getAccount();
        if (!passwordEncoder.matches(code, challenge.getCodeHash())) {
            challenge.failedAttempt();
            int left = maxAttempts - challenge.getAttempts();
            audit.recordIndependent(account.getId(), account.getAccountCode(), "LOGIN_OTP", "USER", account.getAccountCode(),
                    AuditService.FAILED, "wrong code; " + left + " attempts left");
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, left > 0
                    ? "The code is incorrect. " + left + " attempt" + (left == 1 ? "" : "s") + " left."
                    : "Too many wrong codes. Sign in again to get a new code.");
        }
        requireNotLocked(user, now);
        requireActive(user);
        challenge.consume();
        user.registerSuccessfulLogin(now);
        audit.recordIndependent(account.getId(), account.getAccountCode(), "LOGIN", "USER", account.getAccountCode(),
                AuditService.SUCCESS, "verified by " + challenge.getChannel() + " code");
        return issueToken(user);
    }

    /** A new code for the same challenge, at most every {@code resend-interval} and {@code max-resends} times. */
    @Transactional(noRollbackFor = BusinessException.class)
    public LoginChallengeResponse resendLogin(String challengeId) {
        OffsetDateTime now = OffsetDateTime.now();
        OtpVerification challenge = otps.findByChallengeRefAndPurpose(challengeId, LOGIN)
                .filter(c -> c.getConsumedAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_FAILED, "This sign-in has expired. Enter your email or mobile and password again."));
        long wait = Duration.between(now, challenge.getCreatedAt().plus(resendInterval)).toSeconds();
        if (wait > 0) {
            throw new BusinessException(ErrorCode.RATE_LIMITED, "You can ask for a new code in " + wait + " seconds.");
        }
        if (challenge.getResendCount() >= maxResends) {
            throw new BusinessException(ErrorCode.RATE_LIMITED, "No more codes for this sign-in. Enter your email or mobile and password again.");
        }
        AccountUser user = users.findByAccount_Id(challenge.getAccountId()).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
        requireActive(user);
        OtpChannel channel = OtpChannel.valueOf(challenge.getChannel());
        String destination = channel == OtpChannel.EMAIL ? user.getAccount().getEmail() : user.getAccount().getMobile();
        String code = newCode();
        challenge.reissue(passwordEncoder.encode(code), now.plus(codeTtl));
        delivery.deliver(channel, destination, code, "sign-in", codeTtl);
        audit.recordIndependent(user.getAccount().getId(), user.getAccount().getAccountCode(), "LOGIN_OTP_SENT", "USER",
                user.getAccount().getAccountCode(), AuditService.SUCCESS, "resent by " + channel + " to " + challenge.getDestinationMasked());
        return challengeView(challenge);
    }

    @Transactional(readOnly = true)
    public MeResponse me(Jwt jwt) {
        Object claim = jwt.getClaim("account_id");
        if (claim == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        final long accountId;
        try {
            accountId = Long.parseLong(claim.toString());
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        AccountUser user = users.findByAccount_Id(accountId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
        if (!user.isActive() || !user.getAccount().isActive()) {
            throw new AccessDeniedException("Account is inactive");
        }
        return MeResponse.from(user);
    }

    @Transactional
    public void changePassword(Jwt jwt, String currentPassword, String newPassword) {
        AccountUser user = users.findByUsername(jwt.getSubject()).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Your current password is incorrect.");
        }
        requirePolicy(newPassword, user);
        user.updatePassword(passwordEncoder.encode(newPassword));
        audit.record(user.getAccount(), "PASSWORD_CHANGE", "USER", user.getAccount().getAccountCode(), null);
        notifications.notify(user.getAccount().getId(), NotificationService.SECURITY, "Password changed",
                "Your PrimePay password was changed. If this wasn't you, contact support now.", null);
    }

    // ------------------------------------------------------------------ password reset

    public record ForgotResult(String message, String devOtp, String maskedDestination) {
    }

    /** Sends a reset code to the email or mobile typed. Always answers the same way whether or not the account exists. */
    @Transactional
    public ForgotResult forgotPassword(String identifier) {
        String generic = "If an account uses this email or mobile, a code was sent to it. It expires in " + codeTtl.toMinutes() + " minutes.";
        Contact contact = contactOf(identifier);
        Optional<AccountUser> found = contact == null ? Optional.empty() : findUser(contact);
        if (found.isEmpty()) {
            return new ForgotResult(generic, null, null);
        }
        AccountUser user = found.get();
        Long accountId = user.getAccount().getId();
        Optional<OtpVerification> last = otps.findFirstByAccountIdAndPurposeOrderByIdDesc(accountId, RESET_PURPOSE);
        if (last.isPresent() && last.get().getCreatedAt().plus(resendInterval).isAfter(OffsetDateTime.now())) {
            throw new BusinessException(ErrorCode.RATE_LIMITED, "A code was sent less than a minute ago. Wait a moment before asking again.");
        }
        String code = newCode();
        otps.save(new OtpVerification(accountId, RESET_PURPOSE, passwordEncoder.encode(code), OffsetDateTime.now().plus(codeTtl)));
        delivery.deliver(contact.channel(), contact.value(), code, "password reset", codeTtl);
        audit.record(user.getAccount(), "PASSWORD_RESET_REQUEST", "USER", user.getAccount().getAccountCode(), "channel=" + contact.channel());
        // The code only ever travels by email or SMS (or the dev log); it is never returned to the browser.
        return new ForgotResult(generic, null, OtpDeliveryService.mask(contact.channel(), contact.value()));
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public void resetPassword(String identifier, String code, String newPassword) {
        Contact contact = contactOf(identifier);
        AccountUser user = (contact == null ? Optional.<AccountUser>empty() : findUser(contact))
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_FAILED, "The code is incorrect or has expired."));
        OtpVerification otp = otps.findFirstByAccountIdAndPurposeOrderByIdDesc(user.getAccount().getId(), RESET_PURPOSE)
                .filter(o -> o.usable(OffsetDateTime.now(), maxAttempts))
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_FAILED, "The code is incorrect or has expired. Ask for a new one."));
        if (!passwordEncoder.matches(code, otp.getCodeHash())) {
            otp.failedAttempt();
            int left = maxAttempts - otp.getAttempts();
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, left > 0 ? "The code is incorrect. " + left + " attempts left."
                    : "Too many wrong codes. Ask for a new one.");
        }
        requirePolicy(newPassword, user);
        otp.consume();
        user.updatePassword(passwordEncoder.encode(newPassword));
        audit.record(user.getAccount(), "PASSWORD_RESET", "USER", user.getAccount().getAccountCode(), null);
        notifications.notify(user.getAccount().getId(), NotificationService.SECURITY, "Password reset",
                "Your password was reset with a one-time code. If this wasn't you, contact support now.", null);
    }

    // ------------------------------------------------------------------ helpers

    /** The channel follows what the user typed: an email address gets an email, a mobile number an SMS. */
    private record Contact(OtpChannel channel, String value) {
    }

    private static Contact contactOf(String identifier) {
        if (identifier == null || identifier.isBlank()) return null;
        try {
            return ContactNormalizer.looksLikeEmail(identifier)
                    ? new Contact(OtpChannel.EMAIL, ContactNormalizer.email(identifier))
                    : new Contact(OtpChannel.SMS, ContactNormalizer.mobile(identifier));
        } catch (BusinessException e) {
            return null; // not a valid email or mobile: treated like an unknown account
        }
    }

    private Optional<AccountUser> findUser(Contact contact) {
        Optional<AgentAccount> account = contact.channel() == OtpChannel.EMAIL ? accounts.findByEmail(contact.value()) : accounts.findByMobile(contact.value());
        return account.flatMap(a -> users.findByAccount_Id(a.getId()));
    }

    private void requireNotLocked(AccountUser user, OffsetDateTime now) {
        if (user.isLocked(now)) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED, "Too many incorrect passwords. Try again after "
                    + user.getLockedUntil().atZoneSameInstant(ZoneOffset.ofHoursMinutes(5, 30)).format(DateTimeFormatter.ofPattern("h:mm a"))
                    + " IST, or reset your password.");
        }
    }

    private static void requireActive(AccountUser user) {
        if (!user.isActive() || !user.getAccount().isActive()) {
            String status = user.getAccount().getStatus().name().toLowerCase(Locale.ROOT).replace('_', ' ');
            throw new BusinessException(ErrorCode.ACCOUNT_INACTIVE, "This account is " + status
                    + ". Contact your parent distributor or PrimePay support.");
        }
    }

    private LoginResponse issueToken(AccountUser user) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(TOKEN_LIFETIME);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("primepay-api")
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(user.getUsername())
                .claim("account_id", user.getAccount().getId())
                .claim("amr", java.util.List.of("pwd", "otp"))
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new LoginResponse(token, "Bearer", expiresAt, AccountResponse.from(user.getAccount()));
    }

    private LoginChallengeResponse challengeView(OtpVerification c) {
        int wait = (int) Math.max(0, Duration.between(OffsetDateTime.now(), c.getCreatedAt().plus(resendInterval)).toSeconds());
        return new LoginChallengeResponse(c.getChallengeRef(), c.getChannel(), c.getDestinationMasked(), c.getExpiresAt(),
                codeLength, wait, Math.max(0, maxResends - c.getResendCount()));
    }

    private String newCode() {
        int bound = (int) Math.pow(10, codeLength);
        return String.format("%0" + codeLength + "d", random.nextInt(bound));
    }

    private String newChallengeRef() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void requirePolicy(String password, AccountUser user) {
        if (password == null || !POLICY.matcher(password).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Use at least 10 characters with upper and lower case letters, a number and a symbol.");
        }
        if (passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Choose a password you haven't used before.");
        }
    }
}
