package com.primepay.api.onboarding;

import com.primepay.api.account.AccountRole;
import com.primepay.api.account.AccountUser;
import com.primepay.api.account.AccountUserRepository;
import com.primepay.api.account.AgentAccount;
import com.primepay.api.account.AgentAccountRepository;
import java.io.IOException;
import java.security.SecureRandom;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DistributorOnboardingService {
    public static final String AGREEMENT_VERSION = "MASTER-DISTRIBUTOR-V1";
    private static final long MAX_PHOTO_BYTES = 5L * 1024 * 1024;

    private final AgentAccountRepository accounts;
    private final AccountUserRepository users;
    private final DistributorOnboardingRepository onboardings;
    private final OtpService otpService;
    private final PasswordEncoder passwordEncoder;

    public DistributorOnboardingService(
            AgentAccountRepository accounts,
            AccountUserRepository users,
            DistributorOnboardingRepository onboardings,
            OtpService otpService,
            PasswordEncoder passwordEncoder) {
        this.accounts = accounts;
        this.users = users;
        this.onboardings = onboardings;
        this.otpService = otpService;
        this.passwordEncoder = passwordEncoder;
    }

    public OtpResponse requestOtp(Jwt jwt, OtpRequest request) {
        AgentAccount actor = requireMasterDistributor(jwt);
        return otpService.request(actor.getId(), request.mobile());
    }

    public void verifyOtp(Jwt jwt, OtpVerificationRequest request) {
        AgentAccount actor = requireMasterDistributor(jwt);
        otpService.verify(actor.getId(), request.mobile(), request.code());
    }

    @Transactional
    public OnboardingResponse submit(Jwt jwt, OnboardDistributorRequest request, MultipartFile photo) {
        AgentAccount actor = requireMasterDistributor(jwt);
        validateSubmission(request, photo);
        otpService.requireVerified(actor.getId(), request.mobile());

        String accountCode = request.accountCode().trim();
        if (accounts.existsByAccountCode(accountCode)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Distributor account code already exists");
        }
        String username = accountCode.toLowerCase(Locale.ROOT);
        if (users.existsByUsername(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Distributor login username already exists");
        }

        byte[] image;
        try {
            image = photo.getBytes();
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read the uploaded photo", exception);
        }

        AgentAccount distributor = accounts.save(new AgentAccount(
                accountCode,
                request.fullName().trim(),
                AccountRole.DISTRIBUTOR,
                actor));
        String initialPassword = generateInitialPassword();
        users.save(new AccountUser(username, passwordEncoder.encode(initialPassword), distributor));
        DistributorOnboarding onboarding = onboardings.save(new DistributorOnboarding(
                distributor,
                actor,
                request,
                photo.getContentType().toLowerCase(Locale.ROOT),
                image,
                OffsetDateTime.now()));
        return OnboardingResponse.from(onboarding, initialPassword);
    }

    private AgentAccount requireMasterDistributor(Jwt jwt) {
        Object claim = jwt.getClaim("account_id");
        if (claim == null) {
            throw new AccessDeniedException("Token is missing the account_id claim");
        }

        final Long id;
        try {
            id = Long.valueOf(claim.toString());
        } catch (NumberFormatException exception) {
            throw new AccessDeniedException("Token account_id claim must be numeric");
        }

        AgentAccount actor = accounts.findById(id)
                .orElseThrow(() -> new AccessDeniedException("No account is associated with this token"));
        if (!actor.isActive() || actor.getRole() != AccountRole.MASTER_DISTRIBUTOR) {
            throw new AccessDeniedException("Only an active Master Distributor may onboard Distributors");
        }
        return actor;
    }

    private void validateSubmission(OnboardDistributorRequest request, MultipartFile photo) {
        if (request.velocityDaily().compareTo(request.velocityPerTransaction()) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Daily velocity limit must be at least the per-transaction limit");
        }
        if (!AGREEMENT_VERSION.equals(request.agreementVersion())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Agreement version is not current");
        }
        if (photo == null || photo.isEmpty() || photo.getSize() > MAX_PHOTO_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A photo of 5 MB or less is required");
        }
        String contentType = photo.getContentType();
        if (!StringUtils.hasText(contentType)
                || !(contentType.equalsIgnoreCase("image/jpeg")
                || contentType.equalsIgnoreCase("image/png")
                || contentType.equalsIgnoreCase("image/webp"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Photo must be JPEG, PNG, or WebP");
        }
    }

    private String generateInitialPassword() {
        byte[] random = new byte[24];
        new SecureRandom().nextBytes(random);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }
}
