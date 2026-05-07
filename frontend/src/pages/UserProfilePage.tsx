/**
 * UserProfilePage — public profile page at route /:owner
 *
 * Fetches user data via GET /api/users/:owner and displays:
 * - Avatar (with initials fallback), username, bio, join date
 * - Public repository list (cards with name, description, language, last updated)
 * - Activity feed (recent commits / PRs / issues — placeholder if no dedicated endpoint)
 *
 * Handles loading skeleton, 404 (user not found), and generic errors.
 */

import { useParams, Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useApiClient, ApiError } from '../api/client'

// ─── Types ────────────────────────────────────────────────────────────────────

interface UserProfile {
  id: number
  username: string
  email: string
  avatarUrl: string | null
  bio: string | null
  createdAt: string
}

interface RepoSummary {
  id: number
  name: string
  description: string | null
  isPrivate: boolean
  defaultBranch: string
  createdAt: string
  language?: string | null
  updatedAt?: string | null
  starCount?: number
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

/** Format an ISO date string as "Month Year" (e.g. "January 2024"). */
function formatJoinDate(isoString: string): string {
  try {
    return new Date(isoString).toLocaleDateString('en-US', {
      month: 'long',
      year: 'numeric',
    })
  } catch {
    return isoString
  }
}

/** Format an ISO date string as a relative or absolute date. */
function formatUpdatedAt(isoString: string | null | undefined): string {
  if (!isoString) return ''
  try {
    const now = Date.now()
    const then = new Date(isoString).getTime()
    const diffMs = now - then
    const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24))

    if (diffDays === 0) return 'Updated today'
    if (diffDays === 1) return 'Updated yesterday'
    if (diffDays < 30) return `Updated ${diffDays} days ago`

    return `Updated ${new Date(isoString).toLocaleDateString('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
    })}`
  } catch {
    return ''
  }
}

/** Derive initials from a username for the avatar fallback. */
function getInitials(username: string): string {
  return username.slice(0, 2).toUpperCase()
}

/** Map a language name to a Tailwind color class for the language badge. */
function languageColor(language: string | null | undefined): string {
  if (!language) return 'bg-gray-600 text-gray-300'
  const map: Record<string, string> = {
    TypeScript: 'bg-blue-700 text-blue-200',
    JavaScript: 'bg-yellow-700 text-yellow-200',
    Python: 'bg-green-700 text-green-200',
    Java: 'bg-orange-700 text-orange-200',
    Go: 'bg-cyan-700 text-cyan-200',
    Rust: 'bg-red-800 text-red-200',
    'C++': 'bg-pink-700 text-pink-200',
    C: 'bg-purple-700 text-purple-200',
    Ruby: 'bg-rose-700 text-rose-200',
    PHP: 'bg-violet-700 text-violet-200',
    Shell: 'bg-lime-700 text-lime-200',
    HTML: 'bg-orange-600 text-orange-200',
    CSS: 'bg-sky-700 text-sky-200',
  }
  return map[language] ?? 'bg-gray-600 text-gray-300'
}

// ─── Skeleton components ──────────────────────────────────────────────────────

function ProfileSkeleton() {
  return (
    <div className="animate-pulse" aria-busy="true" aria-label="Loading profile">
      {/* Avatar + info */}
      <div className="flex flex-col sm:flex-row gap-6 mb-8">
        <div className="w-24 h-24 rounded-full bg-gray-700 flex-shrink-0" />
        <div className="flex-1 space-y-3 pt-2">
          <div className="h-6 bg-gray-700 rounded w-40" />
          <div className="h-4 bg-gray-700 rounded w-64" />
          <div className="h-4 bg-gray-700 rounded w-32" />
        </div>
      </div>
      {/* Repo cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {[1, 2, 3, 4].map((i) => (
          <div key={i} className="bg-gray-800 border border-gray-700 rounded-lg p-4 space-y-2">
            <div className="h-4 bg-gray-700 rounded w-32" />
            <div className="h-3 bg-gray-700 rounded w-full" />
            <div className="h-3 bg-gray-700 rounded w-3/4" />
          </div>
        ))}
      </div>
    </div>
  )
}

// ─── RepoCard ─────────────────────────────────────────────────────────────────

interface RepoCardProps {
  repo: RepoSummary
  owner: string
}

function RepoCard({ repo, owner }: RepoCardProps) {
  return (
    <article className="bg-gray-800 border border-gray-700 rounded-lg p-4 hover:border-gray-500 transition-colors flex flex-col gap-2">
      {/* Repo name link */}
      <div className="flex items-center gap-2">
        <svg
          xmlns="http://www.w3.org/2000/svg"
          viewBox="0 0 16 16"
          fill="currentColor"
          className="w-4 h-4 text-gray-400 flex-shrink-0"
          aria-hidden="true"
        >
          <path d="M2 2.5A2.5 2.5 0 0 1 4.5 0h8.75a.75.75 0 0 1 .75.75v12.5a.75.75 0 0 1-.75.75h-2.5a.75.75 0 0 1 0-1.5h1.75v-2h-8a1 1 0 0 0-.714 1.7.75.75 0 1 1-1.072 1.05A2.495 2.495 0 0 1 2 11.5Zm10.5-1h-8a1 1 0 0 0-1 1v6.708A2.486 2.486 0 0 1 4.5 9h8V1.5Z" />
        </svg>
        <Link
          to={`/${owner}/${repo.name}`}
          className="text-indigo-400 hover:text-indigo-300 font-medium text-sm truncate transition-colors"
        >
          {repo.name}
        </Link>
        {repo.isPrivate && (
          <span className="ml-auto flex-shrink-0 text-xs px-1.5 py-0.5 rounded border border-gray-600 text-gray-400">
            Private
          </span>
        )}
      </div>

      {/* Description */}
      {repo.description && (
        <p className="text-gray-400 text-xs leading-relaxed line-clamp-2">
          {repo.description}
        </p>
      )}

      {/* Footer: language + star count + updated */}
      <div className="flex items-center gap-3 mt-auto pt-1 flex-wrap">
        {repo.language && (
          <span
            className={`text-xs px-2 py-0.5 rounded-full font-medium ${languageColor(repo.language)}`}
          >
            {repo.language}
          </span>
        )}
        {typeof repo.starCount === 'number' && (
          <span className="flex items-center gap-1 text-xs text-gray-400">
            <svg
              xmlns="http://www.w3.org/2000/svg"
              viewBox="0 0 16 16"
              fill="currentColor"
              className="w-3.5 h-3.5 text-yellow-400"
              aria-hidden="true"
            >
              <path d="M8 .25a.75.75 0 0 1 .673.418l1.882 3.815 4.21.612a.75.75 0 0 1 .416 1.279l-3.046 2.97.719 4.192a.751.751 0 0 1-1.088.791L8 12.347l-3.766 1.98a.75.75 0 0 1-1.088-.79l.72-4.194L.818 6.374a.75.75 0 0 1 .416-1.28l4.21-.611L7.327.668A.75.75 0 0 1 8 .25Z" />
            </svg>
            {repo.starCount}
          </span>
        )}
        {repo.updatedAt && (
          <span className="text-xs text-gray-500 ml-auto">
            {formatUpdatedAt(repo.updatedAt)}
          </span>
        )}
      </div>
    </article>
  )
}

// ─── ActivityFeed ─────────────────────────────────────────────────────────────

/**
 * Placeholder activity feed section.
 * Displays a "No recent activity" message since there is no dedicated
 * activity endpoint in the current API.
 */
function ActivityFeed() {
  return (
    <section aria-labelledby="activity-heading">
      <h2
        id="activity-heading"
        className="text-base font-semibold text-gray-200 mb-3"
      >
        Recent Activity
      </h2>
      <div className="bg-gray-800 border border-gray-700 rounded-lg p-6 text-center">
        <svg
          xmlns="http://www.w3.org/2000/svg"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.5"
          className="w-10 h-10 text-gray-600 mx-auto mb-3"
          aria-hidden="true"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            d="M12 6v6h4.5m4.5 0a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z"
          />
        </svg>
        <p className="text-sm text-gray-500">No recent activity</p>
      </div>
    </section>
  )
}

// ─── UserProfilePage ──────────────────────────────────────────────────────────

export default function UserProfilePage() {
  const { owner } = useParams<{ owner: string }>()
  const api = useApiClient()

  // Fetch user profile
  const {
    data: user,
    isLoading: userLoading,
    error: userError,
  } = useQuery<UserProfile, ApiError>({
    queryKey: ['user', owner],
    queryFn: () => api.get<UserProfile>(`/api/users/${owner}`),
    enabled: !!owner,
    retry: (failureCount, error) => {
      // Don't retry on 404
      if (error instanceof ApiError && error.status === 404) return false
      return failureCount < 2
    },
  })

  // Fetch user's public repos
  const {
    data: repos,
    isLoading: reposLoading,
  } = useQuery<RepoSummary[], ApiError>({
    queryKey: ['user-repos', owner],
    queryFn: () => api.get<RepoSummary[]>(`/api/users/${owner}/repos`),
    enabled: !!owner && !!user,
    retry: false,
  })

  const isLoading = userLoading || reposLoading

  // ── 404 state ──────────────────────────────────────────────────────────────

  if (
    userError instanceof ApiError &&
    userError.status === 404
  ) {
    return (
      <div className="min-h-screen bg-gray-950 flex items-center justify-center px-4">
        <div className="text-center">
          <p className="text-6xl font-bold text-gray-700 mb-4" aria-hidden="true">
            404
          </p>
          <h1 className="text-xl font-semibold text-gray-200 mb-2">
            User not found
          </h1>
          <p className="text-gray-400 mb-6">
            The user <span className="font-mono text-gray-300">{owner}</span> doesn&apos;t exist.
          </p>
          <Link
            to="/"
            className="inline-flex items-center gap-2 rounded-md bg-indigo-600 px-4 py-2 text-sm font-semibold text-white hover:bg-indigo-500 transition-colors focus:outline-none focus:ring-2 focus:ring-indigo-400 focus:ring-offset-2 focus:ring-offset-gray-950"
          >
            Go home
          </Link>
        </div>
      </div>
    )
  }

  // ── Generic error state ────────────────────────────────────────────────────

  if (userError && !(userError instanceof ApiError && userError.status === 404)) {
    return (
      <div className="min-h-screen bg-gray-950 flex items-center justify-center px-4">
        <div className="text-center">
          <p className="text-gray-400 mb-4">Failed to load profile.</p>
          <p className="text-sm text-red-400">
            {userError instanceof Error ? userError.message : 'Unknown error'}
          </p>
        </div>
      </div>
    )
  }

  // ── Loading state ──────────────────────────────────────────────────────────

  if (isLoading && !user) {
    return (
      <div className="min-h-screen bg-gray-950 px-4 py-10">
        <div className="max-w-5xl mx-auto">
          <ProfileSkeleton />
        </div>
      </div>
    )
  }

  if (!user) return null

  const publicRepos = (repos ?? []).filter((r) => !r.isPrivate)

  // ── Profile view ───────────────────────────────────────────────────────────

  return (
    <div className="min-h-screen bg-gray-950 text-gray-100">
      {/* Top nav bar */}
      <header className="border-b border-gray-800 bg-gray-900">
        <div className="max-w-5xl mx-auto px-4 py-3 flex items-center gap-3">
          <Link
            to="/"
            className="flex items-center gap-2 text-white font-bold text-lg hover:text-indigo-400 transition-colors"
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
        </div>
      </header>

      <main className="max-w-5xl mx-auto px-4 py-8">
        <div className="flex flex-col lg:flex-row gap-8">
          {/* ── Left sidebar: user info ─────────────────────────────────── */}
          <aside className="lg:w-64 flex-shrink-0" aria-label="User profile">
            {/* Avatar */}
            <div className="mb-4">
              {user.avatarUrl ? (
                <img
                  src={user.avatarUrl}
                  alt={`${user.username}'s avatar`}
                  className="w-24 h-24 lg:w-full lg:h-auto lg:max-h-64 rounded-full lg:rounded-xl object-cover border-2 border-gray-700"
                />
              ) : (
                <div
                  className="w-24 h-24 lg:w-full lg:aspect-square rounded-full lg:rounded-xl bg-indigo-700 flex items-center justify-center border-2 border-gray-700"
                  aria-label={`${user.username}'s avatar`}
                >
                  <span className="text-white font-bold text-3xl lg:text-5xl select-none">
                    {getInitials(user.username)}
                  </span>
                </div>
              )}
            </div>

            {/* Username */}
            <h1 className="text-xl font-bold text-gray-100 mb-1">
              {user.username}
            </h1>

            {/* Bio */}
            {user.bio && (
              <p className="text-sm text-gray-400 mb-4 leading-relaxed">
                {user.bio}
              </p>
            )}

            {/* Join date */}
            <div className="flex items-center gap-2 text-sm text-gray-500">
              <svg
                xmlns="http://www.w3.org/2000/svg"
                viewBox="0 0 16 16"
                fill="currentColor"
                className="w-4 h-4 flex-shrink-0"
                aria-hidden="true"
              >
                <path d="M5.75 7.5a.75.75 0 1 0 0 1.5.75.75 0 0 0 0-1.5ZM5 10.25a.75.75 0 1 1 1.5 0 .75.75 0 0 1-1.5 0Zm5.75-2.75a.75.75 0 1 0 0 1.5.75.75 0 0 0 0-1.5ZM10 10.25a.75.75 0 1 1 1.5 0 .75.75 0 0 1-1.5 0ZM8 7.5a.75.75 0 1 0 0 1.5.75.75 0 0 0 0-1.5ZM7.25 10.25a.75.75 0 1 1 1.5 0 .75.75 0 0 1-1.5 0Z" />
                <path
                  fillRule="evenodd"
                  d="M4.75 1a.75.75 0 0 1 .75.75V3h5V1.75a.75.75 0 0 1 1.5 0V3h.25A2.75 2.75 0 0 1 15 5.75v7.5A2.75 2.75 0 0 1 12.25 16H3.75A2.75 2.75 0 0 1 1 13.25v-7.5A2.75 2.75 0 0 1 3.75 3H4V1.75A.75.75 0 0 1 4.75 1ZM3.75 4.5c-.69 0-1.25.56-1.25 1.25v.75h11v-.75c0-.69-.56-1.25-1.25-1.25H3.75ZM2.5 8v5.25c0 .69.56 1.25 1.25 1.25h8.5c.69 0 1.25-.56 1.25-1.25V8h-11Z"
                  clipRule="evenodd"
                />
              </svg>
              <span>Joined {formatJoinDate(user.createdAt)}</span>
            </div>

            {/* Repo count */}
            <div className="flex items-center gap-2 text-sm text-gray-500 mt-2">
              <svg
                xmlns="http://www.w3.org/2000/svg"
                viewBox="0 0 16 16"
                fill="currentColor"
                className="w-4 h-4 flex-shrink-0"
                aria-hidden="true"
              >
                <path d="M2 2.5A2.5 2.5 0 0 1 4.5 0h8.75a.75.75 0 0 1 .75.75v12.5a.75.75 0 0 1-.75.75h-2.5a.75.75 0 0 1 0-1.5h1.75v-2h-8a1 1 0 0 0-.714 1.7.75.75 0 1 1-1.072 1.05A2.495 2.495 0 0 1 2 11.5Zm10.5-1h-8a1 1 0 0 0-1 1v6.708A2.486 2.486 0 0 1 4.5 9h8V1.5Z" />
              </svg>
              <span>
                {publicRepos.length}{' '}
                {publicRepos.length === 1 ? 'repository' : 'repositories'}
              </span>
            </div>
          </aside>

          {/* ── Main content ────────────────────────────────────────────── */}
          <div className="flex-1 min-w-0 space-y-8">
            {/* Repositories section */}
            <section aria-labelledby="repos-heading">
              <h2
                id="repos-heading"
                className="text-base font-semibold text-gray-200 mb-3"
              >
                Public Repositories
                {publicRepos.length > 0 && (
                  <span className="ml-2 text-xs font-normal text-gray-500 bg-gray-800 border border-gray-700 rounded-full px-2 py-0.5">
                    {publicRepos.length}
                  </span>
                )}
              </h2>

              {reposLoading ? (
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4 animate-pulse">
                  {[1, 2].map((i) => (
                    <div
                      key={i}
                      className="bg-gray-800 border border-gray-700 rounded-lg p-4 h-24"
                    />
                  ))}
                </div>
              ) : publicRepos.length === 0 ? (
                <div className="bg-gray-800 border border-gray-700 rounded-lg p-6 text-center">
                  <p className="text-sm text-gray-500">No public repositories yet.</p>
                </div>
              ) : (
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  {publicRepos.map((repo) => (
                    <RepoCard key={repo.id} repo={repo} owner={user.username} />
                  ))}
                </div>
              )}
            </section>

            {/* Activity feed section */}
            <ActivityFeed />
          </div>
        </div>
      </main>
    </div>
  )
}
