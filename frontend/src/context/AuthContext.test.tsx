/**
 * AuthContext.test.tsx
 *
 * Tests for AuthContext covering:
 * - login() stores access token and user in context state
 * - login() throws on failure
 * - logout() clears token and user, then redirects to /login
 * - refresh() on success calls /api/auth/refresh and updates token
 * - refresh() on failure (401) clears state and redirects to /login
 * - useAuth() throws when used outside AuthProvider
 * - isAuthenticated reflects accessToken presence
 * - silent refresh on mount (isLoading lifecycle)
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, act, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { AuthProvider, useAuth } from './AuthContext'
import type { LoginCredentials } from './AuthContext'

// ─── Helpers ──────────────────────────────────────────────────────────────────

/**
 * Wraps AuthProvider inside a MemoryRouter so useNavigate works.
 * Renders a consumer component that exposes auth actions via data-testid buttons.
 */
function renderAuthProvider(initialPath = '/') {
  // A simple consumer that renders auth state and exposes action buttons
  function Consumer() {
    const { accessToken, user, isAuthenticated, isLoading, login, logout, refresh } = useAuth()

    const handleLogin = async () => {
      const creds: LoginCredentials = { username: 'alice', password: 'secret' }
      await login(creds)
    }

    const handleRefresh = async () => {
      await refresh().catch(() => {})
    }

    return (
      <div>
        <span data-testid="token">{accessToken ?? 'null'}</span>
        <span data-testid="username">{user?.username ?? 'null'}</span>
        <span data-testid="authenticated">{String(isAuthenticated)}</span>
        <span data-testid="loading">{String(isLoading)}</span>
        <button onClick={handleLogin}>login</button>
        <button onClick={logout}>logout</button>
        <button onClick={handleRefresh}>refresh</button>
      </div>
    )
  }

  const utils = render(
    <MemoryRouter initialEntries={[initialPath]}>
      <Routes>
        <Route
          path="*"
          element={
            <AuthProvider>
              <Consumer />
            </AuthProvider>
          }
        />
        <Route path="/login" element={<div data-testid="login-page">Login Page</div>} />
      </Routes>
    </MemoryRouter>
  )

  return utils
}

// ─── fetch mock helpers ───────────────────────────────────────────────────────

/** Build a mock Response that resolves to the given JSON body. */
function mockJsonResponse(body: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: () => Promise.resolve(body),
  } as unknown as Response
}

/** Build a mock Response that rejects json() parsing (network-level error). */
function mockNetworkError(): Promise<Response> {
  return Promise.reject(new Error('Network error'))
}

// ─── Tests ────────────────────────────────────────────────────────────────────

describe('AuthContext', () => {
  let fetchSpy: ReturnType<typeof vi.spyOn>

  beforeEach(() => {
    fetchSpy = vi.spyOn(globalThis, 'fetch')
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  // ── Mount / silent refresh ──────────────────────────────────────────────────

  describe('on mount (silent refresh)', () => {
    it('sets isLoading=true initially and false after the silent refresh resolves', async () => {
      // Silent refresh fails (no cookie) — that's the normal unauthenticated case
      fetchSpy.mockResolvedValueOnce(mockJsonResponse({}, 401))

      renderAuthProvider()

      // isLoading starts true; after the effect resolves it becomes false
      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('false')
      })
    })

    it('stores token and user when the silent refresh succeeds', async () => {
      fetchSpy.mockResolvedValueOnce(
        mockJsonResponse({ accessToken: 'silent-token', user: { id: 1, username: 'alice', email: 'a@b.com', avatarUrl: null } })
      )

      renderAuthProvider()

      await waitFor(() => {
        expect(screen.getByTestId('token')).toHaveTextContent('silent-token')
        expect(screen.getByTestId('username')).toHaveTextContent('alice')
        expect(screen.getByTestId('loading')).toHaveTextContent('false')
      })
    })

    it('leaves token null when the silent refresh fails', async () => {
      fetchSpy.mockResolvedValueOnce(mockJsonResponse({}, 401))

      renderAuthProvider()

      await waitFor(() => {
        expect(screen.getByTestId('token')).toHaveTextContent('null')
        expect(screen.getByTestId('loading')).toHaveTextContent('false')
      })
    })

    it('leaves token null when the silent refresh throws a network error', async () => {
      fetchSpy.mockImplementationOnce(() => mockNetworkError())

      renderAuthProvider()

      await waitFor(() => {
        expect(screen.getByTestId('token')).toHaveTextContent('null')
        expect(screen.getByTestId('loading')).toHaveTextContent('false')
      })
    })
  })

  // ── login() ─────────────────────────────────────────────────────────────────

  describe('login()', () => {
    it('stores the access token in context state after a successful login', async () => {
      const user = userEvent.setup()

      // First call: silent refresh on mount (fails — unauthenticated)
      fetchSpy.mockResolvedValueOnce(mockJsonResponse({}, 401))
      // Second call: login
      fetchSpy.mockResolvedValueOnce(
        mockJsonResponse({
          accessToken: 'access-token-abc',
          user: { id: 1, username: 'alice', email: 'alice@example.com', avatarUrl: null },
        })
      )

      renderAuthProvider()

      // Wait for mount refresh to finish
      await waitFor(() => expect(screen.getByTestId('loading')).toHaveTextContent('false'))

      await user.click(screen.getByRole('button', { name: 'login' }))

      await waitFor(() => {
        expect(screen.getByTestId('token')).toHaveTextContent('access-token-abc')
      })
    })

    it('stores the user object in context state after a successful login', async () => {
      const user = userEvent.setup()

      fetchSpy.mockResolvedValueOnce(mockJsonResponse({}, 401))
      fetchSpy.mockResolvedValueOnce(
        mockJsonResponse({
          accessToken: 'tok',
          user: { id: 42, username: 'bob', email: 'bob@example.com', avatarUrl: 'https://example.com/avatar.png' },
        })
      )

      renderAuthProvider()
      await waitFor(() => expect(screen.getByTestId('loading')).toHaveTextContent('false'))

      await user.click(screen.getByRole('button', { name: 'login' }))

      await waitFor(() => {
        expect(screen.getByTestId('username')).toHaveTextContent('bob')
      })
    })

    it('sets isAuthenticated=true after a successful login', async () => {
      const user = userEvent.setup()

      fetchSpy.mockResolvedValueOnce(mockJsonResponse({}, 401))
      fetchSpy.mockResolvedValueOnce(
        mockJsonResponse({ accessToken: 'tok', user: { id: 1, username: 'alice', email: 'a@b.com', avatarUrl: null } })
      )

      renderAuthProvider()
      await waitFor(() => expect(screen.getByTestId('loading')).toHaveTextContent('false'))

      await user.click(screen.getByRole('button', { name: 'login' }))

      await waitFor(() => {
        expect(screen.getByTestId('authenticated')).toHaveTextContent('true')
      })
    })

    it('POSTs to /api/auth/login with the provided credentials', async () => {
      const user = userEvent.setup()

      fetchSpy.mockResolvedValueOnce(mockJsonResponse({}, 401))
      fetchSpy.mockResolvedValueOnce(
        mockJsonResponse({ accessToken: 'tok', user: { id: 1, username: 'alice', email: 'a@b.com', avatarUrl: null } })
      )

      renderAuthProvider()
      await waitFor(() => expect(screen.getByTestId('loading')).toHaveTextContent('false'))

      await user.click(screen.getByRole('button', { name: 'login' }))

      await waitFor(() => {
        const loginCall = fetchSpy.mock.calls.find(([url]) => String(url).includes('/api/auth/login'))
        expect(loginCall).toBeDefined()
        expect(loginCall![1]).toMatchObject({
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          credentials: 'include',
        })
        const body = JSON.parse(loginCall![1]!.body as string)
        expect(body).toEqual({ username: 'alice', password: 'secret' })
      })
    })

    it('throws an error when the login request fails', async () => {
      // We need a component that can capture the thrown error
      let caughtError: Error | null = null

      function ErrorCapturingConsumer() {
        const { login } = useAuth()

        const handleLogin = async () => {
          try {
            await login({ username: 'alice', password: 'wrong' })
          } catch (e) {
            caughtError = e as Error
          }
        }

        return <button onClick={handleLogin}>login</button>
      }

      fetchSpy.mockResolvedValueOnce(mockJsonResponse({}, 401)) // silent refresh
      fetchSpy.mockResolvedValueOnce(
        mockJsonResponse({ message: 'Invalid credentials' }, 401)
      )

      const user = userEvent.setup()

      render(
        <MemoryRouter>
          <AuthProvider>
            <ErrorCapturingConsumer />
          </AuthProvider>
        </MemoryRouter>
      )

      await user.click(screen.getByRole('button', { name: 'login' }))

      await waitFor(() => {
        expect(caughtError).not.toBeNull()
        expect(caughtError!.message).toContain('Invalid credentials')
      })
    })
  })

  // ── logout() ────────────────────────────────────────────────────────────────

  describe('logout()', () => {
    /**
     * For logout tests that check state clearing, we place AuthProvider above
     * the Routes so it stays mounted after navigation. A persistent status bar
     * outside the Routes always shows the current auth state.
     */
    function renderWithPersistentStatus() {
      function StatusBar() {
        const auth = useAuth()
        return (
          <div data-testid="status-bar">
            <span data-testid="token">{auth.accessToken ?? 'null'}</span>
            <span data-testid="username">{auth.user?.username ?? 'null'}</span>
            <span data-testid="authenticated">{String(auth.isAuthenticated)}</span>
          </div>
        )
      }

      function LogoutButton() {
        const { logout } = useAuth()
        return <button onClick={logout}>logout</button>
      }

      render(
        <MemoryRouter initialEntries={['/app']}>
          <AuthProvider>
            {/* StatusBar is always mounted — outside Routes */}
            <StatusBar />
            <Routes>
              <Route path="/app" element={<LogoutButton />} />
              <Route path="/login" element={<div data-testid="login-page">Login Page</div>} />
            </Routes>
          </AuthProvider>
        </MemoryRouter>
      )
    }

    it('clears the access token from context state', async () => {
      const user = userEvent.setup()

      fetchSpy.mockResolvedValueOnce(
        mockJsonResponse({
          accessToken: 'existing-token',
          user: { id: 1, username: 'alice', email: 'a@b.com', avatarUrl: null },
        })
      )

      renderWithPersistentStatus()
      await waitFor(() => expect(screen.getByTestId('token')).toHaveTextContent('existing-token'))

      await user.click(screen.getByRole('button', { name: 'logout' }))

      await waitFor(() => {
        expect(screen.getByTestId('token')).toHaveTextContent('null')
      })
    })

    it('clears the user from context state', async () => {
      const user = userEvent.setup()

      fetchSpy.mockResolvedValueOnce(
        mockJsonResponse({
          accessToken: 'tok',
          user: { id: 1, username: 'alice', email: 'a@b.com', avatarUrl: null },
        })
      )

      renderWithPersistentStatus()
      await waitFor(() => expect(screen.getByTestId('username')).toHaveTextContent('alice'))

      await user.click(screen.getByRole('button', { name: 'logout' }))

      await waitFor(() => {
        expect(screen.getByTestId('username')).toHaveTextContent('null')
      })
    })

    it('sets isAuthenticated=false after logout', async () => {
      const user = userEvent.setup()

      fetchSpy.mockResolvedValueOnce(
        mockJsonResponse({
          accessToken: 'tok',
          user: { id: 1, username: 'alice', email: 'a@b.com', avatarUrl: null },
        })
      )

      renderWithPersistentStatus()
      await waitFor(() => expect(screen.getByTestId('authenticated')).toHaveTextContent('true'))

      await user.click(screen.getByRole('button', { name: 'logout' }))

      await waitFor(() => {
        expect(screen.getByTestId('authenticated')).toHaveTextContent('false')
      })
    })

    it('redirects to /login after logout', async () => {
      const user = userEvent.setup()

      fetchSpy.mockResolvedValueOnce(
        mockJsonResponse({
          accessToken: 'tok',
          user: { id: 1, username: 'alice', email: 'a@b.com', avatarUrl: null },
        })
      )

      // Render with a /login route so we can detect the redirect
      function Consumer() {
        const { logout } = useAuth()
        return <button onClick={logout}>logout</button>
      }

      render(
        <MemoryRouter initialEntries={['/dashboard']}>
          <Routes>
            <Route
              path="/dashboard"
              element={
                <AuthProvider>
                  <Consumer />
                </AuthProvider>
              }
            />
            <Route path="/login" element={<div data-testid="login-page">Login Page</div>} />
          </Routes>
        </MemoryRouter>
      )

      await waitFor(() => expect(screen.getByRole('button', { name: 'logout' })).toBeInTheDocument())

      await user.click(screen.getByRole('button', { name: 'logout' }))

      await waitFor(() => {
        expect(screen.getByTestId('login-page')).toBeInTheDocument()
      })
    })
  })

  // ── refresh() ───────────────────────────────────────────────────────────────

  describe('refresh()', () => {
    it('calls POST /api/auth/refresh', async () => {
      const user = userEvent.setup()

      fetchSpy.mockResolvedValueOnce(mockJsonResponse({}, 401)) // silent refresh on mount
      fetchSpy.mockResolvedValueOnce(
        mockJsonResponse({ accessToken: 'refreshed-token' })
      )

      renderAuthProvider()
      await waitFor(() => expect(screen.getByTestId('loading')).toHaveTextContent('false'))

      await user.click(screen.getByRole('button', { name: 'refresh' }))

      await waitFor(() => {
        const refreshCall = fetchSpy.mock.calls.find(([url]) => String(url).includes('/api/auth/refresh'))
        expect(refreshCall).toBeDefined()
        expect(refreshCall![1]).toMatchObject({
          method: 'POST',
          credentials: 'include',
        })
      })
    })

    it('updates the access token in context state on a successful refresh', async () => {
      const user = userEvent.setup()

      fetchSpy.mockResolvedValueOnce(mockJsonResponse({}, 401)) // silent refresh on mount
      fetchSpy.mockResolvedValueOnce(
        mockJsonResponse({ accessToken: 'new-access-token' })
      )

      renderAuthProvider()
      await waitFor(() => expect(screen.getByTestId('loading')).toHaveTextContent('false'))

      await user.click(screen.getByRole('button', { name: 'refresh' }))

      await waitFor(() => {
        expect(screen.getByTestId('token')).toHaveTextContent('new-access-token')
      })
    })

    it('updates the user in context state when the refresh response includes a user', async () => {
      const user = userEvent.setup()

      fetchSpy.mockResolvedValueOnce(mockJsonResponse({}, 401))
      fetchSpy.mockResolvedValueOnce(
        mockJsonResponse({
          accessToken: 'new-tok',
          user: { id: 5, username: 'carol', email: 'carol@example.com', avatarUrl: null },
        })
      )

      renderAuthProvider()
      await waitFor(() => expect(screen.getByTestId('loading')).toHaveTextContent('false'))

      await user.click(screen.getByRole('button', { name: 'refresh' }))

      await waitFor(() => {
        expect(screen.getByTestId('username')).toHaveTextContent('carol')
      })
    })

    it('clears token and user when the refresh response is 401', async () => {
      const user = userEvent.setup()

      function StatusBar() {
        const auth = useAuth()
        return (
          <div data-testid="status-bar">
            <span data-testid="token">{auth.accessToken ?? 'null'}</span>
            <span data-testid="username">{auth.user?.username ?? 'null'}</span>
          </div>
        )
      }

      function RefreshButton() {
        const { refresh } = useAuth()
        return <button onClick={() => refresh().catch(() => {})}>refresh</button>
      }

      // Mount: silent refresh succeeds → user is logged in
      fetchSpy.mockResolvedValueOnce(
        mockJsonResponse({
          accessToken: 'old-token',
          user: { id: 1, username: 'alice', email: 'a@b.com', avatarUrl: null },
        })
      )
      // Explicit refresh call: 401
      fetchSpy.mockResolvedValueOnce(mockJsonResponse({ message: 'Refresh token expired' }, 401))

      render(
        <MemoryRouter initialEntries={['/app']}>
          <AuthProvider>
            {/* StatusBar is always mounted — outside Routes */}
            <StatusBar />
            <Routes>
              <Route path="/app" element={<RefreshButton />} />
              <Route path="/login" element={<div data-testid="login-page">Login Page</div>} />
            </Routes>
          </AuthProvider>
        </MemoryRouter>
      )

      await waitFor(() => expect(screen.getByTestId('token')).toHaveTextContent('old-token'))

      await user.click(screen.getByRole('button', { name: 'refresh' }))

      // After failed refresh, state is cleared and navigate('/login') is called
      await waitFor(() => {
        expect(screen.getByTestId('token')).toHaveTextContent('null')
        expect(screen.getByTestId('username')).toHaveTextContent('null')
      })
    })

    it('redirects to /login when the refresh response is not ok', async () => {
      const user = userEvent.setup()

      fetchSpy.mockResolvedValueOnce(
        mockJsonResponse({
          accessToken: 'old-token',
          user: { id: 1, username: 'alice', email: 'a@b.com', avatarUrl: null },
        })
      )
      fetchSpy.mockResolvedValueOnce(mockJsonResponse({}, 401))

      function Consumer() {
        const { refresh } = useAuth()
        return <button onClick={() => refresh().catch(() => {})}>refresh</button>
      }

      render(
        <MemoryRouter initialEntries={['/dashboard']}>
          <Routes>
            <Route
              path="/dashboard"
              element={
                <AuthProvider>
                  <Consumer />
                </AuthProvider>
              }
            />
            <Route path="/login" element={<div data-testid="login-page">Login Page</div>} />
          </Routes>
        </MemoryRouter>
      )

      await waitFor(() => expect(screen.getByRole('button', { name: 'refresh' })).toBeInTheDocument())

      await user.click(screen.getByRole('button', { name: 'refresh' }))

      await waitFor(() => {
        expect(screen.getByTestId('login-page')).toBeInTheDocument()
      })
    })

    it('throws an error when the refresh fails', async () => {
      let caughtError: Error | null = null

      function ErrorCapturingConsumer() {
        const { refresh } = useAuth()

        const handleRefresh = async () => {
          try {
            await refresh()
          } catch (e) {
            caughtError = e as Error
          }
        }

        return <button onClick={handleRefresh}>refresh</button>
      }

      fetchSpy.mockResolvedValueOnce(mockJsonResponse({}, 401)) // silent refresh on mount
      fetchSpy.mockResolvedValueOnce(mockJsonResponse({}, 401)) // explicit refresh call

      const user = userEvent.setup()

      render(
        <MemoryRouter>
          <AuthProvider>
            <ErrorCapturingConsumer />
          </AuthProvider>
        </MemoryRouter>
      )

      await user.click(screen.getByRole('button', { name: 'refresh' }))

      await waitFor(() => {
        expect(caughtError).not.toBeNull()
        expect(caughtError!.message).toMatch(/token refresh failed/i)
      })
    })
  })

  // ── useAuth() hook ──────────────────────────────────────────────────────────

  describe('useAuth()', () => {
    it('throws when used outside an AuthProvider', () => {
      // Suppress the expected React error boundary console output
      const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {})

      function BadConsumer() {
        useAuth()
        return null
      }

      expect(() => {
        render(
          <MemoryRouter>
            <BadConsumer />
          </MemoryRouter>
        )
      }).toThrow('useAuth must be used within an AuthProvider')

      consoleError.mockRestore()
    })
  })

  // ── isAuthenticated ─────────────────────────────────────────────────────────

  describe('isAuthenticated', () => {
    it('is false when there is no access token', async () => {
      fetchSpy.mockResolvedValueOnce(mockJsonResponse({}, 401))

      renderAuthProvider()

      await waitFor(() => expect(screen.getByTestId('loading')).toHaveTextContent('false'))

      expect(screen.getByTestId('authenticated')).toHaveTextContent('false')
    })

    it('is true when an access token is present', async () => {
      fetchSpy.mockResolvedValueOnce(
        mockJsonResponse({
          accessToken: 'tok',
          user: { id: 1, username: 'alice', email: 'a@b.com', avatarUrl: null },
        })
      )

      renderAuthProvider()

      await waitFor(() => {
        expect(screen.getByTestId('authenticated')).toHaveTextContent('true')
      })
    })
  })
})
