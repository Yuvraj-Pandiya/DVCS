/**
 * Shared threshold configuration for k6 load test scenarios.
 *
 * Usage in scenario scripts:
 *   import { thresholds } from './thresholds.js';
 *
 *   export const options = {
 *     thresholds,
 *     // ... other scenario-specific options
 *   };
 */

/**
 * Default thresholds applied to all load test scenarios:
 *   - p(95) of http_req_duration must be below 2000ms
 *   - http_req_failed rate must be below 1%
 */
export const thresholds = {
  http_req_duration: ['p(95)<2000'],
  http_req_failed: ['rate<0.01'],
};
