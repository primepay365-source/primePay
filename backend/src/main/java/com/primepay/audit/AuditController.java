package com.primepay.audit;

import com.primepay.common.api.ApiResponse;
import com.primepay.common.api.PageResponse;
import com.primepay.security.CurrentActor;
import com.primepay.user.AccountRole;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.OffsetDateTime;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Audit")
@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {
    private final AuditService audit;
    private final CurrentActor currentActor;

    public AuditController(AuditService audit, CurrentActor currentActor) {
        this.audit = audit;
        this.currentActor = currentActor;
    }

    @GetMapping
    public ApiResponse<PageResponse<AuditRow>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String action,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        currentActor.requireRole(AccountRole.ADMIN);
        var result = audit.search(q, action, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "occurredAt")));
        return ApiResponse.ok(PageResponse.from(result, AuditRow::from));
    }

    public record AuditRow(Long id, OffsetDateTime occurredAt, String actor, String action, String targetType,
                           String targetId, String result, String detail, String ipAddress, String requestId) {
        static AuditRow from(AuditLog log) {
            return new AuditRow(log.getId(), log.getOccurredAt(), log.getActorLabel(), log.getAction(), log.getTargetType(),
                    log.getTargetId(), log.getResult(), log.getDetail(), log.getIpAddress(), log.getRequestId());
        }
    }
}
