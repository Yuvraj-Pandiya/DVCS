/**
 * k6 Load Test — Pull Request Merge Flow
 *
 * Scenario: 10 virtual users × 5 minutes
 * Each iteration:
 *   1. POST /api/repos/{owner}/{repo}/pulls          — create a PR
 *   2. POST /api/repos/{owner}/{repo}/pulls/{id}/review — submit APPROVE review
 *   3. POST /api/repos/{owner}/{repo}/pulls/{id}/merge?strategy=squash — merge PR
 *
 * Thresholds:
 *   - p95 response time < 5000ms  (PR merge is heavier than clone/push)
 *   - error rate < 1%  (imported from shared thresholds)
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { thresholds } from './thresholds.js';

// ---------------------------------------------------------------------------
// Configuration — override via environment variables
// ---------------------------------------------------------------------------
const BASE_URL   = __ENV.BASE_URL   || 'http://localhost:8080';
const OWNER      = __ENV.OWNER      || 'alice';
const REPO       = __ENV.REPO       || 'myrepo';
const AUTH_TOKEN = __ENV.AUTH_TOKEN || '';

// ---------------------------------------------------------------------------
// k6 options
// ---------------------------------------------------------------------------
export const options = {
  vus:      10,
  duration: '5m',
  thresholds: {
    // Override the shared p95 threshold: PR merge is allowed up to 5000ms
    http_req_duration: ['p(95)<5000'],
    // Retain the shared error-rate threshold
    http_req_failed: thresholds.http_req_failed,
  },
};

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/**
 * Build common JSON request headers, including Bearer auth when a token is set.
 * @returns {Object} headers object
 */
function jsonHeaders() {
  const headers = {
    'Content-Type': 'application/json',
    'Accept':       'application/json',
  };
  if (AUTH_TOKEN) {
    headers['Authorization'] = `Bearer ${AUTH_TOKEN}`;
  }
  return headers;
}

// ---------------------------------------------------------------------------
// Default function — executed once per VU iteration
// ---------------------------------------------------------------------------
export default function () {
  // Use VU id + iteration counter to generate a unique branch name so
  // concurrent VUs and repeated iterations never collide on the same PR.
  // __VU  is the 1-based virtual user number (k6 built-in)
  // __ITER is the 0-based iteration counter per VU (k6 built-in)
  const branchSuffix = `vu${__VU}-iter${__ITER}`;
  const headBranch   = `load-test-head-${branchSuffix}`;
  const basePrPath   = `/api/repos/${OWNER}/${REPO}/pulls`;

  // ------------------------------------------------------------------
  // Step 1: POST /pulls — create a new pull request
  // ------------------------------------------------------------------
  const createPrPayload = JSON.stringify({
    title:      `Load test PR ${branchSuffix}`,
    body:       `Automated load-test pull request (VU ${__VU}, iteration ${__ITER})`,
    head:       headBranch,
    base:       'main',
  });

  const createPrRes = http.post(
    `${BASE_URL}${basePrPath}`,
    createPrPayload,
    {
      headers: jsonHeaders(),
      tags:    { name: 'pr_create' },
    },
  );

  const prCreated = check(createPrRes, {
    'create PR status is 201 or 200': (r) => r.status === 201 || r.status === 200,
    'create PR returns id':           (r) => {
      try {
        return JSON.parse(r.body).id !== undefined;
      } catch (_) {
        return false;
      }
    },
  });

  // If PR creation failed we cannot proceed with review or merge.
  // Skip the remaining steps and let the error-rate threshold catch it.
  if (!prCreated) {
    sleep(1);
    return;
  }

  let prId;
  try {
    prId = JSON.parse(createPrRes.body).id;
  } catch (_) {
    sleep(1);
    return;
  }

  // ------------------------------------------------------------------
  // Step 2: POST /pulls/{id}/review — submit an APPROVE review
  // ------------------------------------------------------------------
  const reviewPayload = JSON.stringify({
    verdict: 'APPROVE',
    body:    'LGTM — load test auto-approval',
  });

  const reviewRes = http.post(
    `${BASE_URL}${basePrPath}/${prId}/review`,
    reviewPayload,
    {
      headers: jsonHeaders(),
      tags:    { name: 'pr_review' },
    },
  );

  const reviewSubmitted = check(reviewRes, {
    'submit review status is 201 or 200': (r) => r.status === 201 || r.status === 200,
  });

  if (!reviewSubmitted) {
    sleep(1);
    return;
  }

  // ------------------------------------------------------------------
  // Step 3: POST /pulls/{id}/merge?strategy=squash — merge the PR
  // ------------------------------------------------------------------
  const mergeRes = http.post(
    `${BASE_URL}${basePrPath}/${prId}/merge?strategy=squash`,
    null,
    {
      headers: jsonHeaders(),
      tags:    { name: 'pr_merge' },
    },
  );

  check(mergeRes, {
    'merge PR responds': (r) =>
      // 200 OK or 204 No Content on success;
      // 422 Unprocessable Entity if not mergeable (conflicts / missing approval);
      // 401/403 if auth is required but token is absent/invalid.
      r.status === 200 || r.status === 204 ||
      r.status === 401 || r.status === 403 || r.status === 422,
  });

  // Brief pause between iterations to avoid thundering-herd on a single VU
  sleep(1);
}
