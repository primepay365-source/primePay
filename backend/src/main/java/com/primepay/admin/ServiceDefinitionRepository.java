package com.primepay.admin;

import com.primepay.common.error.BusinessException;
import com.primepay.common.error.ErrorCode;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceDefinitionRepository extends JpaRepository<ServiceDefinition, Long> {
    Optional<ServiceDefinition> findByServiceCode(String serviceCode);

    default ServiceDefinition requireEnabled(String serviceCode) {
        ServiceDefinition service = findByServiceCode(serviceCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Unknown service " + serviceCode + "."));
        if (!service.isEnabled()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE, service.getName() + " is turned off by the platform admin.");
        }
        return service;
    }
}
