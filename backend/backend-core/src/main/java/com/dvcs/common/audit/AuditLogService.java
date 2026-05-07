package com.dvcs.common.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for recording security-sensitive audit events.
 *
 * <p>Each call to {@link #record} persists one {@link AuditLog} row in the
 * {@code audit_logs} table. The method runs in its own transaction
 * ({@link Propagation#REQUIRES_NEW}) so that audit records are committed even
 * if the calling transaction is rolled back.
 *
 * <p>Audit records are written asynchronously to avoid adding latency to the
 * request path. If the write fails, the error is logged but not propagated.
 */
@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Records an audit event asynchronously in a new transaction.
     *
     * <p>This method is intentionally {@code @Async} so that audit writes do not
     * block the request thread. The {@link Propagation#REQUIRES_NEW} propagation
     * ensures the audit record is committed independently of the caller's transaction.
     *
     * @param actorId      the ID of the user performing the action (may be {@code null})
     * @param action       a short, machine-readable action name (e.g., {@code "login"})
     * @param resourceType the type of resource affected (e.g., {@code "repository"})
     * @param resourceId   the primary key of the affected resource (may be {@code null})
     * @param ip           the client IP address (may be {@code null})
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long actorId, String action, String resourceType, Long resourceId, String ip) {
        try {
            AuditLog entry = AuditLog.builder()
                    .actorId(actorId)
                    .action(action)
                    .resourceType(resourceType)
                    .resourceId(resourceId)
                    .ip(ip)
                    .build();
            auditLogRepository.save(entry);
            log.debug("Audit: actor={} action={} resourceType={} resourceId={} ip={}",
                    actorId, action, resourceType, resourceId, ip);
        } catch (Exception e) {
            // Audit failures must never break the main request flow
            log.error("Failed to write audit log: actor={} action={} error={}",
                    actorId, action, e.getMessage(), e);
        }
    }
}
