package com.dvcs.git.transport;

import com.dvcs.auth.domain.User;
import com.dvcs.common.security.RepoAccessGuard;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * REST controller implementing the Git smart HTTP transport protocol.
 *
 * <p>Exposes three endpoints under {@code /api/git/{owner}/{repo}}:
 * <ol>
 *   <li>{@code GET  /info/refs?service=git-upload-pack|git-receive-pack} — ref advertisement</li>
 *   <li>{@code POST /git-upload-pack} — clone / fetch (upload-pack)</li>
 *   <li>{@code POST /git-receive-pack} — push (receive-pack)</li>
 * </ol>
 *
 * <h2>Git smart HTTP protocol overview</h2>
 * <p>The Git smart HTTP protocol uses a two-step handshake for both clone and push:
 * <ol>
 *   <li>The client sends a {@code GET /info/refs?service=...} request to discover
 *       the server's advertised references.</li>
 *   <li>The client sends a {@code POST} to the appropriate service endpoint to
 *       perform the actual data transfer.</li>
 * </ol>
 *
 * <h2>Pkt-line format</h2>
 * <p>The ref advertisement response body must begin with a pkt-line encoded
 * service announcement followed by a flush-pkt:
 * <pre>
 *   pkt-line("# service=git-upload-pack\n") = "001e# service=git-upload-pack\n"
 *   flush-pkt                               = "0000"
 * </pre>
 * The 4-hex-digit length prefix includes the 4 bytes of the prefix itself.
 *
 * <h2>Access control</h2>
 * <p>Read operations (GET and upload-pack) require at least READ access via
 * {@code @repoAccessGuard.canRead}. Write operations (receive-pack) require
 * WRITE or OWNER access via {@code @repoAccessGuard.canWrite}.
 *
 * @see UploadPackService
 * @see ReceivePackService
 * @see RepoAccessGuard
 */
@RestController
@RequestMapping("/api/git/{owner}/{repo}")
public class GitTransportController {

    /** Git service name for clone/fetch. */
    private static final String SERVICE_UPLOAD_PACK = "git-upload-pack";

    /** Git service name for push. */
    private static final String SERVICE_RECEIVE_PACK = "git-receive-pack";

    /** Flush-pkt that terminates the service-line preamble. */
    private static final byte[] FLUSH_PKT = "0000".getBytes(StandardCharsets.US_ASCII);

    private final UploadPackService uploadPackService;
    private final ReceivePackService receivePackService;
    private final RepoLookupService repoLookupService;

    /**
     * Constructs the controller with its required service collaborators.
     *
     * @param uploadPackService  handles clone/fetch operations
     * @param receivePackService handles push operations
     * @param repoLookupService  resolves owner+name to an internal repository ID
     */
    public GitTransportController(UploadPackService uploadPackService,
                                  ReceivePackService receivePackService,
                                  RepoLookupService repoLookupService) {
        this.uploadPackService = uploadPackService;
        this.receivePackService = receivePackService;
        this.repoLookupService = repoLookupService;
    }

    // -------------------------------------------------------------------------
    // GET /info/refs — ref advertisement
    // -------------------------------------------------------------------------

    /**
     * Handles the Git smart HTTP ref-advertisement request.
     *
     * <p>The {@code service} query parameter determines which service is being
     * advertised:
     * <ul>
     *   <li>{@code git-upload-pack} — for clone/fetch</li>
     *   <li>{@code git-receive-pack} — for push</li>
     * </ul>
     *
     * <p>The response body format (per the Git smart HTTP spec):
     * <pre>
     *   pkt-line("# service={service}\n")
     *   flush-pkt ("0000")
     *   {raw ref advertisement bytes from the service}
     * </pre>
     *
     * @param owner   the repository owner's username (path variable)
     * @param repo    the repository name (path variable)
     * @param service the requested Git service ({@code git-upload-pack} or
     *                {@code git-receive-pack})
     * @return the ref advertisement response with the appropriate content type
     */
    @GetMapping("/info/refs")
    @PreAuthorize("@repoAccessGuard.canRead(authentication, #owner, #repo)")
    public ResponseEntity<byte[]> infoRefs(
            @PathVariable String owner,
            @PathVariable String repo,
            @RequestParam("service") String service) {

        validateService(service);

        Long repoId = repoLookupService.resolveRepoId(owner, repo);

        byte[] refsPayload;
        if (SERVICE_UPLOAD_PACK.equals(service)) {
            refsPayload = uploadPackService.advertiseRefs(repoId);
        } else {
            refsPayload = receivePackService.advertiseRefs(repoId);
        }

        byte[] body = buildInfoRefsBody(service, refsPayload);

        String contentType = "application/x-" + service + "-advertisement";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header("Cache-Control", "no-cache")
                .header("Pragma", "no-cache")
                .body(body);
    }

    // -------------------------------------------------------------------------
    // POST /git-upload-pack — clone / fetch
    // -------------------------------------------------------------------------

    /**
     * Handles the Git upload-pack POST request (clone / fetch).
     *
     * <p>Reads the client's want/have negotiation from the request body and
     * delegates to {@link UploadPackService#uploadPack(Long, InputStream)} to
     * perform object negotiation and stream the resulting pack file.
     *
     * @param owner   the repository owner's username (path variable)
     * @param repo    the repository name (path variable)
     * @param request the HTTP servlet request (used to obtain the input stream)
     * @return the pack-file response stream
     * @throws IOException if reading the request body or streaming the response fails
     */
    @PostMapping(
            value = "/git-upload-pack",
            consumes = "application/x-git-upload-pack-request",
            produces = "application/x-git-upload-pack-result"
    )
    @PreAuthorize("@repoAccessGuard.canRead(authentication, #owner, #repo)")
    public ResponseEntity<byte[]> uploadPack(
            @PathVariable String owner,
            @PathVariable String repo,
            HttpServletRequest request) throws IOException {

        Long repoId = repoLookupService.resolveRepoId(owner, repo);

        InputStream resultStream = uploadPackService.uploadPack(repoId, request.getInputStream());
        byte[] responseBytes = resultStream.readAllBytes();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/x-git-upload-pack-result"))
                .header("Cache-Control", "no-cache")
                .header("Pragma", "no-cache")
                .body(responseBytes);
    }

    // -------------------------------------------------------------------------
    // POST /git-receive-pack — push
    // -------------------------------------------------------------------------

    /**
     * Handles the Git receive-pack POST request (push).
     *
     * <p>Reads the client's ref-update commands and pack data from the request
     * body, extracts the authenticated user's ID from the {@link Authentication}
     * principal, and delegates to
     * {@link ReceivePackService#receivePack(Long, Long, InputStream)}.
     *
     * @param owner          the repository owner's username (path variable)
     * @param repo           the repository name (path variable)
     * @param authentication the current Spring Security authentication; must be
     *                       authenticated (enforced by {@code @PreAuthorize})
     * @param request        the HTTP servlet request (used to obtain the input stream)
     * @return HTTP 200 with an empty body on success
     * @throws IOException if reading the request body or processing the pack fails
     */
    @PostMapping(
            value = "/git-receive-pack",
            consumes = "application/x-git-receive-pack-request",
            produces = "application/x-git-receive-pack-result"
    )
    @PreAuthorize("@repoAccessGuard.canWrite(authentication, #owner, #repo)")
    public ResponseEntity<byte[]> receivePack(
            @PathVariable String owner,
            @PathVariable String repo,
            Authentication authentication,
            HttpServletRequest request) throws IOException {

        Long repoId = repoLookupService.resolveRepoId(owner, repo);
        Long userId = extractUserId(authentication);

        receivePackService.receivePack(repoId, userId, request.getInputStream());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/x-git-receive-pack-result"))
                .header("Cache-Control", "no-cache")
                .header("Pragma", "no-cache")
                .body(new byte[0]);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Builds the full response body for a {@code GET /info/refs} request.
     *
     * <p>The Git smart HTTP spec requires the body to start with a pkt-line
     * encoding of {@code "# service={service}\n"} followed by a flush-pkt
     * ({@code "0000"}), then the raw ref-advertisement bytes from the service.
     *
     * <p>Pkt-line format: a 4-hex-digit length prefix (inclusive of the 4 prefix
     * bytes themselves) followed by the data bytes.
     *
     * @param service     the Git service name (e.g. {@code "git-upload-pack"})
     * @param refsPayload the raw ref-advertisement bytes from the service
     * @return the complete response body bytes
     */
    private static byte[] buildInfoRefsBody(String service, byte[] refsPayload) {
        String serviceLine = "# service=" + service + "\n";
        byte[] serviceLineBytes = serviceLine.getBytes(StandardCharsets.US_ASCII);

        // pkt-line length = 4 (length field itself) + data length
        int pktLen = 4 + serviceLineBytes.length;
        String pktLenHex = String.format("%04x", pktLen);
        byte[] pktLenBytes = pktLenHex.getBytes(StandardCharsets.US_ASCII);

        ByteArrayOutputStream out = new ByteArrayOutputStream(
                pktLenBytes.length + serviceLineBytes.length + FLUSH_PKT.length + refsPayload.length);
        out.writeBytes(pktLenBytes);
        out.writeBytes(serviceLineBytes);
        out.writeBytes(FLUSH_PKT);
        out.writeBytes(refsPayload);
        return out.toByteArray();
    }

    /**
     * Validates that the {@code service} query parameter is one of the two
     * recognised Git service names.
     *
     * @param service the value of the {@code service} query parameter
     * @throws ResponseStatusException HTTP 400 if the service name is unrecognised
     */
    private static void validateService(String service) {
        if (!SERVICE_UPLOAD_PACK.equals(service) && !SERVICE_RECEIVE_PACK.equals(service)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unknown Git service: '" + service + "'. "
                    + "Expected 'git-upload-pack' or 'git-receive-pack'.");
        }
    }

    /**
     * Extracts the user ID from the Spring Security {@link Authentication} principal.
     *
     * <p>The {@link com.dvcs.common.security.JwtAuthenticationFilter} sets the
     * principal to a {@link User} entity, so this cast is safe when the filter
     * has run and the {@code @PreAuthorize} check has passed.
     *
     * @param authentication the current authentication; must not be {@code null}
     * @return the authenticated user's ID
     * @throws ResponseStatusException HTTP 401 if the principal is not a {@link User}
     */
    private static Long extractUserId(Authentication authentication) {
        User user = RepoAccessGuard.extractUser(authentication);
        if (user == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication principal is not a recognised User.");
        }
        return user.getId();
    }
}
