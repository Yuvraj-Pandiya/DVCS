/**
 * Typed API client with automatic JWT injection and silent token refresh.
 *
 * Usage (inside a React component or hook):
 *   const api = useApiClient()
 *   const data = await api.get<MyType>('/api/repos')
 *
 * Usage (factory, e.g. in a custom hook):
 *   const api = createApiClient({ getToken, refresh, onUnauthorized })
 */

import { useAuth } from '../context/AuthContext'

// ─── Error class ─────────────────────────────────────────────────────────────

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

// ─── Types ────────────────────────────────────────────────────────────────────

export interface ApiClientOptions {
  /** Returns the current access token (or null if unauthenticated). */
  getToken: () => string | null
  /** Attempts a silent token refresh. Should throw on failure. */
  refresh: () => Promise<void>
  /** Called when a second 401 occurs after a refresh attempt. */
  onUnauthorized: () => void
}

export interface ApiClient {
  get<T>(path: string): Promise<T>
  post<T>(path: string, body?: unknown): Promise<T>
  patch<T>(path: string, body?: unknown): Promise<T>
  delete<T>(path: string): Promise<T>
}

// ─── Core implementation ──────────────────────────────────────────────────────

/**
 * Builds the request headers for a given method.
 * Adds `Authorization: Bearer {token}` when a token is available.
 * Adds `Content-Type: application/json` for methods that send a body.
 */
function buildHeaders(
  method: string,
  token: string | null,
): Record<string, string> {
  const headers: Record<string, string> = {}

  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }

  if (method === 'POST' || method === 'PATCH') {
    headers['Content-Type'] = 'application/json'
  }

  return headers
}

/**
 * Parses a response body as JSON, falling back to an empty object on failure.
 * Used to extract error messages from non-ok responses.
 */
async function parseErrorBody(res: Response): Promise<string> {
  try {
    const body = await res.json()
    return (body as { message?: string }).message ?? res.statusText
  } catch {
    return res.statusText
  }
}

/**
 * Factory function that creates a fully configured `ApiClient`.
 *
 * The client:
 * 1. Injects `Authorization: Bearer {token}` on every request.
 * 2. On a 401 response, calls `refresh()` once and retries the request.
 * 3. On a second 401 (after refresh), calls `onUnauthorized()` (redirect to /login).
 * 4. Throws `ApiError` for any non-ok response.
 */
export function createApiClient(options: ApiClientOptions): ApiClient {
  const { getToken, refresh, onUnauthorized } = options

  async function request<T>(
    method: string,
    path: string,
    body?: unknown,
    isRetry = false,
  ): Promise<T> {
    const token = getToken()
    const headers = buildHeaders(method, token)

    const init: RequestInit = {
      method,
      headers,
      credentials: 'include', // send HttpOnly refresh-token cookie when needed
    }

    if (body !== undefined) {
      init.body = JSON.stringify(body)
    }

    const res = await fetch(path, init)

    if (res.status === 401) {
      if (isRetry) {
        // Second 401 — refresh didn't help, redirect to login
        onUnauthorized()
        throw new ApiError(401, 'Session expired. Please log in again.')
      }

      // First 401 — attempt a silent refresh then retry once
      try {
        await refresh()
      } catch {
        // Refresh itself failed — treat as unauthorized
        onUnauthorized()
        throw new ApiError(401, 'Session expired. Please log in again.')
      }

      return request<T>(method, path, body, true)
    }

    if (!res.ok) {
      const message = await parseErrorBody(res)
      throw new ApiError(res.status, message)
    }

    // 204 No Content — return undefined cast to T
    if (res.status === 204) {
      return undefined as unknown as T
    }

    return res.json() as Promise<T>
  }

  return {
    get: <T>(path: string) => request<T>('GET', path),
    post: <T>(path: string, body?: unknown) => request<T>('POST', path, body),
    patch: <T>(path: string, body?: unknown) => request<T>('PATCH', path, body),
    delete: <T>(path: string) => request<T>('DELETE', path),
  }
}

// ─── React hook ───────────────────────────────────────────────────────────────

/**
 * Returns an `ApiClient` configured with the current `AuthContext`.
 *
 * Must be called inside a component tree wrapped by `<AuthProvider>`.
 *
 * The returned client is stable across renders as long as the auth context
 * callbacks are stable (they are memoised with `useCallback` in AuthContext).
 */
export function useApiClient(): ApiClient {
  const { accessToken, refresh, logout } = useAuth()

  // We intentionally recreate the client object on every render so it always
  // closes over the latest `accessToken`. Because `createApiClient` is cheap
  // (no side-effects) this is fine. If performance becomes a concern, wrap
  // with `useMemo` keyed on `accessToken`.
  return createApiClient({
    getToken: () => accessToken,
    refresh,
    onUnauthorized: logout,
  })
}
