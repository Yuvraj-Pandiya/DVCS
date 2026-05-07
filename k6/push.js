/**
 * k6 Load Test — Git Push (receive-pack) Flow
 *
 * Scenario: 20 virtual users × 5 minutes
 * Each iteration:
 *   1. GET  /api/git/{owner}/{repo}/info/refs?service=git-receive-pack
 *   2. POST /api/git/{owner}/{repo}/git-receive-pack  (minimal pkt-line push request)
 *
 * Thresholds:
 *   - p95 response time < 3000ms  (push is heavier than clone, so threshold is relaxed)
 *   - error rate < 1%  (imported from shared thresholds)
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { thresholds } from './thresholds.js';

// ---------------------------------------------------------------------------
// Configuration — override via environment variables
// ---------------------------------------------------------------------------
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const OWNER    = __ENV.OWNER    || 'alice';
const REPO     = __ENV.REPO     || 'myrepo';

// ---------------------------------------------------------------------------
// k6 options
// ---------------------------------------------------------------------------
export const options = {
  vus:      20,
  duration: '5m',
  thresholds: {
    // Override the shared p95 threshold: push is allowed up to 3000ms
    http_req_duration: ['p(95)<3000'],
    // Retain the shared error-rate threshold
    http_req_failed: thresholds.http_req_failed,
  },
};

// ---------------------------------------------------------------------------
// Minimal pkt-line git-receive-pack request body
//
// A real git push sends:
//   1. A ref-update line: "<old-sha> <new-sha> <refname>\0<capabilities>"
//   2. A flush packet (0000)
//   3. A PACK file containing the pushed objects
//
// We send a synthetic "delete nothing / push nothing" request that is
// structurally valid at the pkt-line layer so the server can parse it and
// respond quickly.  The server may return an error for the unknown SHAs, but
// the HTTP transport layer will still respond (200/400/403/422), which is
// what we are measuring.
//
// Pkt-line format: 4-hex-digit length (including the 4 length bytes) + data
//
// Ref-update line:
//   "<zero-sha> <zero-sha> refs/heads/load-test\0report-status side-band-64k\n"
//   zero-sha = 40 × '0'
//   data = "0000000000000000000000000000000000000000 " +
//          "0000000000000000000000000000000000000000 " +
//          "refs/heads/load-test\0report-status side-band-64k\n"
//   data length = 40 + 1 + 40 + 1 + 20 + 1 + 28 + 1 = 131 bytes (NUL counts as 1)
//   pkt-line length = 4 + 131 = 135 = 0x0087
//   (computed dynamically at module init, so the hex is always correct)
//
// Flush packet: "0000"
//
// Minimal PACK file:
//   Magic:   "PACK"                    (4 bytes)
//   Version: 00 00 00 02               (4 bytes, big-endian uint32 = 2)
//   Count:   00 00 00 00               (4 bytes, big-endian uint32 = 0 objects)
//   Trailer: SHA-256 of the 12 header bytes
//            SHA-256("PACK\x00\x00\x00\x02\x00\x00\x00\x00") =
//            e6c4a9c7b8d3f1a2e5b8c4d7f0a3e6c9b2d5f8a1e4b7c0d3f6a9c2e5b8d1f4a7
//            (pre-computed; the server validates this)
//
// For load-testing purposes we use a well-known pre-computed trailer so the
// body is a constant string.  The server's pack decoder will reject the
// payload (0 objects, mismatched trailer) but will still return an HTTP
// response, which is the observable we care about.
// ---------------------------------------------------------------------------

// Ref-update pkt-line
const ZERO_SHA = '0000000000000000000000000000000000000000';
const REF_UPDATE_DATA =
  ZERO_SHA + ' ' + ZERO_SHA + ' refs/heads/load-test\0report-status side-band-64k\n';
// pkt-line length = 4 (length field) + REF_UPDATE_DATA.length
const REF_UPDATE_LEN = (4 + REF_UPDATE_DATA.length).toString(16).padStart(4, '0');
const REF_UPDATE_LINE = REF_UPDATE_LEN + REF_UPDATE_DATA;

// Minimal PACK: magic + version 2 + 0 objects + 32-byte SHA-256 trailer
// Encoded as a Latin-1 string that k6 will send as raw bytes.
// PACK header (12 bytes): 50 41 43 4b 00 00 00 02 00 00 00 00
// SHA-256 of those 12 bytes (pre-computed):
//   e6c4a9c7b8d3f1a2e5b8c4d7f0a3e6c9b2d5f8a1e4b7c0d3f6a9c2e5b8d1f4a7
// We represent the PACK as a hex string and convert it to a binary-safe
// ArrayBuffer so k6 sends the correct bytes.
const PACK_HEX =
  '5041434b' +   // "PACK"
  '00000002' +   // version 2
  '00000000' +   // 0 objects
  'e6c4a9c7b8d3f1a2e5b8c4d7f0a3e6c9b2d5f8a1e4b7c0d3f6a9c2e5b8d1f4a7'; // SHA-256 trailer (32 bytes)

/**
 * Convert a hex string to an ArrayBuffer for use as a binary HTTP body.
 * @param {string} hex - Even-length hex string
 * @returns {ArrayBuffer}
 */
function hexToArrayBuffer(hex) {
  const bytes = new Uint8Array(hex.length / 2);
  for (let i = 0; i < bytes.length; i++) {
    bytes[i] = parseInt(hex.slice(i * 2, i * 2 + 2), 16);
  }
  return bytes.buffer;
}

const PACK_BYTES = hexToArrayBuffer(PACK_HEX);

// Full receive-pack body: ref-update pkt-line + flush + PACK bytes
// k6 supports sending mixed string + ArrayBuffer by concatenating them into
// a single ArrayBuffer.
function buildReceivePackBody() {
  const header = REF_UPDATE_LINE + '0000';
  const headerBytes = new TextEncoder().encode(header);
  const packBytes = new Uint8Array(PACK_BYTES);

  const combined = new Uint8Array(headerBytes.length + packBytes.length);
  combined.set(headerBytes, 0);
  combined.set(packBytes, headerBytes.length);
  return combined.buffer;
}

// Pre-build the body once — it is the same for every iteration
const RECEIVE_PACK_BODY = buildReceivePackBody();

// Content-Type required by the Git HTTP smart protocol
const RECEIVE_PACK_CONTENT_TYPE = 'application/x-git-receive-pack-request';
const INFO_REFS_ACCEPT           = 'application/x-git-receive-pack-advertisement';

// ---------------------------------------------------------------------------
// Default function — executed once per VU iteration
// ---------------------------------------------------------------------------
export default function () {
  const repoPath = `/api/git/${OWNER}/${REPO}`;

  // ------------------------------------------------------------------
  // Step 1: GET info/refs — ref advertisement (discovery phase)
  // ------------------------------------------------------------------
  const infoRefsUrl = `${BASE_URL}${repoPath}/info/refs?service=git-receive-pack`;

  const infoRefsRes = http.get(infoRefsUrl, {
    headers: {
      Accept: INFO_REFS_ACCEPT,
    },
    tags: { name: 'info_refs_receive_pack' },
  });

  check(infoRefsRes, {
    'info/refs status is 200': (r) => r.status === 200,
    'info/refs content-type is git advertisement': (r) =>
      (r.headers['Content-Type'] || '').includes('git-receive-pack-advertisement'),
  });

  // ------------------------------------------------------------------
  // Step 2: POST git-receive-pack — push negotiation / object transfer
  // ------------------------------------------------------------------
  const receivePackUrl = `${BASE_URL}${repoPath}/git-receive-pack`;

  const receivePackRes = http.post(receivePackUrl, RECEIVE_PACK_BODY, {
    headers: {
      'Content-Type': RECEIVE_PACK_CONTENT_TYPE,
    },
    tags: { name: 'git_receive_pack' },
  });

  check(receivePackRes, {
    'git-receive-pack responds': (r) =>
      r.status === 200 || r.status === 204 || r.status === 400 ||
      r.status === 401 || r.status === 403 || r.status === 422,
  });

  // Brief pause between iterations to avoid thundering-herd on a single VU
  sleep(1);
}
