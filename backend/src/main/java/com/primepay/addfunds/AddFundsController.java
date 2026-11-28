package com.primepay.addfunds;

import com.primepay.common.api.ApiResponse;
import com.primepay.security.CurrentActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Add funds")
@RestController
@RequestMapping("/api/v1/add-funds")
public class AddFundsController {
    private final AddFundsService service;
    private final CurrentActor currentActor;

    public AddFundsController(AddFundsService service, CurrentActor currentActor) {
        this.service = service;
        this.currentActor = currentActor;
    }

    @GetMapping("/categories")
    public ApiResponse<List<AddFundsService.CategoryView>> categories(@RequestParam(defaultValue = "false") boolean all) {
        currentActor.require();
        return ApiResponse.ok(service.categories(all));
    }

    public record CategoryBody(@NotNull @DecimalMin("1.00") @Digits(integer = 17, fraction = 2) BigDecimal minAmount,
                               @NotNull @DecimalMin("1.00") @Digits(integer = 17, fraction = 2) BigDecimal maxAmount,
                               @NotNull @Digits(integer = 3, fraction = 6) BigDecimal chargePercent,
                               boolean enabled) {
    }

    @Operation(summary = "Platform Admin: change a gateway category's limits, charge and availability")
    @PutMapping("/categories/{id}")
    public ApiResponse<AddFundsService.CategoryView> updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryBody body) {
        return ApiResponse.ok(service.updateCategory(currentActor.require(), id, body.minAmount(), body.maxAmount(), body.chargePercent(), body.enabled()));
    }

    @Operation(summary = "Charges for a top-up: gateway charge, each upline level's margin and the amount credited")
    @GetMapping("/preview")
    public ApiResponse<AddFundsService.Breakdown> preview(@RequestParam String categoryCode, @RequestParam BigDecimal amount) {
        return ApiResponse.ok(service.preview(currentActor.require(), categoryCode, amount));
    }

    public record StartBody(@NotBlank String categoryCode,
                            @NotBlank @Pattern(regexp = "VISA|MASTERCARD|RUPAY", message = "Choose Visa, Mastercard or RuPay") String cardNetwork,
                            @NotBlank @Size(min = 2, max = 120) String cardHolder,
                            @NotNull @DecimalMin("1.00") @Digits(integer = 17, fraction = 2) BigDecimal amount) {
    }

    @Operation(summary = "Step 1: start an add-funds payment (category, card type, card holder, amount)")
    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AddFundsService.SessionView> start(@Valid @RequestBody StartBody body) {
        return ApiResponse.ok(service.start(currentActor.require(), body.categoryCode(), body.cardNetwork(), body.cardHolder(), body.amount()));
    }

    /** Only the last 4 digits of the card are accepted; the full number and CVV stay on the gateway page. */
    public record ModeBody(@NotBlank @Pattern(regexp = "CARD|UPI") String mode,
                           @Pattern(regexp = "[0-9]{4}", message = "Card details are incomplete") String cardLast4,
                           @Size(max = 100) String upiId) {
    }

    @Operation(summary = "Step 2: choose card (OTP sent) or UPI (collect request sent)")
    @PostMapping("/sessions/{ref}/pay")
    public ApiResponse<AddFundsService.SessionView> pay(@PathVariable String ref, @Valid @RequestBody ModeBody body) {
        return ApiResponse.ok(service.choosePaymentMode(currentActor.require(), ref, body.mode(), body.cardLast4(), body.upiId()));
    }

    public record CompleteBody(@Pattern(regexp = "[0-9]{6}", message = "Enter the 6-digit OTP") String otp, @Size(max = 100) String upiId) {
    }

    @Operation(summary = "Step 3: verify the OTP / confirm UPI approval; credits amount minus charge on success")
    @PostMapping("/sessions/{ref}/complete")
    public ApiResponse<AddFundsService.SessionView> complete(@PathVariable String ref, @Valid @RequestBody CompleteBody body) {
        return ApiResponse.ok(service.complete(currentActor.require(), ref, body.otp(), body.upiId()));
    }
}
