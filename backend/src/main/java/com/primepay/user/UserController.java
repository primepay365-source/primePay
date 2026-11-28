package com.primepay.user;

import com.primepay.common.api.ApiResponse;
import com.primepay.common.web.CsvWriter;
import com.primepay.hierarchy.HierarchyService;
import com.primepay.security.CurrentActor;
import com.primepay.wallet.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Users and hierarchy")
@RestController
@RequestMapping("/api/v1")
public class UserController {
    private final UserService users;
    private final HierarchyService hierarchy;
    private final WalletService wallets;
    private final CurrentActor currentActor;

    public UserController(UserService users, HierarchyService hierarchy, WalletService wallets, CurrentActor currentActor) {
        this.users = users;
        this.hierarchy = hierarchy;
        this.wallets = wallets;
        this.currentActor = currentActor;
    }

    @Operation(summary = "Accounts below you, filtered by role, status and search text")
    @GetMapping("/users")
    public ApiResponse<List<UserService.UserRow>> list(@RequestParam(required = false) AccountRole role,
                                                       @RequestParam(required = false) AccountStatus status,
                                                       @RequestParam(required = false) String q) {
        return ApiResponse.ok(users.list(currentActor.require(), role, status, q));
    }

    @GetMapping("/users/counts")
    public ApiResponse<Map<AccountRole, Long>> counts() {
        return ApiResponse.ok(users.countByRole(currentActor.require()));
    }

    @GetMapping(value = "/users/export.csv", produces = "text/csv")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) AccountRole role,
                                         @RequestParam(required = false) AccountStatus status,
                                         @RequestParam(required = false) String q) {
        List<UserService.UserRow> rows = users.list(currentActor.require(), role, status, q);
        CsvWriter csv = new CsvWriter("User ID", "Name", "Business", "Role", "Parent", "Status", "Email", "Mobile", "Wallet balance", "Joined");
        rows.forEach(r -> csv.row(r.accountCode(), r.displayName(), r.businessName(), r.role(), r.parentCode(), r.status(),
                r.email(), r.mobile(), r.walletBalance(), r.createdAt()));
        return csv.download("primepay-users.csv");
    }

    @GetMapping("/users/{id}")
    public ApiResponse<UserService.UserRow> get(@PathVariable Long id) {
        return ApiResponse.ok(users.get(currentActor.require(), id));
    }

    @GetMapping("/users/{id}/status-history")
    public ApiResponse<List<UserService.StatusChange>> history(@PathVariable Long id) {
        return ApiResponse.ok(users.history(currentActor.require(), id));
    }

    public record CreateUserBody(
            @NotBlank @Size(max = 40) @Pattern(regexp = "[A-Za-z0-9_-]+") String accountCode,
            @NotBlank @Size(max = 160) String displayName,
            @NotNull AccountRole role,
            Long parentId, // not used for a Platform Admin
            @NotBlank @Size(min = 3, max = 80) @Pattern(regexp = "[A-Za-z0-9._-]+") String username,
            @NotBlank @Size(min = 12, max = 200) String initialPassword,
            @Email @Size(max = 160) String email,
            @Pattern(regexp = "^$|\\+?[0-9]{7,15}") String mobile,
            @Size(max = 160) String businessName,
            @Size(max = 500) String address) {
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserService.UserRow> create(@Valid @RequestBody CreateUserBody body) {
        CreateAccountRequest request = new CreateAccountRequest(body.accountCode(), body.displayName(), body.role(), body.parentId(),
                body.username(), body.initialPassword());
        return ApiResponse.ok(users.create(currentActor.require(), request, body.email(), body.mobile(), body.businessName(), body.address()),
                "User created");
    }

    public record StatusBody(@NotNull AccountStatus status, @NotBlank @Size(max = 500) String reason) {
    }

    @PatchMapping("/users/{id}/status")
    public ApiResponse<UserService.UserRow> changeStatus(@PathVariable Long id, @Valid @RequestBody StatusBody body) {
        return ApiResponse.ok(users.changeStatus(currentActor.require(), id, body.status(), body.reason()), "Status updated");
    }

    @GetMapping("/users/me/profile")
    public ApiResponse<UserService.UserRow> myProfile() {
        AgentAccount me = currentActor.require();
        return ApiResponse.ok(users.get(me, me.getId()));
    }

    public record ProfileBody(@NotBlank @Size(max = 160) String displayName, @Size(max = 160) String businessName,
                              @Email @Size(max = 160) String email, @Size(max = 500) String address) {
    }

    @PutMapping("/users/me/profile")
    public ApiResponse<UserService.UserRow> updateProfile(@Valid @RequestBody ProfileBody body) {
        return ApiResponse.ok(users.updateProfile(currentActor.require(), body.displayName(), body.businessName(), body.email(), body.address()),
                "Profile saved");
    }

    @Operation(summary = "Your network as a tree (Platform Admin: whole platform)")
    @GetMapping("/hierarchy/tree")
    public ApiResponse<List<HierarchyService.HierarchyNode>> tree() {
        AgentAccount actor = currentActor.require();
        return ApiResponse.ok(hierarchy.tree(actor, wallets.balancesByAccount(hierarchy.scopeIds(actor))));
    }
}
