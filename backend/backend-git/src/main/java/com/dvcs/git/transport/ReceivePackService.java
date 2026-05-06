package com.dvcs.git.transport;

import java.io.IOException;
import java.io.InputStream;

/**
 * Service interface for the Git receive-pack protocol (push).
 *
 * <p>Implementations handle the server side of the Git smart HTTP receive-pack
 * protocol, which is used by {@code git push}.
 *
 * <p>Full implementation is provided in task 6.3.
 */
public interface ReceivePackService {

    /**
     * Produces the pkt-line encoded ref advertisement for the given repository.
     *
     * <p>The returned bytes are the raw ref-advertisement payload (without the
     * {@code # service=git-receive-pack\n} preamble — that is prepended by the
     * controller).
     *
     * @param repoId the internal repository ID
     * @return pkt-line encoded ref advertisement bytes
     */
    byte[] advertiseRefs(Long repoId);

    /**
     * Processes an incoming push: parses ref updates, unpacks objects, updates
     * branch refs, and triggers downstream events.
     *
     * @param repoId the internal repository ID
     * @param userId the ID of the authenticated user performing the push
     * @param input  the request body containing pkt-line ref updates followed
     *               by the pack-file data
     * @throws IOException if reading the input stream or writing objects fails
     */
    void receivePack(Long repoId, Long userId, InputStream input) throws IOException;
}
