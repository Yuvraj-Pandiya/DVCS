import {
  createContext,
  useContext,
  useState,
  useEffect,
  useCallback,
  ReactNode,
} from 'react'
import { useNavigate } from 'react-router-dom'

// ─── Types ────────────────────────────────────────────────────────────────────

export interface User {
  id: number
  username: string
  email: string
  avatarUrl: string | null
}

export interface LoginCredentials {
  username: string
  password: string
}

export interface AuthContextValue {
  /** JWT access token stored in memory only — never persisted to localStorage */
  accessToken: string | null
  user: User | null
  isAuthenticated: boolean
  /** True while the initial silent-refresh attempt is in progress */
  isLoading: boolean
  login: (credentials: LoginCredentials) => Promise<void>
  logout: () => void
  refresh: () => Promise<void>
}

// ─── Context ──────────────────────────────────────────────────────────────────

const AuthContext = createContext<AuthContextValue | null>(null)

// ─── Provider ─────────────────────────────────────────────────────────────────

interface AuthProviderProps {
  children: ReactNode
}

export function AuthProvider({ children }: AuthProviderProps) {
  const navigate = useNavigate()

  const [accessToken, setAccessToken] = useState<string | null>(null)
  const [user, setUser] = useState<User | null>(null)
  const [isLoading, setIsLoading] = useState(true)

  /**
   * Attempt a silent token refresh on mount.
   * The refresh token lives in an HttpOnly cookie so we send no body —
   * the browser attaches the cookie automatically.
   */
  useEffect(() => {
    let cancelled = false

    async function silentRefresh() {
      try {
        const res = await fetch('/api/auth/refresh', {
          method: 'POST',
          credentials: 'include', // send HttpOnly refresh-token cookie
        })

        if (!cancelled && res.ok) {
          const data = await res.json()
          setAccessToken(data.accessToken)
          setUser(data.user ?? null)
        }
      } catch {
        // No valid refresh token — user is not authenticated; that's fine.
      } finally {
        if (!cancelled) {
          setIsLoading(false)
        }
      }
    }

    silentRefresh()

    return () => {
      cancelled = true
    }
  }, [])

  /**
   * POST /api/auth/login
   * Stores the returned accessToken in React state (memory only).
   * Throws on failure so callers can display error messages.
   */
  const login = useCallback(async (credentials: LoginCredentials): Promise<void> => {
    const res = await fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include', // receive the HttpOnly refresh-token cookie
      body: JSON.stringify(credentials),
    })

    if (!res.ok) {
      const errorBody = await res.json().catch(() => ({}))
      throw new Error(
        (errorBody as { message?: string }).message ?? `Login failed (${res.status})`
      )
    }

    const data = await res.json()
    setAccessToken(data.accessToken)
    setUser(data.user ?? null)
  }, [])

  /**
   * Clears in-memory token and user, then redirects to /login.
   * The HttpOnly refresh-token cookie is cleared server-side on the next
   * request or via a dedicated /api/auth/logout endpoint if one exists.
   */
  const logout = useCallback(() => {
    setAccessToken(null)
    setUser(null)
    navigate('/login')
  }, [navigate])

  /**
   * POST /api/auth/refresh
   * The refresh token is in an HttpOnly cookie — no body required.
   * Updates the in-memory accessToken on success.
   */
  const refresh = useCallback(async (): Promise<void> => {
    const res = await fetch('/api/auth/refresh', {
      method: 'POST',
      credentials: 'include',
    })

    if (!res.ok) {
      // Refresh token is invalid or expired — force logout
      setAccessToken(null)
      setUser(null)
      navigate('/login')
      throw new Error(`Token refresh failed (${res.status})`)
    }

    const data = await res.json()
    setAccessToken(data.accessToken)
    if (data.user) {
      setUser(data.user)
    }
  }, [navigate])

  const value: AuthContextValue = {
    accessToken,
    user,
    isAuthenticated: accessToken !== null,
    isLoading,
    login,
    logout,
    refresh,
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

// ─── Hook ─────────────────────────────────────────────────────────────────────

/**
 * Returns the current auth context.
 * Must be used inside an <AuthProvider>.
 */
export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return ctx
}

export default AuthContext
