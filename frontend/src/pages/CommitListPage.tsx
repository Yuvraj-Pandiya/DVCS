/**
 * CommitListPage — paginated commit list at route /:owner/:repo/commits/:ref
 *
 * Displays:
 * - Paginated list of commits for a given branch/ref
 * - Each row shows: author avatar (placeholder), commit message (truncated), SHA (link to detail), relative timestamp
 * - Infinite scroll or page controls for pagination
 * - Fetches commits via GET /api/repos/{owner}/{repo}/commits/{ref}?page=
 *
 * Features:
 * - Infinite scroll pagination (loads more commits as user scrolls)
 * - Commit message truncation with ellipsis
 * - SHA displayed as short hash (first 7 chars) with link to commit detail
 * - Relative timestamps (e.g., "2 hours ago")
 * - Loading states and error handling
 */

import { useParams, Link } from 'react-router-dom'
import { useInfiniteQuery } from '@tanstack/react-query'
import { useApiClient, ApiError } from '../api/client'
import { useEffect, useRef } from 'react'

// ─── Types ────────────────────────────────────────────────────────────────────

interface CommitMetaDto {
  id: number
  sha: string
  authorId: number | null
  authorUsername: string | null
  message: string
  authoredAt: string
  committedAt: string
}

interface CommitLogResponse {
  commits: CommitMetaDto[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

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

/** Truncate commit message to a maximum length with ellipsis. */
function truncateMessage(message: string, maxLength: number = 80): string {
  const firstLine = message.split('\n')[0]
  if (firstLine.length <= maxLength) return firstLine
  return firstLine.slice(0, maxLength) + '...'
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

// ─── CommitRow Component ──────────────────────────────────────────────────────

interface CommitRowProps {
  commit: CommitMetaDto
  owner: string
  repo: string
}

function CommitRow({ commit, owner, repo }: CommitRowProps) {
  return (
    <div className="flex items-center gap-4 px-4 py-3 hover:bg-gray-700 transition-colors border-b border-gray-700 last:border-b-0">
      {/* Author avatar (placeholder with initials) */}
      <div
        className="w-10 h-10 rounded-full bg-indigo-700 flex items-center justify-center flex-shrink-0"
        title={commit.authorUsername || 'Unknown author'}
      >
        <span className="text-white font-semibold text-sm select-none">
          {getInitials(commit.authorUsername)}
        </span>
      </div>
      
      {/* Commit message and metadata */}
      <div className="flex-1 min-w-0">
        <div className="flex items-baseline gap-2 mb-1">
          <Link
            to={`/${owner}/${repo}/commit/${commit.sha}`}
            className="text-sm text-gray-200 hover:text-indigo-400 transition-colors font-medium truncate"
            title={commit.message}
          >
            {truncateMessage(commit.message)}
          </Link>
        </div>
        <div className="flex items-center gap-3 text-xs text-gray-500">
          {commit.authorUsername && (
            <span className="flex items-center gap-1">
              <span className="text-gray-400">{commit.authorUsername}</span>
            </span>
          )}
          <span>committed {formatRelativeTime(commit.authoredAt)}</span>
        </div>
      </div>
      
      {/* SHA link */}
      <div className="flex-shrink-0">
        <Link
          to={`/${owner}/${repo}/commit/${commit.sha}`}
          className="font-mono text-xs text-gray-400 hover:text-indigo-400 transition-colors px-2 py-1 rounded bg-gray-800 border border-gray-700 hover:border-indigo-500"
          title={commit.sha}
        >
          {shortSha(commit.sha)}
        </Link>
      </div>
    </div>
  )
}

// ─── Skeleton Component ───────────────────────────────────────────────────────

function CommitListSkeleton() {
  return (
    <div className="animate-pulse divide-y divide-gray-700">
      {[...Array(10)].map((_, i) => (
        <div key={i} className="flex items-center gap-4 px-4 py-3">
          <div className="w-10 h-10 bg-gray-700 rounded-full flex-shrink-0" />
          <div className="flex-1 space-y-2">
            <div className="h-4 bg-gray-700 rounded w-3/4" />
            <div className="h-3 bg-gray-700 rounded w-1/2" />
          </div>
          <div className="w-16 h-6 bg-gray-700 rounded" />
        </div>
      ))}
    </div>
  )
}

// ─── LoadMoreTrigger Component ────────────────────────────────────────────────

interface LoadMoreTriggerProps {
  onIntersect: () => void
}

function LoadMoreTrigger({ onIntersect }: LoadMoreTriggerProps) {
  const ref = useRef<HTMLDivElement>(null)
  
  useEffect(() => {
    const element = ref.current
    if (!element) return
    
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0].isIntersecting) {
          onIntersect()
        }
      },
      { threshold: 0.1 }
    )
    
    observer.observe(element)
    
    return () => {
      observer.disconnect()
    }
  }, [onIntersect])
  
  return <div ref={ref} className="h-4" />
}

// ─── CommitListPage ───────────────────────────────────────────────────────────

export default function CommitListPage() {
  const { owner, repo, ref } = useParams<{
    owner: string
    repo: string
    ref: string
  }>()
  
  const api = useApiClient()
  
  // Infinite query for paginated commits
  const {
    data,
    isLoading,
    error,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
  } = useInfiniteQuery<CommitLogResponse, ApiError>({
    queryKey: ['repo-commits', owner, repo, ref],
    queryFn: ({ pageParam = 0 }) =>
      api.get<CommitLogResponse>(
        `/api/repos/${owner}/${repo}/commits/${ref}?page=${pageParam}&size=20`
      ),
    initialPageParam: 0,
    getNextPageParam: (lastPage) => {
      const nextPage = lastPage.page + 1
      return nextPage < lastPage.totalPages ? nextPage : undefined
    },
    enabled: !!owner && !!repo && !!ref,
    retry: (failureCount, error) => {
      if (error instanceof ApiError && error.status === 404) return false
      return failureCount < 2
    },
  })
  
  // Flatten all pages into a single commit list
  const commits = data?.pages.flatMap((page) => page.commits) ?? []
  const totalElements = data?.pages[0]?.totalElements ?? 0
  
  // ── 404 state ──────────────────────────────────────────────────────────────
  
  if (error instanceof ApiError && error.status === 404) {
    return (
      <div className="min-h-screen bg-gray-950 flex items-center justify-center px-4">
        <div className="text-center">
          <p className="text-6xl font-bold text-gray-700 mb-4" aria-hidden="true">
            404
          </p>
          <h1 className="text-xl font-semibold text-gray-200 mb-2">
            Branch not found
          </h1>
          <p className="text-gray-400 mb-6">
            The branch{' '}
            <span className="font-mono text-gray-300">{ref}</span>{' '}
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
          <p className="text-gray-400 mb-4">Failed to load commits.</p>
          <p className="text-sm text-red-400">
            {error instanceof Error ? error.message : 'Unknown error'}
          </p>
        </div>
      </div>
    )
  }
  
  // ── Loading state ──────────────────────────────────────────────────────────
  
  if (isLoading && commits.length === 0) {
    return (
      <div className="min-h-screen bg-gray-950">
        <div className="max-w-5xl mx-auto px-4 py-6">
          {/* Header skeleton */}
          <div className="mb-6 space-y-2">
            <div className="h-8 bg-gray-700 rounded w-64 animate-pulse" />
            <div className="h-4 bg-gray-700 rounded w-48 animate-pulse" />
          </div>
          {/* Commit list skeleton */}
          <div className="bg-gray-800 border border-gray-700 rounded-lg overflow-hidden">
            <CommitListSkeleton />
          </div>
        </div>
      </div>
    )
  }
  
  if (commits.length === 0) {
    return (
      <div className="min-h-screen bg-gray-950">
        <div className="max-w-5xl mx-auto px-4 py-6">
          {/* Header */}
          <div className="mb-6">
            <h1 className="text-2xl font-bold text-gray-100 mb-2">
              Commits on {ref}
            </h1>
            <nav className="flex items-center gap-2 text-sm text-gray-400">
              <Link
                to={`/${owner}/${repo}`}
                className="hover:text-indigo-400 transition-colors"
              >
                {owner}/{repo}
              </Link>
              <span>/</span>
              <span className="text-gray-200">commits</span>
            </nav>
          </div>
          
          {/* Empty state */}
          <div className="bg-gray-800 border border-gray-700 rounded-lg p-8 text-center">
            <p className="text-sm text-gray-500">No commits found on this branch.</p>
          </div>
        </div>
      </div>
    )
  }
  
  // ── Commit list view ───────────────────────────────────────────────────────
  
  return (
    <div className="min-h-screen bg-gray-950 text-gray-100">
      <div className="max-w-5xl mx-auto px-4 py-6">
        {/* Header */}
        <div className="mb-6">
          <h1 className="text-2xl font-bold text-gray-100 mb-2">
            Commits on {ref}
          </h1>
          <nav className="flex items-center gap-2 text-sm text-gray-400">
            <Link
              to={`/${owner}/${repo}`}
              className="hover:text-indigo-400 transition-colors"
            >
              {owner}/{repo}
            </Link>
            <span>/</span>
            <span className="text-gray-200">commits</span>
          </nav>
          <p className="text-sm text-gray-500 mt-2">
            {totalElements.toLocaleString()} {totalElements === 1 ? 'commit' : 'commits'}
          </p>
        </div>
        
        {/* Commit list */}
        <div className="bg-gray-800 border border-gray-700 rounded-lg overflow-hidden">
          {commits.map((commit) => (
            <CommitRow
              key={commit.id}
              commit={commit}
              owner={owner!}
              repo={repo!}
            />
          ))}
          
          {/* Load more trigger (infinite scroll) */}
          {hasNextPage && (
            <LoadMoreTrigger onIntersect={() => fetchNextPage()} />
          )}
          
          {/* Loading more indicator */}
          {isFetchingNextPage && (
            <div className="px-4 py-6 text-center">
              <div className="inline-flex items-center gap-2 text-sm text-gray-400">
                <svg
                  className="animate-spin h-4 w-4"
                  xmlns="http://www.w3.org/2000/svg"
                  fill="none"
                  viewBox="0 0 24 24"
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
                    d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
                  />
                </svg>
                Loading more commits...
              </div>
            </div>
          )}
          
          {/* End of list indicator */}
          {!hasNextPage && commits.length > 0 && (
            <div className="px-4 py-4 text-center text-xs text-gray-600 border-t border-gray-700">
              End of commit history
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
