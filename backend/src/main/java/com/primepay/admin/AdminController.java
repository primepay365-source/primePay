package com.primepay.admin;

import com.primepay.audit.AuditService;
import com.primepay.common.api.ApiResponse;
import com.primepay.common.error.BusinessException;
import com.primepay.common.error.ErrorCode;
import com.primepay.security.CurrentActor;
import com.primepay.user.AccountRole;
import com.primepay.user.AgentAccount;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin configuration")
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
    private final CurrentActor currentActor;
    private final ServiceDefinitionRepository services;
    private final AuditService audit;
    private final String timezone;
    private final boolean otpDevMode;

    public AdminController(CurrentActor currentActor, ServiceDefinitionRepository services, AuditService audit,
                           @Value("${app.business-timezone:Asia/Kolkata}") String timezone,
                           @Value("${app.onboarding.otp.dev-mode:false}") boolean otpDevMode) {
        this.currentActor = currentActor;
        this.services = services;
        this.audit = audit;
        this.timezone = timezone;
        this.otpDevMode = otpDevMode;
    }

    /** "Y" granted, "S" within own downstream scope, "O" own records only, "-" never. Enforced in code, shown here read-only. */
    public record PermissionRow(String code, String description, Map<String, String> byRole) {
    }

    private static PermissionRow p(String code, String description, String admin, String sup, String master, String dist, String retail) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("ADMIN", admin);
        m.put("SUPER_DISTRIBUTOR", sup);
        m.put("MASTER_DISTRIBUTOR", master);
        m.put("DISTRIBUTOR", dist);
        m.put("RETAILER", retail);
        return new PermissionRow(code, description, m);
    }

    @GetMapping("/permissions")
    public ApiResponse<List<PermissionRow>> permissions() {
        currentActor.requireRole(AccountRole.ADMIN);
        return ApiResponse.ok(List.of(
                p("USER_CREATE", "Create the account level directly below", "Y", "S", "S", "S", "-"),
                p("USER_VIEW", "View accounts", "Y", "S", "S", "S", "O"),
                p("USER_STATUS_CHANGE", "Activate, suspend or deactivate accounts below", "Y", "S", "S", "S", "-"),
                p("ONBOARD_SUBMIT", "Submit the 5-step distributor onboarding", "-", "-", "S", "-", "-"),
                p("ONBOARD_APPROVE", "Approve onboarding applications", "Y", "-", "-", "-", "-"),
                p("HIERARCHY_VIEW", "View the network tree", "Y", "S", "S", "S", "-"),
                p("WALLET_VIEW", "View wallet and statement", "-", "O", "O", "O", "O"),
                p("WALLET_LOAD_REQUEST", "Request a wallet load", "-", "O", "O", "O", "-"),
                p("WALLET_LOAD_APPROVE", "Approve wallet loads", "Y", "-", "-", "-", "-"),
                p("WALLET_TRANSFER", "Transfer to direct children", "-", "S", "S", "S", "-"),
                p("WALLET_ADJUST", "Request and approve adjustments (maker ≠ checker)", "Y", "-", "-", "-", "-"),
                p("TXN_CREATE", "Make service transactions (payout)", "-", "-", "-", "-", "O"),
                p("TXN_VIEW", "View transactions", "Y", "S", "S", "S", "O"),
                p("TXN_REVERSE", "Reverse transactions", "Y", "-", "-", "-", "-"),
                p("COMMISSION_CONFIG", "Configure commission rules", "Y", "-", "-", "-", "-"),
                p("COMMISSION_VIEW", "View commission statement", "-", "O", "O", "O", "O"),
                p("LIMITS_CONFIG", "Set velocity limits and user overrides", "Y", "-", "-", "-", "-"),
                p("REPORT_VIEW", "View and export reports", "Y", "S", "S", "S", "O"),
                p("ADMIN_CONFIG", "Roles, services and settings", "Y", "-", "-", "-", "-"),
                p("AUDIT_VIEW", "View audit log and reconciliation", "Y", "-", "-", "-", "-")));
    }

    public record ServiceView(Long id, String serviceCode, String name, BigDecimal feeAmount, boolean enabled, String provider) {
    }

    @GetMapping("/services")
    public ApiResponse<List<ServiceView>> services() {
        currentActor.requireRole(AccountRole.ADMIN);
        return ApiResponse.ok(services.findAll().stream().map(AdminController::view).toList());
    }

    public record ServiceBody(@NotNull @DecimalMin("0") BigDecimal feeAmount, boolean enabled) {
    }

    @PutMapping("/services/{id}")
    @Transactional
    public ApiResponse<ServiceView> updateService(@PathVariable Long id, @Valid @RequestBody ServiceBody body) {
        AgentAccount admin = currentActor.requireRole(AccountRole.ADMIN);
        ServiceDefinition service = services.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Service not found."));
        service.setFeeAmount(body.feeAmount().setScale(2, java.math.RoundingMode.HALF_UP));
        service.setEnabled(body.enabled());
        audit.record(admin, "SERVICE_UPDATE", "SERVICE", service.getServiceCode(), "fee=" + body.feeAmount() + " enabled=" + body.enabled());
        return ApiResponse.ok(view(service), "Service saved");
    }

    public record Setting(String key, String value, String note) {
    }

    @GetMapping("/settings")
    public ApiResponse<List<Setting>> settings() {
        currentActor.requireRole(AccountRole.ADMIN);
        return ApiResponse.ok(List.of(
                new Setting("Business time zone", timezone, "Daily and monthly limits reset at midnight in this zone."),
                new Setting("Access token lifetime", "30 minutes", "Signed HS256 JWT."),
                new Setting("Account lockout", "5 failed sign-ins → locked 30 minutes", "A password reset unlocks immediately."),
                new Setting("OTP", "6 digits · 5 minutes · 5 attempts", otpDevMode ? "Development mock: the code is shown on screen." : "Sent by SMS/email provider."),
                new Setting("Password policy", "10+ characters, upper and lower case, number, symbol", "Checked on change and reset."),
                new Setting("Reversal window", "7 days", "After that, use a wallet adjustment."),
                new Setting("Currency", "INR · 2 decimals · half-up rounding", "Money is never stored as floating point."),
                new Setting("Payout provider", "Mock (simulated)", "No real money moves until an approved provider is connected.")));
    }

    private static ServiceView view(ServiceDefinition s) {
        return new ServiceView(s.getId(), s.getServiceCode(), s.getName(), s.getFeeAmount(), s.isEnabled(), s.getProvider());
    }
}
