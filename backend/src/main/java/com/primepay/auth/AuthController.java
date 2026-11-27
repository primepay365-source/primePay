package com.primepay.auth;

import com.primepay.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

    /** Returns the token and account directly (not wrapped) so existing clients keep working. */
    @Operation(summary = "Sign in with username and password (5 wrong attempts lock the account for 30 minutes)")
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
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

    public record ForgotBody(@NotBlank @Size(max = 80) String username) {
    }

    @Operation(summary = "Send a 6-digit reset code. Same answer whether or not the user exists.")
    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<AuthService.ForgotResult> forgot(@Valid @RequestBody ForgotBody body) {
        return ApiResponse.ok(authService.forgotPassword(body.username()));
    }

    public record ResetBody(@NotBlank @Size(max = 80) String username, @NotBlank @Pattern(regexp = "[0-9]{6}") String otp,
                            @NotBlank @Size(max = 200) String newPassword) {
    }

    @PostMapping("/reset-password")
    public ApiResponse<Void> reset(@Valid @RequestBody ResetBody body) {
        authService.resetPassword(body.username(), body.otp(), body.newPassword());
        return ApiResponse.ok(null, "Password reset");
    }
}
