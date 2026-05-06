package com.dvcs.git.transport;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import java.io.InputStream;

/**
 * Stub implementation of {@link ReceivePackService} that is active only when no
 * other {@link ReceivePackService} bean is present in the application context.
 *
 * <p>This stub returns empty/minimal responses so that the transport layer
 * compiles and starts up before the full receive-pack implementation (task 6.3)
 * is complete. Once task 6.3 provides a real {@link ReceivePackService} bean,
 * this stub will be automatically displaced by the
 * {@link ConditionalOnMissingBean} condition.
 *
 * <p><strong>This class must be replaced by a real implementation in task 6.3.</strong>
 */
@Service
@ConditionalOnMissingBean(ReceivePackService.class)
public class StubReceivePackService implements ReceivePackService {

    /**
     * Returns an empty byte array as a placeholder ref advertisement.
     *
     * <p>Replace this in task 6.3 with a real pkt-line encoded ref list
     * built from the repository's branches and tags.
     *
     * @param repoId the internal repository ID
     * @return empty byte array (stub)
     */
    @Override
    public byte[] advertiseRefs(Long repoId) {
        // Stub: return empty advertisement.
        // TODO (task 6.3): return pkt-line encoded ref list for the repository.
        return new byte[0];
    }

    /**
     * No-op stub for receive-pack processing.
     *
     * <p>Replace this in task 6.3 with real ref-update parsing, pack decoding,
     * object storage, branch ref updates, cache invalidation, and event publishing.
     *
     * @param repoId the internal repository ID
     * @param userId the ID of the authenticated user performing the push
     * @param input  the client's ref-update + pack-file stream
     */
    @Override
    public void receivePack(Long repoId, Long userId, InputStream input) {
        // Stub: no-op.
        // TODO (task 6.3): implement full receive-pack processing.
    }
}
