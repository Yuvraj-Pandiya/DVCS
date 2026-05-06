package com.dvcs.git.transport;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

/**
 * Stub implementation of {@link RepoLookupService} that is active only when no
 * other {@link RepoLookupService} bean is present in the application context.
 *
 * <p>This stub always throws {@link RepoNotFoundException} so that the transport
 * layer compiles and starts up before the full repository module (task 7.1) is
 * implemented. Once task 7.1 provides a real {@link RepoLookupService} bean backed
 * by the JPA {@code RepoRepository}, this stub will be automatically displaced by
 * the {@link ConditionalOnMissingBean} condition.
 *
 * <p><strong>This class must be replaced by a real implementation in task 7.1.</strong>
 */
@Service
@ConditionalOnMissingBean(RepoLookupService.class)
public class StubRepoLookupService implements RepoLookupService {

    /**
     * Always throws {@link RepoNotFoundException}.
     *
     * <p>Replace this implementation in task 7.1 with a real lookup against
     * the {@code repositories} table via {@code RepoRepository}.
     *
     * @param owner    the repository owner username
     * @param repoName the repository name
     * @return never returns normally
     * @throws RepoNotFoundException always
     */
    @Override
    public Long resolveRepoId(String owner, String repoName) {
        throw new RepoNotFoundException(owner, repoName);
    }
}
