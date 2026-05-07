import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

/**
 * Wraps routes that require authentication.
 *
 * Behaviour:
 * - While the initial silent-refresh is in progress (`isLoading === true`),
 *   renders a full-screen loading spinner so the user never sees a flash
 *   redirect to /login.
 * - Once the auth check completes, redirects unauthenticated visitors to
 *   /login, preserving the attempted location so the login page can redirect
 *   back after a successful sign-in.
 * - Authenticated users see the nested route content via <Outlet />, or
 *   explicit `children` if provided.
 */

interface ProtectedRouteProps {
  /** Optional children — if omitted, <Outlet /> is rendered instead. */
  children?: React.ReactNode
}

export default function ProtectedRoute({ children }: ProtectedRouteProps) {
  const { isAuthenticated, isLoading } = useAuth()
  const location = useLocation()

  // Still performing the initial silent-refresh — don't redirect yet.
  if (isLoading) {
    return (
      <div
        role="status"
        aria-label="Loading"
        className="flex min-h-screen items-center justify-center"
      >
        <svg
          className="h-8 w-8 animate-spin text-indigo-600"
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
      </div>
    )
  }

  // Auth check complete — redirect unauthenticated users to /login.
  // Preserve the current location so the login page can redirect back.
  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />
  }

  // Authenticated — render nested routes or explicit children.
  return children ? <>{children}</> : <Outlet />
}
