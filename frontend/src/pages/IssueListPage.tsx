/**
 * IssueListPage — issue list at route /:owner/:repo/issues
 *
 * Displays:
 * - Filterable list of issues by status (open, closed) and label
 * - Each issue shows: number, title, label badges, comment count, author, created date
 * - "New Issue" button navigating to /:owner/:repo/issues/new
 * - Pagination support
 *
 * Fetches via: GET /api/repos/{owner}/{repo}/issues?status=&label=&page=&size=
 */

import { useParams, Link, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useApiClient, ApiError } from '../api/client'

// ─── Types ────────────────────────────────────────────────────────────────────

interface LabelDto {
  id: number
  name: string
  color: string
}

interface IssueDto {
  id: number
  number: number
  title: string
  body: string | null
  authorId: number
  authorUsername: string
  status: 'open' | 'closed'
  createdAt: string
  labels: LabelDto[]
  commentCount?: number
}

interface IssueListResponse {
  issues: IssueDto[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

type StatusFilter = 'open' | 'closed'

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

// ─── LabelBadge Component ─────────────────────────────────────────────────────

interface LabelBadgeProps {
  label: LabelDto
  /** If provided, clicking the badge filters by this label. */
  onClick?: (labelName: string) => void
}

function LabelBadge({ label, onClick }: LabelBadgeProps) {
  const style = {
    backgroundColor: `${label.color}20`,
    color: label.color,
    borderColor: label.color,
    borderWidth: '1px',
  }

  if (onClick) {
    return (
      <button
        type="button"
        onClick={(e) => {
          e.preventDefault()
          e.stopPropagation()
          onClick(label.name)
        }}
        className="inline-flex items-center px-2 py-0.5 text-xs font-medium rounded hover:opacity-80 transition-opacity cursor-pointer"
        style={style}
        title={`Filter by label: ${label.name}`}
      >
        {label.name}
      </button>
    )
  }

  return (
    <span
      className="inline-flex items-center px-2 py-0.5 text-xs font-medium rounded"
      style={style}
    >
      {label.name}
    </span>
  )
}

// ─── IssueStatusIcon Component ────────────────────────────────────────────────

interface IssueStatusIconProps {
  status: 'open' | 'closed'
}

function IssueStatusIcon({ status }: IssueStatusIconProps) {
  if (status === 'open') {
    return (
      <svg
        className="w-4 h-4 text-green-400 flex-shrink-0 mt-0.5"
        fill="none"
        stroke="currentColor"
        viewBox="0 0 24 24"
        aria-label="Open issue"
      >
        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeWidth={2}
          d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
        />
      </svg>
    )
  }

  return (
    <svg
      className="w-4 h-4 text-purple-400 flex-shrink-0 mt-0.5"
      fill="none"
      stroke="currentColor"
      viewBox="0 0 24 24"
      aria-label="Closed issue"
    >
      <path
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth={2}
        d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"
      />
    </svg>
  )
}

// ─── IssueRow Component ───────────────────────────────────────────────────────

interface IssueRowProps {
  issue: IssueDto
  owner: string
  repo: string
  onLabelClick: (labelName: string) => void
}

function IssueRow({ issue, owner, repo, onLabelClick }: IssueRowProps) {
  return (
    <div className="flex items-start gap-3 px-4 py-3 hover:bg-gray-750 transition-colors border-b border-gray-700 last:border-b-0">
      {/* Status icon */}
      <IssueStatusIcon status={issue.status} />

      {/* Issue info */}
      <div className="flex-1 min-w-0">
        {/* Title + labels row */}
        <div className="flex flex-wrap items-center gap-2 mb-1">
          <Link
            to={`/${owner}/${repo}/issues/${issue.number}`}
            className="text-sm font-semibold text-gray-200 hover:text-indigo-400 transition-colors"
          >
            {issue.title}
          </Link>
          {issue.labels.map((label) => (
            <LabelBadge key={label.id} label={label} onClick={onLabelClick} />
          ))}
        </div>

        {/* Meta row: number, author, date */}
        <div className="flex items-center gap-3 text-xs text-gray-500">
          <span>
            #{issue.number} opened {formatRelativeTime(issue.createdAt)} by{' '}
            <span className="text-gray-400">{issue.authorUsername}</span>
          </span>
        </div>
      </div>

      {/* Right side: comment count + author avatar */}
      <div className="flex items-center gap-3 flex-shrink-0">
        {/* Comment count */}
        {typeof issue.commentCount === 'number' && issue.commentCount > 0 && (
          <div
            className="flex items-center gap-1 text-xs text-gray-400"
            title={`${issue.commentCount} comment${issue.commentCount !== 1 ? 's' : ''}`}
          >
            <svg
              className="w-3.5 h-3.5"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
              aria-hidden="true"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z"
              />
            </svg>
            <span>{issue.commentCount}</span>
          </div>
        )}

        {/* Author avatar */}
        <div
          className="w-6 h-6 rounded-full bg-indigo-700 flex items-center justify-center"
          title={issue.authorUsername}
          aria-label={`Author: ${issue.authorUsername}`}
        >
          <span className="text-white font-semibold text-xs select-none">
            {getInitials(issue.authorUsername)}
          </span>
        </div>
      </div>
    </div>
  )
}

// ─── Skeleton Component ───────────────────────────────────────────────────────

function IssueListSkeleton() {
  return (
    <div className="animate-pulse divide-y divide-gray-700" aria-busy="true" aria-label="Loading issues">
      {[...Array(5)].map((_, i) => (
        <div key={i} className="flex items-start gap-3 px-4 py-3">
          <div className="w-4 h-4 bg-gray-700 rounded-full flex-shrink-0 mt-0.5" />
          <div className="flex-1 space-y-2">
            <div className="h-4 bg-gray-700 rounded w-3/4" />
            <div className="h-3 bg-gray-700 rounded w-1/3" />
          </div>
          <div className="w-6 h-6 bg-gray-700 rounded-full flex-shrink-0" />
        </div>
      ))}
    </div>
  )
}

// ─── IssueListPage ────────────────────────────────────────────────────────────

export default function IssueListPage() {
  const { owner, repo } = useParams<{ owner: string; repo: string }>()
  const [searchParams, setSearchParams] = useSearchParams()

  const statusFilter = (searchParams.get('status') as StatusFilter) || 'open'
  const labelFilter = searchParams.get('label') || ''
  const page = parseInt(searchParams.get('page') || '0', 10)

  const api = useApiClient()

  // Build query string
  const queryString = new URLSearchParams({
    status: statusFilter,
    page: String(page),
    size: '20',
    ...(labelFilter ? { label: labelFilter } : {}),
  }).toString()

  const {
    data,
    isLoading,
    error,
  } = useQuery<IssueListResponse, ApiError>({
    queryKey: ['repo-issues', owner, repo, statusFilter, labelFilter, page],
    queryFn: () =>
      api.get<IssueListResponse>(`/api/repos/${owner}/${repo}/issues?${queryString}`),
    enabled: !!owner && !!repo,
    retry: (failureCount, error) => {
      if (error instanceof ApiError && error.status === 404) return false
      return failureCount < 2
    },
  })

  const issues = data?.issues ?? []
  const totalElements = data?.totalElements ?? 0

  /** Update the label filter; resets to page 0. */
  function handleLabelClick(labelName: string) {
    // Toggle: clicking the active label clears it
    const newLabel = labelFilter === labelName ? '' : labelName
    const params: Record<string, string> = { status: statusFilter, page: '0' }
    if (newLabel) params.label = newLabel
    setSearchParams(params)
  }

  /** Clear the label filter. */
  function clearLabelFilter() {
    const params: Record<string, string> = { status: statusFilter, page: '0' }
    setSearchParams(params)
  }

  // ── 404 state ──────────────────────────────────────────────────────────────

  if (error instanceof ApiError && error.status === 404) {
    return (
      <div className="min-h-screen bg-gray-950 flex items-center justify-center px-4">
        <div className="text-center">
          <p className="text-6xl font-bold text-gray-700 mb-4" aria-hidden="true">
            404
          </p>
          <h1 className="text-xl font-semibold text-gray-200 mb-2">Repository not found</h1>
          <p className="text-gray-400 mb-6">
            The repository{' '}
            <span className="font-mono text-gray-300">
              {owner}/{repo}
            </span>{' '}
            doesn&apos;t exist or you don&apos;t have access.
          </p>
          <Link
            to="/"
            className="inline-flex items-center gap-2 rounded-md bg-indigo-600 px-4 py-2 text-sm font-semibold text-white hover:bg-indigo-500 transition-colors focus:outline-none focus:ring-2 focus:ring-indigo-400 focus:ring-offset-2 focus:ring-offset-gray-950"
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
          <p className="text-gray-400 mb-4">Failed to load issues.</p>
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
          <div className="mb-6 flex items-center justify-between">
            <div className="space-y-2">
              <div className="h-7 bg-gray-700 rounded w-24 animate-pulse" />
              <div className="h-4 bg-gray-700 rounded w-48 animate-pulse" />
            </div>
            <div className="h-9 bg-gray-700 rounded w-28 animate-pulse" />
          </div>
          <div className="bg-gray-800 border border-gray-700 rounded-lg overflow-hidden">
            <IssueListSkeleton />
          </div>
        </div>
      </div>
    )
  }

  // ── Issue list view ───────────────────────────────────────────────────────

  return (
    <div className="min-h-screen bg-gray-950 text-gray-100">
      <div className="max-w-5xl mx-auto px-4 py-6">

        {/* ── Header ──────────────────────────────────────────────────────── */}
        <div className="mb-5 flex items-start justify-between gap-4">
          <div>
            <h1 className="text-xl font-bold text-gray-100 mb-1">Issues</h1>
            <nav className="flex items-center gap-1.5 text-sm text-gray-400" aria-label="Breadcrumb">
              <Link
                to={`/${owner}/${repo}`}
                className="hover:text-indigo-400 transition-colors"
              >
                {owner}/{repo}
              </Link>
              <span aria-hidden="true">/</span>
              <span className="text-gray-200">issues</span>
            </nav>
          </div>

          {/* New Issue button */}
          <Link
            to={`/${owner}/${repo}/issues/new`}
            className="flex-shrink-0 px-4 py-2 text-sm font-semibold text-white bg-indigo-600 hover:bg-indigo-500 rounded-md transition-colors focus:outline-none focus:ring-2 focus:ring-indigo-400 focus:ring-offset-2 focus:ring-offset-gray-950"
          >
            New issue
          </Link>
        </div>

        {/* ── Filters bar ─────────────────────────────────────────────────── */}
        <div className="flex flex-wrap items-center gap-3 mb-4">
          {/* Status tabs */}
          <div className="flex items-center border border-gray-700 rounded-md overflow-hidden">
            {(['open', 'closed'] as const).map((status) => (
              <button
                key={status}
                onClick={() => {
                  const params: Record<string, string> = { status, page: '0' }
                  if (labelFilter) params.label = labelFilter
                  setSearchParams(params)
                }}
                className={`px-4 py-1.5 text-sm font-medium transition-colors ${
                  statusFilter === status
                    ? 'bg-gray-700 text-gray-100'
                    : 'bg-gray-800 text-gray-400 hover:text-gray-200 hover:bg-gray-750'
                }`}
                aria-pressed={statusFilter === status}
              >
                {status === 'open' ? (
                  <span className="flex items-center gap-1.5">
                    <svg className="w-3.5 h-3.5 text-green-400" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                    </svg>
                    Open
                  </span>
                ) : (
                  <span className="flex items-center gap-1.5">
                    <svg className="w-3.5 h-3.5 text-purple-400" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                    </svg>
                    Closed
                  </span>
                )}
              </button>
            ))}
          </div>

          {/* Active label filter chip */}
          {labelFilter && (
            <div className="flex items-center gap-1.5 px-3 py-1 bg-gray-800 border border-gray-600 rounded-full text-sm text-gray-300">
              <span className="text-gray-500 text-xs">Label:</span>
              <span className="font-medium">{labelFilter}</span>
              <button
                type="button"
                onClick={clearLabelFilter}
                className="ml-1 text-gray-500 hover:text-gray-200 transition-colors"
                aria-label={`Remove label filter: ${labelFilter}`}
              >
                <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>
          )}

          {/* Issue count */}
          <span className="ml-auto text-sm text-gray-500">
            {totalElements.toLocaleString()} {totalElements === 1 ? 'issue' : 'issues'}
          </span>
        </div>

        {/* ── Issue list ───────────────────────────────────────────────────── */}
        {issues.length === 0 ? (
          <div className="bg-gray-800 border border-gray-700 rounded-lg p-10 text-center">
            <svg
              className="w-10 h-10 text-gray-600 mx-auto mb-3"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
              aria-hidden="true"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={1.5}
                d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
              />
            </svg>
            <p className="text-sm text-gray-500">
              {labelFilter
                ? `No ${statusFilter} issues with label "${labelFilter}".`
                : `No ${statusFilter} issues found.`}
            </p>
            {labelFilter && (
              <button
                type="button"
                onClick={clearLabelFilter}
                className="mt-3 text-sm text-indigo-400 hover:text-indigo-300 transition-colors"
              >
                Clear label filter
              </button>
            )}
          </div>
        ) : (
          <div className="bg-gray-800 border border-gray-700 rounded-lg overflow-hidden">
            {issues.map((issue) => (
              <IssueRow
                key={issue.id}
                issue={issue}
                owner={owner!}
                repo={repo!}
                onLabelClick={handleLabelClick}
              />
            ))}
          </div>
        )}

        {/* ── Pagination ───────────────────────────────────────────────────── */}
        {data && data.totalPages > 1 && (
          <div className="flex items-center justify-center gap-4 mt-6">
            <button
              onClick={() => {
                const params: Record<string, string> = { status: statusFilter, page: String(page - 1) }
                if (labelFilter) params.label = labelFilter
                setSearchParams(params)
              }}
              disabled={page === 0}
              className="px-4 py-2 text-sm font-medium text-gray-300 hover:text-gray-100 bg-gray-800 hover:bg-gray-700 border border-gray-700 rounded transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
            >
              Previous
            </button>
            <span className="text-sm text-gray-400">
              Page {page + 1} of {data.totalPages}
            </span>
            <button
              onClick={() => {
                const params: Record<string, string> = { status: statusFilter, page: String(page + 1) }
                if (labelFilter) params.label = labelFilter
                setSearchParams(params)
              }}
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
