package com.primepay.audit;

import com.primepay.common.web.RequestIdFilter;
import com.primepay.user.AgentAccount;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class AuditService {
    public static final String SUCCESS = "SUCCESS";
    public static final String DENIED = "DENIED";
    public static final String FAILED = "FAILED";

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    /** Written in the caller's transaction, so it commits or rolls back with the business change. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AgentAccount actor, String action, String targetType, Object targetId, String detail) {
        save(actor == null ? null : actor.getId(), label(actor), action, targetType, targetId, SUCCESS, detail);
    }

    /** Same as record, with an explicit result (e.g. FAILED for a declined payout). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AgentAccount actor, String action, String targetType, Object targetId, String detail, String result) {
        save(actor == null ? null : actor.getId(), label(actor), action, targetType, targetId, result, detail);
    }

    /** Written in its own transaction: used for denied or failed attempts whose business transaction rolls back. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIndependent(Long actorId, String actorLabel, String action, String targetType, Object targetId,
                                  String result, String detail) {
        save(actorId, actorLabel, action, targetType, targetId, result, detail);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> search(String q, String action, Pageable pageable) {
        Specification<AuditLog> spec = (root, query, cb) -> cb.conjunction();
        if (action != null && !action.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.like(root.get("action"), action.trim().toUpperCase() + "%"));
        }
        if (q != null && !q.isBlank()) {
            String like = "%" + q.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("actorLabel")), like),
                    cb.like(cb.lower(root.get("targetId")), like),
                    cb.like(cb.lower(root.get("action")), like)));
        }
        return repository.findAll(spec, pageable);
    }

    private void save(Long actorId, String actorLabel, String action, String targetType, Object targetId, String result, String detail) {
        repository.save(new AuditLog(actorId, actorLabel, action, targetType, targetId == null ? null : String.valueOf(targetId),
                result, detail, clientIp(), RequestIdFilter.currentRequestId()));
    }

    static String label(AgentAccount actor) {
        return actor == null ? "system" : actor.getAccountCode();
    }

    private static String clientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            return request.getRemoteAddr();
        }
        return null;
    }
}
