package com.primepay.admin;

import com.primepay.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only service list (fees and on/off) for any signed-in user, so screens show the real charge. */
@Tag(name = "Services")
@RestController
@RequestMapping("/api/v1/services")
public class ServiceCatalogController {
    private final ServiceDefinitionRepository services;

    public ServiceCatalogController(ServiceDefinitionRepository services) {
        this.services = services;
    }

    public record ServiceInfo(String serviceCode, String name, BigDecimal feeAmount, boolean enabled) {
    }

    @GetMapping
    public ApiResponse<List<ServiceInfo>> list() {
        return ApiResponse.ok(services.findAll().stream()
                .map(s -> new ServiceInfo(s.getServiceCode(), s.getName(), s.getFeeAmount(), s.isEnabled())).toList());
    }
}
