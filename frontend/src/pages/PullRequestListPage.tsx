/**
 * PullRequestListPage — pull request list at route /:owner/:repo/pulls
 *
 * Displays:
 * - Filterable list of pull requests by status (open, closed, merged)
 * - Each PR shows: number, title, author, status badge, created date
 * - Create PR button (navigates to create form)
 * - Pagination support
 *
 * Features:
 * - Status filter tabs (open, closed, merged)
 * - Visual status indicators
 * - Loading states and error handling
 * - Empty states for each filter
 */

import { useParams, Link, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useApiClient, ApiError } from '../api/client'

// ─── Types ────────────────────────────────────────────────────────────────────

interface PullRequestDto {
  id: number
  number: number
  title: string
  body: string | null
  headBranch: string
  baseBranch: string
  authorId: number
  authorUsername: string
  status: 'open' | 'closed' | 'merged'
  mergedAt: string | null
  createdAt: string
}

interface PullRequestListResponse {
  pullRequests: PullRequestDto[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

type StatusFilter = 'open' | 'closed' | 'merged' | 'all'

// ─── Helpers ──────────────────────────────────────────────────────────────────

/** Format a timestamp to relative time (e.g., "2 hours ago"). */
function formatRelativeTime(timestamp: string): string {
  const now = new Date()
  const then = new Date(timestamp)
  const seconds = Math.floor((now.getTime() - then.getTime()) / 1000)

  if (seconds < 60) return 'just now'
  if (seconds < 3600) return `${Math.floor(seconds / 60)} minutes ago`
  if (seconds < 86400) return `${Math.floor(seconds / 3600)} hours ago`
  if (seconds < 2592000) return `${Math.floor(seconds / 86400)} days ago`
  if (seconds < 31536000) return `${Math.floor(seconds / 2592000)} months ago`
  return `${Math.floor(seconds / 31536000)} years ago`
}

/** Generate initials from username for avatar fallback. */
function getInitials(username: string): string {
  return username.slice(0, 2).toUpperCase()
}

// ─── StatusBadge Component ────────────────────────────────────────────────────

interface StatusBadgeProps {
  status: 'open' | 'closed' | 'merged'
}

function StatusBadge({ status }: StatusBadgeProps) {
  const config = {
    open: {
      label: 'Open',
      className: 'bg-green-900/30 text-green-300 border-green-700',
      icon: (
        <svg className="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={2}
            d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"
          />
        </svg>
      ),
    },
    closed: {
      label: 'Closed',
      className: 'bg-red-900/30 text-red-300 border-red-700',
      icon: (
        <svg className="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={2}
            d="M10 14l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2m7-2a9 9 0 11-18 0 9 9 0 0118 0z"
          />
        </svg>
      ),
    },
    merged: {
      label: 'Merged',
      className: 'bg-purple-900/30 text-purple-300 border-purple-700',
      icon: (
        <svg className="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={2}
            d="M7 16V4m0 0L3 8m4-4l4 4m6 0v12m0 0l4-4m-4 4l-4-4"
          />
        </svg>
      ),
    },
  }

  const { label, className, icon } = config[status]

  return (
    <span
      className={`inline-flex items-center gap-1 px-2 py-0.5 text-xs font-medium border rounded ${className}`}
    >
      {icon}
      {label}
    </span>
  )
}

// ─── PullRequestRow Component ─────────────────────────────────────────────────

interface PullRequestRowProps {
  pr: PullRequestDto
  owner: string
  repo: string
}

function PullRequestRow({ pr, owner, repo }: PullRequestRowProps) {
  return (
    <div className="flex items-start gap-4 px-4 py-4 hover:bg-gray-700 transition-colors border-b border-gray-700 last:border-b-0">
      {/* Author avatar */}
      <div
        className="w-10 h-10 rounded-full bg-indigo-700 flex items-center justify-center flex-shrink-0 mt-1"
        title={pr.authorUsername}
      >
        <span className="text-white font-semibold text-sm select-none">
          {getInitials(pr.authorUsername)}
        </span>
      </div>

      {/* PR info */}
      <div className="flex-1 min-w-0">
        <div className="flex items-start gap-2 mb-2">
          <Link
            to={`/${owner}/${repo}/pulls/${pr.number}`}
            className="text-base font-semibold text-gray-200 hover:text-indigo-400 transition-colors"
          >
            {pr.title}
          </Link>
          <StatusBadge status={pr.status} />
        </div>

        <div className="flex items-center gap-3 text-xs text-gray-500">
          <span>
            #{pr.number} opened {formatRelativeTime(pr.createdAt)} by{' '}
            <span className="text-gray-400">{pr.authorUsername}</span>
          </span>
          <span>•</span>
          <span>
            {pr.headBranch} → {pr.baseBranch}
          </span>
          {pr.status === 'merged' && pr.mergedAt && (
            <>
              <span>•</span>
              <span>merged {formatRelativeTime(pr.mergedAt)}</span>
            </>
          )}
        </div>
      </div>
    </div>
  )
}

// ─── Skeleton Component ───────────────────────────────────────────────────────

function PullRequestListSkeleton() {
  return (
    <div className="animate-pulse divide-y divide-gray-700">
      {[...Array(5)].map((_, i) => (
        <div key={i} className="flex items-start gap-4 px-4 py-4">
          <div className="w-10 h-10 bg-gray-700 rounded-full flex-shrink-0" />
          <div className="flex-1 space-y-2">
            <div className="h-5 bg-gray-700 rounded w-3/4" />
            <div className="h-3 bg-gray-700 rounded w-1/2" />
          </div>
        </div>
      ))}
    </div>
  )
}

// ─── PullRequestListPage ──────────────────────────────────────────────────────

export default function PullRequestListPage() {
  const { owner, repo } = useParams<{ owner: string; repo: string }>()
  const [searchParams, setSearchParams] = useSearchParams()

  const statusFilter = (searchParams.get('status') as StatusFilter) || 'open'
  const page = parseInt(searchParams.get('page') || '0', 10)

  const api = useApiClient()

  const {
    data,
    isLoading,
    error,
  } = useQuery<PullRequestListResponse, ApiError>({
    queryKey: ['repo-pulls', owner, repo, statusFilter, page],
    queryFn: () =>
      api.get<PullRequestListResponse>(
        `/api/repos/${owner}/${repo}/pulls?status=${statusFilter}&page=${page}&size=20`
      ),
    enabled: !!owner && !!repo,
    retry: (failureCount, error) => {
      if (error instanceof ApiError && error.status === 404) return false
      return failureCount < 2
    },
  })

  const pullRequests = data?.pullRequests ?? []
  const totalElements = data?.totalElements ?? 0

  // ── 404 state ──────────────────────────────────────────────────────────────

  if (error instanceof ApiError && error.status === 404) {
    return (
      <div className="min-h-screen bg-gray-950 flex items-center justify-center px-4">
        <div className="text-center">
          <p className="text-6xl font-bold text-gray-700 mb-4" aria-hidden="true">
            404
          </p>
          <h1 className="text-xl font-semibold text-gray-200 mb-2">
            Repository not found
          </h1>
          <p className="text-gray-400 mb-6">
            The repository{' '}
            <span className="font-mono text-gray-300">
              {owner}/{repo}
            </span>{' '}
            doesn&apos;t exist or you don&apos;t have access.
          </p>
          <Link
            to="/"
            className="inline-flex items-center gap-2 rounded-md bg-indigo-600 px-4 py-2 text-sm font-semibold text-white hover:bg-indigo-500 transition-colors"
          >
            Go to home
          </Link>
        </div>
      </div>
    )
  }

  // ── Generic error state ────────────────────────────────────────────────────

  if (error && !(error instanceof ApiError && error.status === 404)) {
    return (
      <div className="min-h-screen bg-gray-950 flex items-center justify-center px-4">
        <div className="text-center">
          <p className="text-gray-400 mb-4">Failed to load pull requests.</p>
          <p className="text-sm text-red-400">
            {error instanceof Error ? error.message : 'Unknown error'}
          </p>
        </div>
      </div>
    )
  }

  // ── Loading state ──────────────────────────────────────────────────────────

  if (isLoading) {
    return (
      <div className="min-h-screen bg-gray-950">
        <div className="max-w-5xl mx-auto px-4 py-6">
          {/* Header skeleton */}
          <div className="mb-6 space-y-2">
            <div className="h-8 bg-gray-700 rounded w-64 animate-pulse" />
            <div className="h-4 bg-gray-700 rounded w-48 animate-pulse" />
          </div>
          {/* PR list skeleton */}
          <div className="bg-gray-800 border border-gray-700 rounded-lg overflow-hidden">
            <PullRequestListSkeleton />
          </div>
        </div>
      </div>
    )
  }

  // ── Pull request list view ────────────────────────────────────────────────

  return (
    <div className="min-h-screen bg-gray-950 text-gray-100">
      <div className="max-w-5xl mx-auto px-4 py-6">
        {/* Header */}
        <div className="mb-6 flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold text-gray-100 mb-2">Pull requests</h1>
            <nav className="flex items-center gap-2 text-sm text-gray-400">
              <Link
                to={`/${owner}/${repo}`}
                className="hover:text-indigo-400 transition-colors"
              >
                {owner}/{repo}
              </Link>
              <span>/</span>
              <span className="text-gray-200">pulls</span>
            </nav>
          </div>

          {/* New PR button */}
          <Link
            to={`/${owner}/${repo}/pulls/new`}
            className="px-4 py-2 text-sm font-semibold text-white bg-indigo-600 hover:bg-indigo-500 rounded-md transition-colors"
          >
            New pull request
          </Link>
        </div>

        {/* Status filter tabs */}
        <div className="flex items-center gap-2 mb-4 border-b border-gray-700">
          {(['open', 'closed', 'merged'] as const).map((status) => (
            <button
              key={status}
              onClick={() => {
                setSearchParams({ status, page: '0' })
              }}
              className={`px-4 py-2 text-sm font-medium transition-colors border-b-2 ${
                statusFilter === status
                  ? 'border-indigo-500 text-indigo-400'
                  : 'border-transparent text-gray-400 hover:text-gray-200'
              }`}
            >
              {status.charAt(0).toUpperCase() + status.slice(1)}
            </button>
          ))}
        </div>

        {/* PR count */}
        <p className="text-sm text-gray-500 mb-4">
          {totalElements.toLocaleString()}{' '}
          {totalElements === 1 ? 'pull request' : 'pull requests'}
        </p>

        {/* PR list */}
        {pullRequests.length === 0 ? (
          <div className="bg-gray-800 border border-gray-700 rounded-lg p-8 text-center">
            <p className="text-sm text-gray-500">
              No {statusFilter} pull requests found.
            </p>
          </div>
        ) : (
          <div className="bg-gray-800 border border-gray-700 rounded-lg overflow-hidden">
            {pullRequests.map((pr) => (
              <PullRequestRow key={pr.id} pr={pr} owner={owner!} repo={repo!} />
            ))}
          </div>
        )}

        {/* Pagination (simple prev/next) */}
        {data && data.totalPages > 1 && (
          <div className="flex items-center justify-center gap-4 mt-6">
            <button
              onClick={() => setSearchParams({ status: statusFilter, page: String(page - 1) })}
              disabled={page === 0}
              className="px-4 py-2 text-sm font-medium text-gray-300 hover:text-gray-100 bg-gray-800 hover:bg-gray-700 border border-gray-700 rounded transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
            >
              Previous
            </button>
            <span className="text-sm text-gray-400">
              Page {page + 1} of {data.totalPages}
            </span>
            <button
              onClick={() => setSearchParams({ status: statusFilter, page: String(page + 1) })}
              disabled={page >= data.totalPages - 1}
              className="px-4 py-2 text-sm font-medium text-gray-300 hover:text-gray-100 bg-gray-800 hover:bg-gray-700 border border-gray-700 rounded transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
            >
              Next
            </button>
          </div>
        )}
      </div>
    </div>
  )
}
