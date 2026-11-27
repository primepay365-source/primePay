package com.primepay.auth;

import com.primepay.audit.AuditService;
import com.primepay.common.error.BusinessException;
import com.primepay.common.error.ErrorCode;
import com.primepay.notification.NotificationService;
import com.primepay.user.AccountResponse;
import com.primepay.user.AccountUser;
import com.primepay.user.AccountUserRepository;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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

@Service
public class AuthService {
    private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(30);
    private static final Duration LOCK_DURATION = Duration.ofMinutes(30);
    private static final Duration OTP_LIFETIME = Duration.ofMinutes(5);
    private static final Duration OTP_RESEND_INTERVAL = Duration.ofSeconds(60);
    private static final int OTP_MAX_ATTEMPTS = 5;
    private static final String RESET_PURPOSE = "PASSWORD_RESET";
    private static final Pattern POLICY = Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{10,200}$");
    /** Used to spend BCrypt time on unknown usernames so response time doesn't reveal which usernames exist. */

    private final AccountUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final OtpVerificationRepository otps;
    private final AuditService audit;
    private final NotificationService notifications;
    private final boolean otpDevMode;
    private final SecureRandom random = new SecureRandom();
    private final String dummyHash;

    public AuthService(AccountUserRepository users, PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder, OtpVerificationRepository otps,
                       AuditService audit, NotificationService notifications,
                       @Value("${app.onboarding.otp.dev-mode:false}") boolean otpDevMode) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.otps = otps;
        this.audit = audit;
        this.notifications = notifications;
        this.otpDevMode = otpDevMode;
        this.dummyHash = passwordEncoder.encode("timing-equaliser-" + random.nextLong());
    }

    /**
     * Wrong passwords count towards a 5-attempt lockout. Unknown username and wrong password give the same
     * message. A correct password on an inactive account says so (safe: the caller already knows the password).
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public LoginResponse login(LoginRequest request) {
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        OffsetDateTime now = OffsetDateTime.now();
        Optional<AccountUser> found = users.findByUsername(username);
        if (found.isEmpty()) {
            passwordEncoder.matches(request.password(), dummyHash);
            audit.recordIndependent(null, username, "LOGIN", "USER", username, AuditService.FAILED, "unknown username");
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        AccountUser user = found.get();
        if (user.isLocked(now)) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED, "Too many incorrect passwords. Try again after "
                    + user.getLockedUntil().atZoneSameInstant(ZoneOffset.ofHoursMinutes(5, 30)).format(DateTimeFormatter.ofPattern("h:mm a"))
                    + " IST, or reset your password.");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            boolean locked = user.registerFailedLogin(now, LOCK_DURATION);
            audit.recordIndependent(user.getAccount().getId(), username, "LOGIN", "USER", user.getAccount().getAccountCode(),
                    AuditService.FAILED, locked ? "wrong password; account locked" : "wrong password");
            if (locked) {
                notifications.notify(user.getAccount().getId(), NotificationService.SECURITY, "Account locked",
                        "Your account was locked for 30 minutes after 5 incorrect passwords. Reset your password if this wasn't you.", null);
                throw new BusinessException(ErrorCode.ACCOUNT_LOCKED, "Too many incorrect passwords. Your account is locked for 30 minutes, or reset your password now.");
            }
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "Invalid username or password. "
                    + user.remainingAttempts() + " attempts left before the account is locked.");
        }
        if (!user.isActive() || !user.getAccount().isActive()) {
            String status = user.getAccount().getStatus().name().toLowerCase(Locale.ROOT).replace('_', ' ');
            throw new BusinessException(ErrorCode.ACCOUNT_INACTIVE, "This account is " + status
                    + ". Contact your parent distributor or PrimePay support.");
        }
        user.registerSuccessfulLogin(now);
        audit.recordIndependent(user.getAccount().getId(), user.getAccount().getAccountCode(), "LOGIN", "USER",
                user.getAccount().getAccountCode(), AuditService.SUCCESS, null);

        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(TOKEN_LIFETIME);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("primepay-api")
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(user.getUsername())
                .claim("account_id", user.getAccount().getId())
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new LoginResponse(token, "Bearer", expiresAt, AccountResponse.from(user.getAccount()));
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

    public record ForgotResult(String message, String devOtp, String maskedDestination) {
    }

    /** Always answers the same way whether or not the username exists. */
    @Transactional
    public ForgotResult forgotPassword(String rawUsername) {
        String username = rawUsername.trim().toLowerCase(Locale.ROOT);
        String generic = "If this account exists, a 6-digit code was sent to its registered mobile and email. It expires in 5 minutes.";
        Optional<AccountUser> found = users.findByUsername(username);
        if (found.isEmpty()) {
            return new ForgotResult(generic, null, null);
        }
        AccountUser user = found.get();
        Long accountId = user.getAccount().getId();
        Optional<OtpVerification> last = otps.findFirstByAccountIdAndPurposeOrderByIdDesc(accountId, RESET_PURPOSE);
        if (last.isPresent() && last.get().getCreatedAt().plus(OTP_RESEND_INTERVAL).isAfter(OffsetDateTime.now())) {
            throw new BusinessException(ErrorCode.RATE_LIMITED, "A code was sent less than a minute ago. Wait a moment before asking again.");
        }
        String code = String.format("%06d", random.nextInt(1_000_000));
        otps.save(new OtpVerification(accountId, RESET_PURPOSE, passwordEncoder.encode(code), OffsetDateTime.now().plus(OTP_LIFETIME)));
        audit.record(user.getAccount(), "PASSWORD_RESET_REQUEST", "USER", user.getAccount().getAccountCode(), null);
        // Development has no SMS/email provider: the mock returns the code so the flow can be tested on screen.
        return new ForgotResult(generic, otpDevMode ? code : null, mask(user.getAccount().getMobile(), user.getAccount().getEmail()));
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public void resetPassword(String rawUsername, String code, String newPassword) {
        String username = rawUsername.trim().toLowerCase(Locale.ROOT);
        AccountUser user = users.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_FAILED, "The code is incorrect or has expired."));
        OtpVerification otp = otps.findFirstByAccountIdAndPurposeOrderByIdDesc(user.getAccount().getId(), RESET_PURPOSE)
                .filter(o -> o.usable(OffsetDateTime.now(), OTP_MAX_ATTEMPTS))
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_FAILED, "The code is incorrect or has expired. Ask for a new one."));
        if (!passwordEncoder.matches(code, otp.getCodeHash())) {
            otp.failedAttempt();
            int left = OTP_MAX_ATTEMPTS - otp.getAttempts();
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

    private void requirePolicy(String password, AccountUser user) {
        if (password == null || !POLICY.matcher(password).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Use at least 10 characters with upper and lower case letters, a number and a symbol.");
        }
        if (passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Choose a password you haven't used before.");
        }
    }

    private static String mask(String mobile, String email) {
        StringBuilder out = new StringBuilder();
        if (mobile != null && mobile.length() >= 4) out.append("mobile ending ").append(mobile.substring(mobile.length() - 4));
        if (email != null && email.contains("@")) {
            if (!out.isEmpty()) out.append(" and ");
            out.append(email.charAt(0)).append("•••").append(email.substring(email.indexOf('@')));
        }
        return out.isEmpty() ? "your registered contact" : out.toString();
    }
}
