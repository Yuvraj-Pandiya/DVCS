import { useState, FormEvent } from 'react'
import { useNavigate, useLocation, Link } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

// ─── Types ────────────────────────────────────────────────────────────────────

interface LocationState {
  from?: Location
}

// ─── LoginPage ────────────────────────────────────────────────────────────────

/**
 * Login page at route /login.
 *
 * - Controlled form with username and password fields
 * - Calls AuthContext.login() on submit
 * - On success, redirects to the `from` location preserved by ProtectedRoute,
 *   or falls back to /
 * - Displays an error message if login throws (e.g. 401 Invalid credentials)
 * - Shows a loading state on the submit button while login is in progress
 * - Links to /register for new users
 */
export default function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  // Recover the location the user was trying to visit before being redirected
  const from = (location.state as LocationState)?.from?.pathname ?? null

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    setError(null)
    setIsSubmitting(true)

    try {
      const loggedInUser = await login({ username, password })
      // Navigate to the page they were trying to reach, or their profile directly
      navigate(from ?? `/${username}`, { replace: true })
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : 'Invalid username or password'
      )
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div className="min-h-screen bg-gray-950 flex items-center justify-center px-4">
      <div className="w-full max-w-sm">
        {/* Logo / heading */}
        <div className="mb-8 text-center">
          <Link
            to="/"
            className="inline-flex items-center gap-2 text-white font-bold text-2xl hover:text-indigo-400 transition-colors"
            aria-label="DVCS home"
          >
            <svg
              xmlns="http://www.w3.org/2000/svg"
              viewBox="0 0 24 24"
              fill="currentColor"
              className="w-8 h-8 text-indigo-400"
              aria-hidden="true"
            >
              <path d="M12 2C6.477 2 2 6.477 2 12s4.477 10 10 10 10-4.477 10-10S17.523 2 12 2zm-1 14.5v-5l-3 3-1.5-1.5 4.5-4.5 4.5 4.5-1.5 1.5-3-3v5h-1z" />
            </svg>
            <span>DVCS</span>
          </Link>
          <h1 className="mt-4 text-xl font-semibold text-gray-100">
            Sign in to your account
          </h1>
        </div>

        {/* Card */}
        <div className="bg-gray-900 border border-gray-700 rounded-lg p-6 shadow-xl">
          {/* Error banner */}
          {error && (
            <div
              role="alert"
              aria-live="assertive"
              className="mb-4 flex items-start gap-2 rounded-md bg-red-900/40 border border-red-700 px-4 py-3 text-sm text-red-300"
            >
              <svg
                xmlns="http://www.w3.org/2000/svg"
                viewBox="0 0 20 20"
                fill="currentColor"
                className="mt-0.5 h-4 w-4 flex-shrink-0 text-red-400"
                aria-hidden="true"
              >
                <path
                  fillRule="evenodd"
                  d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.28 7.22a.75.75 0 00-1.06 1.06L8.94 10l-1.72 1.72a.75.75 0 101.06 1.06L10 11.06l1.72 1.72a.75.75 0 101.06-1.06L11.06 10l1.72-1.72a.75.75 0 00-1.06-1.06L10 8.94 8.28 7.22z"
                  clipRule="evenodd"
                />
              </svg>
              <span>{error}</span>
            </div>
          )}

          <form onSubmit={handleSubmit} noValidate aria-label="Login form">
            {/* Username */}
            <div className="mb-4">
              <label
                htmlFor="username"
                className="block mb-1.5 text-sm font-medium text-gray-300"
              >
                Username
              </label>
              <input
                id="username"
                type="text"
                name="username"
                autoComplete="username"
                required
                aria-required="true"
                aria-describedby={error ? 'login-error' : undefined}
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                disabled={isSubmitting}
                placeholder="your-username"
                className="w-full rounded-md bg-gray-800 border border-gray-600 px-3 py-2 text-sm text-gray-100 placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent disabled:opacity-50 disabled:cursor-not-allowed transition"
              />
            </div>

            {/* Password */}
            <div className="mb-6">
              <label
                htmlFor="password"
                className="block mb-1.5 text-sm font-medium text-gray-300"
              >
                Password
              </label>
              <input
                id="password"
                type="password"
                name="password"
                autoComplete="current-password"
                required
                aria-required="true"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                disabled={isSubmitting}
                placeholder="••••••••"
                className="w-full rounded-md bg-gray-800 border border-gray-600 px-3 py-2 text-sm text-gray-100 placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent disabled:opacity-50 disabled:cursor-not-allowed transition"
              />
            </div>

            {/* Submit */}
            <button
              type="submit"
              disabled={isSubmitting || !username.trim() || !password}
              aria-busy={isSubmitting}
              className="w-full flex items-center justify-center gap-2 rounded-md bg-indigo-600 px-4 py-2 text-sm font-semibold text-white hover:bg-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-400 focus:ring-offset-2 focus:ring-offset-gray-900 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            >
              {isSubmitting ? (
                <>
                  <svg
                    className="h-4 w-4 animate-spin"
                    xmlns="http://www.w3.org/2000/svg"
                    fill="none"
                    viewBox="0 0 24 24"
                    aria-hidden="true"
                  >
                    <circle
                      className="opacity-25"
                      cx="12"
                      cy="12"
                      r="10"
                      stroke="currentColor"
                      strokeWidth="4"
                    />
                    <path
                      className="opacity-75"
                      fill="currentColor"
                      d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"
                    />
                  </svg>
                  Signing in…
                </>
              ) : (
                'Sign in'
              )}
            </button>
          </form>
        </div>

        {/* Register link */}
        <p className="mt-4 text-center text-sm text-gray-400">
          Don&apos;t have an account?{' '}
          <Link
            to="/register"
            className="font-medium text-indigo-400 hover:text-indigo-300 transition-colors"
          >
            Create one
          </Link>
        </p>
      </div>
    </div>
  )
}
