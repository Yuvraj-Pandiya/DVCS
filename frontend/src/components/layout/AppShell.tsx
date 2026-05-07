import { useState, useRef, useEffect, FormEvent } from 'react'
import {
  Outlet,
  Link,
  NavLink,
  useLocation,
  useNavigate,
} from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'
import NotificationBell from '../NotificationBell'

// ─── Types ────────────────────────────────────────────────────────────────────

interface RepoNavParams {
  owner?: string
  repo?: string
}

// ─── User Avatar Dropdown ─────────────────────────────────────────────────────

function UserMenu() {
  const { user, logout } = useAuth()
  const [open, setOpen] = useState(false)
  const menuRef = useRef<HTMLDivElement>(null)

  // Close on outside click
  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        setOpen(false)
      }
    }
    if (open) {
      document.addEventListener('mousedown', handleClickOutside)
    }
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [open])

  if (!user) return null

  const initial = user.username.charAt(0).toUpperCase()

  return (
    <div className="relative" ref={menuRef}>
      <button
        type="button"
        onClick={() => setOpen((prev) => !prev)}
        aria-haspopup="true"
        aria-expanded={open}
        aria-label={`User menu for ${user.username}`}
        className="flex items-center justify-center w-8 h-8 rounded-full bg-indigo-600 text-white text-sm font-semibold hover:bg-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-400 transition-colors"
      >
        {initial}
      </button>

      {open && (
        <div
          role="menu"
          className="absolute right-0 mt-2 w-48 rounded-md shadow-lg bg-gray-800 border border-gray-700 py-1 z-50"
        >
          <div className="px-4 py-2 text-xs text-gray-400 border-b border-gray-700">
            Signed in as{' '}
            <span className="font-semibold text-gray-200">{user.username}</span>
          </div>

          <Link
            to={`/${user.username}`}
            role="menuitem"
            onClick={() => setOpen(false)}
            className="block px-4 py-2 text-sm text-gray-300 hover:bg-gray-700 hover:text-white transition-colors"
          >
            Profile
          </Link>

          <Link
            to="/settings"
            role="menuitem"
            onClick={() => setOpen(false)}
            className="block px-4 py-2 text-sm text-gray-300 hover:bg-gray-700 hover:text-white transition-colors"
          >
            Settings
          </Link>

          <div className="border-t border-gray-700 mt-1 pt-1">
            <button
              type="button"
              role="menuitem"
              onClick={() => {
                setOpen(false)
                logout()
              }}
              className="w-full text-left px-4 py-2 text-sm text-red-400 hover:bg-gray-700 hover:text-red-300 transition-colors"
            >
              Logout
            </button>
          </div>
        </div>
      )}
    </div>
  )
}

// ─── Top Navigation Bar ───────────────────────────────────────────────────────

function TopNav() {
  const { isAuthenticated, isLoading } = useAuth()
  const navigate = useNavigate()
  const [searchQuery, setSearchQuery] = useState('')

  function handleSearchSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    const q = searchQuery.trim()
    if (q) {
      navigate(`/explore?q=${encodeURIComponent(q)}`)
    }
  }

  return (
    <header className="fixed top-0 left-0 right-0 z-40 h-14 bg-gray-900 border-b border-gray-700 flex items-center px-4 gap-4">
      {/* Left: Logo */}
      <Link
        to="/"
        className="flex-shrink-0 flex items-center gap-2 text-white font-bold text-lg hover:text-indigo-400 transition-colors"
        aria-label="DVCS home"
      >
        <svg
          xmlns="http://www.w3.org/2000/svg"
          viewBox="0 0 24 24"
          fill="currentColor"
          className="w-6 h-6 text-indigo-400"
          aria-hidden="true"
        >
          <path d="M12 2C6.477 2 2 6.477 2 12s4.477 10 10 10 10-4.477 10-10S17.523 2 12 2zm-1 14.5v-5l-3 3-1.5-1.5 4.5-4.5 4.5 4.5-1.5 1.5-3-3v5h-1z" />
        </svg>
        <span>DVCS</span>
      </Link>

      {/* Center: Search */}
      <form
        onSubmit={handleSearchSubmit}
        role="search"
        className="flex-1 max-w-xl mx-auto"
      >
        <label htmlFor="global-search" className="sr-only">
          Search repositories, users, or code
        </label>
        <input
          id="global-search"
          type="search"
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          placeholder="Search…"
          className="w-full px-3 py-1.5 rounded-md bg-gray-800 border border-gray-600 text-gray-200 placeholder-gray-500 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent transition"
        />
      </form>

      {/* Right: Auth actions */}
      <nav aria-label="User navigation" className="flex-shrink-0 flex items-center gap-2">
        {isLoading ? (
          <div className="w-8 h-8 rounded-full bg-gray-700 animate-pulse" aria-hidden="true" />
        ) : isAuthenticated ? (
          <>
            <NotificationBell />
            <UserMenu />
          </>
        ) : (
          <>
            <Link
              to="/login"
              className="px-3 py-1.5 text-sm text-gray-300 hover:text-white transition-colors"
            >
              Login
            </Link>
            <Link
              to="/register"
              className="px-3 py-1.5 text-sm bg-indigo-600 text-white rounded-md hover:bg-indigo-500 transition-colors"
            >
              Register
            </Link>
          </>
        )}
      </nav>
    </header>
  )
}

// ─── Repo Sidebar ─────────────────────────────────────────────────────────────

function RepoSidebar({ owner, repo }: Required<RepoNavParams>) {
  const base = `/${owner}/${repo}`

  const navLinkClass = ({ isActive }: { isActive: boolean }) =>
    [
      'flex items-center gap-2 px-3 py-2 rounded-md text-sm transition-colors',
      isActive
        ? 'bg-indigo-600 text-white font-medium'
        : 'text-gray-400 hover:bg-gray-700 hover:text-gray-200',
    ].join(' ')

  return (
    <aside
      aria-label={`${owner}/${repo} navigation`}
      className="fixed top-14 left-0 bottom-0 w-56 bg-gray-900 border-r border-gray-700 overflow-y-auto z-30 flex flex-col"
    >
      {/* Repo header */}
      <div className="px-4 py-3 border-b border-gray-700">
        <Link
          to={base}
          className="text-sm font-semibold text-indigo-400 hover:text-indigo-300 truncate block transition-colors"
          title={`${owner}/${repo}`}
        >
          <span className="text-gray-400">{owner}/</span>
          {repo}
        </Link>
      </div>

      {/* Nav links */}
      <nav className="flex-1 px-2 py-3 space-y-1">
        <NavLink to={`${base}/tree`} end className={navLinkClass}>
          <span aria-hidden="true">📁</span>
          Code
        </NavLink>

        <NavLink to={`${base}/issues`} className={navLinkClass}>
          <span aria-hidden="true">🐛</span>
          Issues
        </NavLink>

        <NavLink to={`${base}/pulls`} className={navLinkClass}>
          <span aria-hidden="true">🔀</span>
          Pull Requests
        </NavLink>

        <NavLink to={`${base}/pipelines`} className={navLinkClass}>
          <span aria-hidden="true">⚙️</span>
          Pipelines
        </NavLink>

        <NavLink to={`${base}/settings`} className={navLinkClass}>
          <span aria-hidden="true">⚙</span>
          Settings
        </NavLink>
      </nav>
    </aside>
  )
}

// ─── Hook: detect repo page ───────────────────────────────────────────────────

/**
 * Returns the owner/repo params when the current URL matches /:owner/:repo/*,
 * otherwise returns null.
 *
 * We use useLocation + a simple regex rather than relying on nested route
 * params so AppShell can be used as a top-level layout without requiring
 * every child route to pass params up.
 */
function useRepoPageParams(): RepoNavParams | null {
  const location = useLocation()
  // Match /:owner/:repo or /:owner/:repo/anything
  const match = location.pathname.match(/^\/([^/]+)\/([^/]+)(?:\/|$)/)
  if (!match) return null

  const [, owner, repo] = match

  // Exclude known top-level routes that aren't repo pages
  const topLevelRoutes = new Set([
    'login',
    'register',
    'settings',
    'explore',
    'notifications',
  ])
  if (topLevelRoutes.has(owner)) return null

  return { owner, repo }
}

// ─── AppShell ─────────────────────────────────────────────────────────────────

/**
 * Root layout component.
 *
 * Structure:
 *   <TopNav />                    — fixed, full-width, h-14
 *   [<RepoSidebar />]             — fixed, left, w-56, visible on repo pages
 *   <main>                        — pt-14, pl-56 when sidebar visible
 *     <Outlet />
 *   </main>
 */
export default function AppShell() {
  const repoParams = useRepoPageParams()
  const hasSidebar = repoParams !== null

  return (
    <div className="min-h-screen bg-gray-950 text-gray-100">
      <TopNav />

      {hasSidebar && repoParams.owner && repoParams.repo && (
        <RepoSidebar owner={repoParams.owner} repo={repoParams.repo} />
      )}

      <main
        className={[
          'pt-14 min-h-screen',
          hasSidebar ? 'pl-56' : '',
        ].join(' ')}
      >
        <Outlet />
      </main>
    </div>
  )
}
