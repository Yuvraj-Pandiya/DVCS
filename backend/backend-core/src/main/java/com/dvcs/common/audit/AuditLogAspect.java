package com.dvcs.common.audit;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * AOP aspect that intercepts methods annotated with {@link Audited} and records
 * an audit log entry via {@link AuditLogService}.
 *
 * <p>The aspect runs as an {@code @Around} advice so it can capture both the
 * method arguments and the return value. The audit record is written <em>after</em>
 * the method completes successfully; failed invocations are not audited (the
 * exception propagates normally).
 *
 * <p>Actor ID resolution:
 * <ol>
 *   <li>If the Spring Security context contains an authenticated principal whose
 *       name is a numeric string, it is parsed as the user ID.</li>
 *   <li>If the principal name is not numeric (e.g., a username), the actor ID
 *       is left as {@code null} — the audit record still captures the action.</li>
 *   <li>If there is no authenticated principal, actor ID is {@code null}.</li>
 * </ol>
 *
 * <p>Resource ID resolution:
 * <ol>
 *   <li>If the first method argument is a {@link Long}, it is used as the resource ID.</li>
 *   <li>Otherwise, resource ID is {@code null}.</li>
 * </ol>
 *
 * <p>IP resolution: extracted from the current {@link HttpServletRequest} via
 * {@link RequestContextHolder}. Falls back to {@code null} if no request is in scope
 * (e.g., in async or scheduled contexts).
 */
@Aspect
@Component
public class AuditLogAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditLogAspect.class);

    private final AuditLogService auditLogService;

    public AuditLogAspect(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    /**
     * Intercepts any method annotated with {@link Audited}, executes it, and
     * records an audit log entry on successful completion.
     *
     * @param joinPoint the proceeding join point
     * @param audited   the {@link Audited} annotation on the intercepted method
     * @return the return value of the intercepted method
     * @throws Throwable if the intercepted method throws
     */
    @Around("@annotation(audited)")
    public Object audit(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        // Execute the target method first
        Object result = joinPoint.proceed();

        // Record the audit entry after successful execution
        try {
            Long actorId = resolveActorId();
            Long resourceId = resolveResourceId(joinPoint.getArgs());
            String ip = resolveClientIp();

            auditLogService.record(actorId, audited.action(), audited.resourceType(), resourceId, ip);
        } catch (Exception e) {
            // Audit failures must never break the main request flow
            log.error("AuditLogAspect failed to record audit entry for action '{}': {}",
                    audited.action(), e.getMessage(), e);
        }

        return result;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Resolves the actor ID from the Spring Security context.
     *
     * @return the actor user ID, or {@code null} if unauthenticated or non-numeric principal
     */
    private static Long resolveActorId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        Object principal = auth.getPrincipal();
        if (principal == null || "anonymousUser".equals(principal)) {
            return null;
        }
        // Try to parse the principal name as a numeric user ID
        String name = auth.getName();
        try {
            return Long.parseLong(name);
        } catch (NumberFormatException e) {
            // Principal is a username string, not a numeric ID — return null
            return null;
        }
    }

    /**
     * Resolves the resource ID from the method arguments.
     *
     * <p>Uses the first {@link Long} argument as the resource ID. This convention
     * works for service methods whose first parameter is a user ID or resource ID.
     *
     * @param args the method arguments
     * @return the first {@link Long} argument, or {@code null} if none found
     */
    private static Long resolveResourceId(Object[] args) {
        if (args == null) {
            return null;
        }
        for (Object arg : args) {
            if (arg instanceof Long id) {
                return id;
            }
        }
        return null;
    }

    /**
     * Resolves the client IP address from the current HTTP request.
     *
     * <p>Respects the {@code X-Forwarded-For} header set by the nginx reverse proxy.
     *
     * @return the client IP address, or {@code null} if no request is in scope
     */
    private static String resolveClientIp() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return null;
            }
            HttpServletRequest request = attrs.getRequest();
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        } catch (Exception e) {
            return null;
        }
    }
}
