package com.dvcs.common.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as security-sensitive, triggering automatic audit logging.
 *
 * <p>When a method annotated with {@code @Audited} is invoked, the
 * {@link AuditLogAspect} intercepts the call and records an {@link AuditLog}
 * entry with the specified action and resource type.
 *
 * <p>The aspect extracts the actor ID from the Spring Security context and
 * the client IP from the current HTTP request (if available).
 *
 * <p>Example usage:
 * <pre>
 * {@literal @}Audited(action = "login", resourceType = "user")
 * public AuthResponse login(LoginRequest req) {
 *     // ...
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    /**
     * A short, machine-readable action name (e.g., {@code "login"}, {@code "push"},
     * {@code "delete_repo"}, {@code "merge_pr"}).
     *
     * @return the action name
     */
    String action();

    /**
     * The type of resource affected by this action (e.g., {@code "user"},
     * {@code "repository"}, {@code "pull_request"}).
     *
     * @return the resource type
     */
    String resourceType();
}
