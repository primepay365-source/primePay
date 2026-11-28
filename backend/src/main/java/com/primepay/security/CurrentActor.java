package com.primepay.security;

import com.primepay.common.error.BusinessException;
import com.primepay.common.error.ErrorCode;
import com.primepay.user.AccountRole;
import com.primepay.user.AgentAccount;
import com.primepay.user.AgentAccountRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Resolves the signed-in account from the JWT on every call and re-checks it is still ACTIVE in the database,
 * so a suspension takes effect immediately even for tokens issued earlier.
 */
@Component
public class CurrentActor {
    private final AgentAccountRepository accounts;

    public CurrentActor(AgentAccountRepository accounts) {
        this.accounts = accounts;
    }

    public AgentAccount require() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        Object claim = jwt.getClaim("account_id");
        long id;
        try {
            id = Long.parseLong(String.valueOf(claim));
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        AgentAccount actor = accounts.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
        if (!actor.isActive()) {
            throw new BusinessException(ErrorCode.ACCOUNT_INACTIVE, "Your account is not active.");
        }
        return actor;
    }

    public AgentAccount requireRole(AccountRole... roles) {
        AgentAccount actor = require();
        for (AccountRole role : roles) {
            if (actor.getRole() == role) {
                return actor;
            }
        }
        throw new AccessDeniedException("Your role can't do this.");
    }

    public String username() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? "system" : auth.getName();
    }
}
