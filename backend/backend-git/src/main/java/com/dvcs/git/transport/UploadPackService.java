package com.dvcs.git.transport;

import java.io.InputStream;

/**
 * Service interface for the Git upload-pack protocol (clone / fetch).
 *
 * <p>Implementations handle the server side of the Git smart HTTP upload-pack
 * protocol, which is used by {@code git clone} and {@code git fetch}.
 *
 * <p>Full implementation is provided in task 6.2.
 */
public interface UploadPackService {

    /**
     * Produces the pkt-line encoded ref advertisement for the given repository.
     *
     * <p>The returned bytes are the raw ref-advertisement payload (without the
     * {@code # service=git-upload-pack\n} preamble — that is prepended by the
     * controller).
     *
     * @param repoId the internal repository ID
     * @return pkt-line encoded ref advertisement bytes
     */
    byte[] advertiseRefs(Long repoId);

    /**
     * Performs want/have negotiation and streams the requested objects as a
     * pack file.
     *
     * @param repoId the internal repository ID
     * @param input  the request body containing the client's want/have lines
     * @return an {@link InputStream} over the pack-file response bytes
     */
    InputStream uploadPack(Long repoId, InputStream input);
}
