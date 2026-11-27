package com.primepay.transaction;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Prefix + yyMMdd (business date) + 7-digit database sequence, e.g. PPT2611260001042. Unique across nodes. */
@Component
public class ReferenceGenerator {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyMMdd");
    private final EntityManager entityManager;
    private final ZoneId businessZone;

    public ReferenceGenerator(EntityManager entityManager, @Value("${app.business-timezone:Asia/Kolkata}") String zone) {
        this.entityManager = entityManager;
        this.businessZone = ZoneId.of(zone);
    }

    public String next(String prefix) {
        Number value = (Number) entityManager.createNativeQuery("select pp_txn_ref_seq.nextval from dual").getSingleResult();
        return prefix + LocalDate.now(businessZone).format(DATE) + String.format("%07d", value.longValue());
    }
}
