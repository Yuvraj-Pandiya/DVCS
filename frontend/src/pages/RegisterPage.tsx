import { useState, FormEvent } from 'react'
import { useNavigate, Link } from 'react-router-dom'

// ─── Types ────────────────────────────────────────────────────────────────────

interface FieldErrors {
  username?: string
  email?: string
  password?: string
  confirmPassword?: string
}

interface ErrorEnvelope {
  error: string
  message: string
  details?: Record<string, string>
  timestamp?: string
}

// ─── RegisterPage ─────────────────────────────────────────────────────────────

/**
 * Registration page at route /register.
 *
 * - Controlled form with username, email, password, confirm-password fields
 * - Client-side validation: passwords must match before submitting
 * - Submits POST /api/auth/register with { username, email, password }
 * - On 201 success: redirects to /login with a success message in state
 * - On 400 response: parses field-level errors from ErrorEnvelope.details
 *   and displays them under each field
 * - On 409 conflict: displays "Username or email already taken"
 * - Shows loading state on submit button
 * - Links to /login for existing users
 */
export default function RegisterPage() {
  const navigate = useNavigate()

  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')

  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
  const [globalError, setGlobalError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  // ── Client-side validation ──────────────────────────────────────────────────

  function validate(): FieldErrors {
    const errors: FieldErrors = {}
    if (!username.trim()) {
      errors.username = 'Username is required'
    }
    if (!email.trim()) {
      errors.email = 'Email is required'
    }
    if (!password) {
      errors.password = 'Password is required'
    }
    if (!confirmPassword) {
      errors.confirmPassword = 'Please confirm your password'
    } else if (password && confirmPassword && password !== confirmPassword) {
      errors.confirmPassword = 'Passwords do not match'
    }
    return errors
  }

  // ── Submit handler ──────────────────────────────────────────────────────────

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    setGlobalError(null)

    // Client-side validation first
    const clientErrors = validate()
    if (Object.keys(clientErrors).length > 0) {
      setFieldErrors(clientErrors)
      return
    }
    setFieldErrors({})

    setIsSubmitting(true)
    try {
      const response = await fetch('/api/auth/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: username.trim(), email: email.trim(), password }),
      })

      if (response.status === 201) {
        navigate('/login', {
          replace: true,
          state: { successMessage: 'Account created! Please sign in.' },
        })
        return
      }

      // Parse error envelope
      let envelope: ErrorEnvelope | null = null
      try {
        envelope = (await response.json()) as ErrorEnvelope
      } catch {
        // non-JSON body — fall through to generic error
      }

      if (response.status === 400 && envelope?.details) {
        // Map backend field names to our local field state
        const serverErrors: FieldErrors = {}
        const d = envelope.details
        if (d.username) serverErrors.username = d.username
        if (d.email) serverErrors.email = d.email
        if (d.password) serverErrors.password = d.password
        setFieldErrors(serverErrors)
        return
      }

      if (response.status === 409) {
        setGlobalError('Username or email already taken')
        return
      }

      // Generic fallback
      setGlobalError(
        envelope?.message ?? `Registration failed (HTTP ${response.status})`
      )
    } catch {
      setGlobalError('Network error — please try again')
    } finally {
      setIsSubmitting(false)
    }
  }

  // ── Helpers ─────────────────────────────────────────────────────────────────

  const isFormEmpty =
    !username.trim() || !email.trim() || !password || !confirmPassword

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
            Create your account
          </h1>
        </div>

        {/* Card */}
        <div className="bg-gray-900 border border-gray-700 rounded-lg p-6 shadow-xl">
          {/* Global error banner */}
          {globalError && (
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
              <span>{globalError}</span>
            </div>
          )}

          <form onSubmit={handleSubmit} noValidate aria-label="Registration form">
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
                aria-describedby={fieldErrors.username ? 'username-error' : undefined}
                aria-invalid={!!fieldErrors.username}
                value={username}
                onChange={(e) => {
                  setUsername(e.target.value)
                  if (fieldErrors.username) {
                    setFieldErrors((prev) => ({ ...prev, username: undefined }))
                  }
                }}
                disabled={isSubmitting}
                placeholder="your-username"
                className={`w-full rounded-md bg-gray-800 border px-3 py-2 text-sm text-gray-100 placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent disabled:opacity-50 disabled:cursor-not-allowed transition ${
                  fieldErrors.username
                    ? 'border-red-500 focus:ring-red-500'
                    : 'border-gray-600'
                }`}
              />
              {fieldErrors.username && (
                <p
                  id="username-error"
                  role="alert"
                  className="mt-1.5 text-xs text-red-400"
                >
                  {fieldErrors.username}
                </p>
              )}
            </div>

            {/* Email */}
            <div className="mb-4">
              <label
                htmlFor="email"
                className="block mb-1.5 text-sm font-medium text-gray-300"
              >
                Email address
              </label>
              <input
                id="email"
                type="email"
                name="email"
                autoComplete="email"
                required
                aria-required="true"
                aria-describedby={fieldErrors.email ? 'email-error' : undefined}
                aria-invalid={!!fieldErrors.email}
                value={email}
                onChange={(e) => {
                  setEmail(e.target.value)
                  if (fieldErrors.email) {
                    setFieldErrors((prev) => ({ ...prev, email: undefined }))
                  }
                }}
                disabled={isSubmitting}
                placeholder="you@example.com"
                className={`w-full rounded-md bg-gray-800 border px-3 py-2 text-sm text-gray-100 placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent disabled:opacity-50 disabled:cursor-not-allowed transition ${
                  fieldErrors.email
                    ? 'border-red-500 focus:ring-red-500'
                    : 'border-gray-600'
                }`}
              />
              {fieldErrors.email && (
                <p
                  id="email-error"
                  role="alert"
                  className="mt-1.5 text-xs text-red-400"
                >
                  {fieldErrors.email}
                </p>
              )}
            </div>

            {/* Password */}
            <div className="mb-4">
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
                autoComplete="new-password"
                required
                aria-required="true"
                aria-describedby={fieldErrors.password ? 'password-error' : undefined}
                aria-invalid={!!fieldErrors.password}
                value={password}
                onChange={(e) => {
                  setPassword(e.target.value)
                  if (fieldErrors.password) {
                    setFieldErrors((prev) => ({ ...prev, password: undefined }))
                  }
                }}
                disabled={isSubmitting}
                placeholder="••••••••"
                className={`w-full rounded-md bg-gray-800 border px-3 py-2 text-sm text-gray-100 placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent disabled:opacity-50 disabled:cursor-not-allowed transition ${
                  fieldErrors.password
                    ? 'border-red-500 focus:ring-red-500'
                    : 'border-gray-600'
                }`}
              />
              {fieldErrors.password && (
                <p
                  id="password-error"
                  role="alert"
                  className="mt-1.5 text-xs text-red-400"
                >
                  {fieldErrors.password}
                </p>
              )}
            </div>

            {/* Confirm Password */}
            <div className="mb-6">
              <label
                htmlFor="confirmPassword"
                className="block mb-1.5 text-sm font-medium text-gray-300"
              >
                Confirm password
              </label>
              <input
                id="confirmPassword"
                type="password"
                name="confirmPassword"
                autoComplete="new-password"
                required
                aria-required="true"
                aria-describedby={
                  fieldErrors.confirmPassword ? 'confirmPassword-error' : undefined
                }
                aria-invalid={!!fieldErrors.confirmPassword}
                value={confirmPassword}
                onChange={(e) => {
                  setConfirmPassword(e.target.value)
                  if (fieldErrors.confirmPassword) {
                    setFieldErrors((prev) => ({
                      ...prev,
                      confirmPassword: undefined,
                    }))
                  }
                }}
                disabled={isSubmitting}
                placeholder="••••••••"
                className={`w-full rounded-md bg-gray-800 border px-3 py-2 text-sm text-gray-100 placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent disabled:opacity-50 disabled:cursor-not-allowed transition ${
                  fieldErrors.confirmPassword
                    ? 'border-red-500 focus:ring-red-500'
                    : 'border-gray-600'
                }`}
              />
              {fieldErrors.confirmPassword && (
                <p
                  id="confirmPassword-error"
                  role="alert"
                  className="mt-1.5 text-xs text-red-400"
                >
                  {fieldErrors.confirmPassword}
                </p>
              )}
            </div>

            {/* Submit */}
            <button
              type="submit"
              disabled={isSubmitting || isFormEmpty}
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
                  Creating account…
                </>
              ) : (
                'Create account'
              )}
            </button>
          </form>
        </div>

        {/* Login link */}
        <p className="mt-4 text-center text-sm text-gray-400">
          Already have an account?{' '}
          <Link
            to="/login"
            className="font-medium text-indigo-400 hover:text-indigo-300 transition-colors"
          >
            Sign in
          </Link>
        </p>
      </div>
    </div>
  )
}
