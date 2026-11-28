package com.primepay.auth;

import com.primepay.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Authentication")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Sign-in step 1: email or mobile + password. Sends a one-time code to that email (SMTP) or mobile (SMS); no session yet.")
    @PostMapping("/login")
    public ApiResponse<LoginChallengeResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        LoginChallengeResponse challenge = authService.login(request, http.getRemoteAddr());
        return ApiResponse.ok(challenge, "Code sent to " + challenge.destination());
    }

    public record VerifyLoginBody(@NotBlank @Size(max = 64) String challengeId,
                                  @NotBlank @Pattern(regexp = "[0-9]{4,8}", message = "Enter the code you received") String otp) {
    }

    /** Returns the token and account directly (not wrapped), as the previous single-step sign-in did. */
    @Operation(summary = "Sign-in step 2: verify the one-time code. Issues the session token only on success.")
    @PostMapping("/login/verify")
    public LoginResponse verifyLogin(@Valid @RequestBody VerifyLoginBody body) {
        return authService.verifyLogin(body.challengeId(), body.otp());
    }

    public record ResendBody(@NotBlank @Size(max = 64) String challengeId) {
    }

    @Operation(summary = "Send a new code for the same sign-in (rate limited)")
    @PostMapping("/login/resend")
    public ApiResponse<LoginChallengeResponse> resendLogin(@Valid @RequestBody ResendBody body) {
        LoginChallengeResponse challenge = authService.resendLogin(body.challengeId());
        return ApiResponse.ok(challenge, "New code sent to " + challenge.destination());
    }

    @Operation(summary = "The signed-in user, role and status")
    @GetMapping("/me")
    public ApiResponse<MeResponse> me(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(authService.me(jwt));
    }

    public record ChangePasswordBody(@NotBlank @Size(max = 200) String currentPassword, @NotBlank @Size(max = 200) String newPassword) {
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangePasswordBody body) {
        authService.changePassword(jwt, body.currentPassword(), body.newPassword());
        return ApiResponse.ok(null, "Password changed");
    }

    public record ForgotBody(@NotBlank @Size(max = 160) @JsonAlias("username") String identifier) {
    }

    @Operation(summary = "Send a reset code to the email or mobile typed. Same answer whether or not the account exists.")
    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<AuthService.ForgotResult> forgot(@Valid @RequestBody ForgotBody body) {
        return ApiResponse.ok(authService.forgotPassword(body.identifier()));
    }

    public record ResetBody(@NotBlank @Size(max = 160) @JsonAlias("username") String identifier, @NotBlank @Pattern(regexp = "[0-9]{4,8}") String otp,
                            @NotBlank @Size(max = 200) String newPassword) {
    }

    @PostMapping("/reset-password")
    public ApiResponse<Void> reset(@Valid @RequestBody ResetBody body) {
        authService.resetPassword(body.identifier(), body.otp(), body.newPassword());
        return ApiResponse.ok(null, "Password reset");
    }
}
