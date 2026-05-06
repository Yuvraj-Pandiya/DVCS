package com.dvcs.git.transport;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * Stub implementation of {@link UploadPackService} that is active only when no
 * other {@link UploadPackService} bean is present in the application context.
 *
 * <p>This stub returns empty/minimal responses so that the transport layer
 * compiles and starts up before the full upload-pack implementation (task 6.2)
 * is complete. Once task 6.2 provides a real {@link UploadPackService} bean,
 * this stub will be automatically displaced by the
 * {@link ConditionalOnMissingBean} condition.
 *
 * <p><strong>This class must be replaced by a real implementation in task 6.2.</strong>
 */
@Service
@ConditionalOnMissingBean(UploadPackService.class)
public class StubUploadPackService implements UploadPackService {

    /**
     * Returns an empty byte array as a placeholder ref advertisement.
     *
     * <p>Replace this in task 6.2 with a real pkt-line encoded ref list
     * built from the repository's branches and tags.
     *
     * @param repoId the internal repository ID
     * @return empty byte array (stub)
     */
    @Override
    public byte[] advertiseRefs(Long repoId) {
        // Stub: return empty advertisement.
        // TODO (task 6.2): return pkt-line encoded ref list for the repository.
        return new byte[0];
    }

    /**
     * Returns an empty input stream as a placeholder pack-file response.
     *
     * <p>Replace this in task 6.2 with real want/have negotiation and
     * pack-file generation via {@code PackFileEncoder}.
     *
     * @param repoId the internal repository ID
     * @param input  the client's want/have request stream
     * @return empty input stream (stub)
     */
    @Override
    public InputStream uploadPack(Long repoId, InputStream input) {
        // Stub: return empty pack stream.
        // TODO (task 6.2): negotiate wants/haves and return a real pack stream.
        return new ByteArrayInputStream(new byte[0]);
    }
}
