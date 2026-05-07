/**
 * CommitDetailPage — commit detail at route /:owner/:repo/commit/:sha
 *
 * Displays:
 * - Commit metadata: author, committer, message, parent SHAs as links
 * - DiffViewer component showing diff vs first parent
 * - Breadcrumb navigation back to repository
 *
 * Features:
 * - Full commit message display (multi-line)
 * - Parent commit links (handles merge commits with multiple parents)
 * - Author and committer information with timestamps
 * - Diff viewer for changes in this commit
 * - Loading states and error handling
 */

import { useParams, Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useApiClient, ApiError } from '../api/client'
import DiffViewer, { DiffHunk, DiffLine } from '../components/DiffViewer'

// ─── Types ────────────────────────────────────────────────────────────────────

interface CommitDetailDto {
  id: number
  sha: string
  authorId: number | null
  authorUsername: string | null
  committerUsername: string | null
  message: string
  authoredAt: string
  committedAt: string
  parentShas: string[]
  diff: DiffHunk[]
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

/** Format a timestamp to a readable date string. */
function formatTimestamp(timestamp: string): string {
  const date = new Date(timestamp)
  return date.toLocaleString('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  })
}

/** Get short SHA (first 7 characters). */
function shortSha(sha: string): string {
  return sha.slice(0, 7)
}

/** Generate initials from username for avatar fallback. */
function getInitials(username: string | null): string {
  if (!username) return '?'
  return username.slice(0, 2).toUpperCase()
}

// ─── Skeleton Component ───────────────────────────────────────────────────────

function CommitDetailSkeleton() {
  return (
    <div className="animate-pulse space-y-6">
      {/* Header skeleton */}
      <div className="space-y-2">
        <div className="h-8 bg-gray-700 rounded w-3/4" />
        <div className="h-4 bg-gray-700 rounded w-1/2" />
      </div>
      {/* Metadata skeleton */}
      <div className="bg-gray-800 border border-gray-700 rounded-lg p-6 space-y-4">
        <div className="flex items-center gap-4">
          <div className="w-12 h-12 bg-gray-700 rounded-full" />
          <div className="flex-1 space-y-2">
            <div className="h-4 bg-gray-700 rounded w-1/3" />
            <div className="h-3 bg-gray-700 rounded w-1/4" />
          </div>
        </div>
        <div className="h-20 bg-gray-700 rounded" />
      </div>
      {/* Diff skeleton */}
      <div className="bg-gray-800 border border-gray-700 rounded-lg p-6 space-y-2">
        {[...Array(10)].map((_, i) => (
          <div key={i} className="h-6 bg-gray-700 rounded" />
        ))}
      </div>
    </div>
  )
}

// ─── CommitDetailPage ─────────────────────────────────────────────────────────

export default function CommitDetailPage() {
  const { owner, repo, sha } = useParams<{
    owner: string
    repo: string
    sha: string
  }>()

  const api = useApiClient()

  const {
    data: commit,
    isLoading,
    error,
  } = useQuery<CommitDetailDto, ApiError>({
    queryKey: ['commit-detail', owner, repo, sha],
    queryFn: () =>
      api.get<CommitDetailDto>(`/api/repos/${owner}/${repo}/commits/${sha}`),
    enabled: !!owner && !!repo && !!sha,
    retry: (failureCount, error) => {
      if (error instanceof ApiError && error.status === 404) return false
      return failureCount < 2
    },
  })

  // ── 404 state ──────────────────────────────────────────────────────────────

  if (error instanceof ApiError && error.status === 404) {
    return (
      <div className="min-h-screen bg-gray-950 flex items-center justify-center px-4">
        <div className="text-center">
          <p className="text-6xl font-bold text-gray-700 mb-4" aria-hidden="true">
            404
          </p>
          <h1 className="text-xl font-semibold text-gray-200 mb-2">
            Commit not found
          </h1>
          <p className="text-gray-400 mb-6">
            The commit{' '}
            <span className="font-mono text-gray-300">{shortSha(sha || '')}</span>{' '}
            doesn&apos;t exist in this repository.
          </p>
          <Link
            to={`/${owner}/${repo}`}
            className="inline-flex items-center gap-2 rounded-md bg-indigo-600 px-4 py-2 text-sm font-semibold text-white hover:bg-indigo-500 transition-colors"
          >
            Go to repository home
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
          <p className="text-gray-400 mb-4">Failed to load commit details.</p>
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
          <CommitDetailSkeleton />
        </div>
      </div>
    )
  }

  if (!commit) {
    return null
  }

  // ── Commit detail view ─────────────────────────────────────────────────────

  return (
    <div className="min-h-screen bg-gray-950 text-gray-100">
      <div className="max-w-5xl mx-auto px-4 py-6">
        {/* Breadcrumb navigation */}
        <nav className="flex items-center gap-2 text-sm text-gray-400 mb-6">
          <Link
            to={`/${owner}/${repo}`}
            className="hover:text-indigo-400 transition-colors"
          >
            {owner}/{repo}
          </Link>
          <span>/</span>
          <span className="text-gray-200">commit</span>
          <span>/</span>
          <span className="font-mono text-gray-200">{shortSha(commit.sha)}</span>
        </nav>

        {/* Commit message header */}
        <div className="mb-6">
          <h1 className="text-2xl font-bold text-gray-100 mb-2 whitespace-pre-wrap">
            {commit.message.split('\n')[0]}
          </h1>
          {commit.message.split('\n').length > 1 && (
            <div className="text-sm text-gray-400 whitespace-pre-wrap mt-4 p-4 bg-gray-800 border border-gray-700 rounded-lg">
              {commit.message.split('\n').slice(1).join('\n').trim()}
            </div>
          )}
        </div>

        {/* Commit metadata */}
        <div className="bg-gray-800 border border-gray-700 rounded-lg p-6 mb-6 space-y-4">
          {/* Author */}
          <div className="flex items-center gap-4">
            <div
              className="w-12 h-12 rounded-full bg-indigo-700 flex items-center justify-center flex-shrink-0"
              title={commit.authorUsername || 'Unknown author'}
            >
              <span className="text-white font-semibold text-sm select-none">
                {getInitials(commit.authorUsername)}
              </span>
            </div>
            <div className="flex-1">
              <div className="text-sm text-gray-200">
                <span className="font-semibold">
                  {commit.authorUsername || 'Unknown'}
                </span>{' '}
                authored
              </div>
              <div className="text-xs text-gray-500">
                {formatTimestamp(commit.authoredAt)}
              </div>
            </div>
          </div>

          {/* Committer (if different from author) */}
          {commit.committerUsername &&
            commit.committerUsername !== commit.authorUsername && (
              <div className="flex items-center gap-4 pt-4 border-t border-gray-700">
                <div
                  className="w-12 h-12 rounded-full bg-purple-700 flex items-center justify-center flex-shrink-0"
                  title={commit.committerUsername}
                >
                  <span className="text-white font-semibold text-sm select-none">
                    {getInitials(commit.committerUsername)}
                  </span>
                </div>
                <div className="flex-1">
                  <div className="text-sm text-gray-200">
                    <span className="font-semibold">{commit.committerUsername}</span>{' '}
                    committed
                  </div>
                  <div className="text-xs text-gray-500">
                    {formatTimestamp(commit.committedAt)}
                  </div>
                </div>
              </div>
            )}

          {/* Commit SHA */}
          <div className="pt-4 border-t border-gray-700">
            <div className="text-xs text-gray-500 mb-1">Commit SHA</div>
            <div className="font-mono text-sm text-gray-300 bg-gray-900 px-3 py-2 rounded border border-gray-700">
              {commit.sha}
            </div>
          </div>

          {/* Parent commits */}
          {commit.parentShas.length > 0 && (
            <div className="pt-4 border-t border-gray-700">
              <div className="text-xs text-gray-500 mb-2">
                {commit.parentShas.length === 1
                  ? 'Parent commit'
                  : `Parent commits (${commit.parentShas.length})`}
              </div>
              <div className="space-y-2">
                {commit.parentShas.map((parentSha, index) => (
                  <Link
                    key={index}
                    to={`/${owner}/${repo}/commit/${parentSha}`}
                    className="block font-mono text-sm text-indigo-400 hover:text-indigo-300 transition-colors bg-gray-900 px-3 py-2 rounded border border-gray-700 hover:border-indigo-500"
                  >
                    {shortSha(parentSha)} → {parentSha}
                  </Link>
                ))}
              </div>
            </div>
          )}
        </div>

        {/* Diff viewer */}
        <div className="mb-6">
          <h2 className="text-lg font-semibold text-gray-100 mb-4">
            Changes in this commit
          </h2>
          <DiffViewer hunks={commit.diff} />
        </div>
      </div>
    </div>
  )
}
