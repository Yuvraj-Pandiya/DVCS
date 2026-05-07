package com.dvcs.common.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

/**
 * JPA entity mapping the {@code audit_logs} table.
 *
 * <p>Records security-sensitive operations performed by authenticated users,
 * including login, push, repository deletion, and PR merge events (Req 18).
 *
 * <p>The {@code ip} column stores the client IP address as a string; PostgreSQL
 * maps it to the {@code INET} type via the {@code columnDefinition}.
 */
@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The ID of the user who performed the action.
     * May be {@code null} for unauthenticated actions.
     */
    @Column(name = "actor_id")
    private Long actorId;

    /**
     * A short, machine-readable action name (e.g., {@code "login"}, {@code "push"},
     * {@code "delete_repo"}, {@code "merge_pr"}).
     */
    @Column(name = "action", nullable = false, length = 128)
    private String action;

    /**
     * The type of resource affected (e.g., {@code "user"}, {@code "repository"},
     * {@code "pull_request"}).
     */
    @Column(name = "resource_type", nullable = false, length = 64)
    private String resourceType;

    /**
     * The primary key of the affected resource. May be {@code null} for actions
     * that do not target a specific entity.
     */
    @Column(name = "resource_id")
    private Long resourceId;

    /**
     * The client IP address. Stored as {@code INET} in PostgreSQL.
     */
    @Column(name = "ip", columnDefinition = "INET")
    private String ip;

    /**
     * Timestamp when the audit record was created. Set automatically by the database.
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
