/**
 * k6 Load Test — Git Clone (upload-pack) Flow
 *
 * Scenario: 50 virtual users × 5 minutes
 * Each iteration:
 *   1. GET  /api/git/{owner}/{repo}/info/refs?service=git-upload-pack
 *   2. POST /api/git/{owner}/{repo}/git-upload-pack  (minimal pkt-line want request)
 *
 * Thresholds (imported from shared module):
 *   - p95 response time < 2000ms
 *   - error rate < 1%
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
  vus:      50,
  duration: '5m',
  thresholds,
};

// ---------------------------------------------------------------------------
// Minimal pkt-line git-upload-pack request body
//
// A real git client sends a "want" line for each ref it needs, followed by
// a flush packet (0000) and a "done" line.  We send a single synthetic want
// for the all-zeros SHA so the server can respond quickly without actually
// building a pack.  The server may return an error for an unknown SHA, but
// the HTTP layer will still respond with 200 (or 404/500), which is what we
// are measuring.
//
// Pkt-line format: 4-hex-digit length (including the 4 length bytes) + data
//   "0032want 0000000000000000000000000000000000000000\n"
//   length = 4 + len("want 0000000000000000000000000000000000000000\n")
//          = 4 + 46 = 50 = 0x32
//   "0000" = flush packet
//   "0009done\n" = done line (4 + 5 = 9 = 0x09)
// ---------------------------------------------------------------------------
const UPLOAD_PACK_BODY =
  '0032want 0000000000000000000000000000000000000000\n' +
  '0000' +
  '0009done\n';

// Content-Type required by the Git HTTP smart protocol
const UPLOAD_PACK_CONTENT_TYPE = 'application/x-git-upload-pack-request';
const INFO_REFS_ACCEPT          = 'application/x-git-upload-pack-advertisement';

// ---------------------------------------------------------------------------
// Default function — executed once per VU iteration
// ---------------------------------------------------------------------------
export default function () {
  const repoPath = `/api/git/${OWNER}/${REPO}`;

  // ------------------------------------------------------------------
  // Step 1: GET info/refs — ref advertisement (discovery phase)
  // ------------------------------------------------------------------
  const infoRefsUrl = `${BASE_URL}${repoPath}/info/refs?service=git-upload-pack`;

  const infoRefsRes = http.get(infoRefsUrl, {
    headers: {
      Accept: INFO_REFS_ACCEPT,
    },
    tags: { name: 'info_refs_upload_pack' },
  });

  check(infoRefsRes, {
    'info/refs status is 200': (r) => r.status === 200,
    'info/refs content-type is git advertisement': (r) =>
      (r.headers['Content-Type'] || '').includes('git-upload-pack-advertisement'),
  });

  // ------------------------------------------------------------------
  // Step 2: POST git-upload-pack — pack negotiation / data transfer
  // ------------------------------------------------------------------
  const uploadPackUrl = `${BASE_URL}${repoPath}/git-upload-pack`;

  const uploadPackRes = http.post(uploadPackUrl, UPLOAD_PACK_BODY, {
    headers: {
      'Content-Type': UPLOAD_PACK_CONTENT_TYPE,
    },
    tags: { name: 'git_upload_pack' },
  });

  check(uploadPackRes, {
    'git-upload-pack responds': (r) =>
      r.status === 200 || r.status === 204 || r.status === 401 || r.status === 403,
  });

  // Brief pause between iterations to avoid thundering-herd on a single VU
  sleep(1);
}
