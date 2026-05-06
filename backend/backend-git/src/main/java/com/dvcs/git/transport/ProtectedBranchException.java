package com.dvcs.git.transport;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when a push targets a protected branch.
 *
 * <p>Annotated with {@link ResponseStatus} so that Spring MVC maps it to
 * HTTP 403 Forbidden when it propagates out of a controller.
 *
 * <p>Requirement 5.7 / Requirement 6: Branch protection enforcement during push.
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class ProtectedBranchException extends RuntimeException {

    /**
     * Constructs a {@code ProtectedBranchException} for the given branch name.
     *
     * @param branchName the name of the protected branch that was targeted
     */
    public ProtectedBranchException(String branchName) {
        super("Branch '" + branchName + "' is protected and cannot be pushed to.");
    }
}
