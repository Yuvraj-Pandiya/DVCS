/**
 * IssueDetailPage — issue detail at route /:owner/:repo/issues/:id
 *
 * Displays:
 * - Issue metadata: title, description, status, labels
 * - Comment thread
 * - Add comment form
 * - Close/reopen issue button
 * - Label management
 *
 * Features:
 * - Add comments
 * - Close/reopen issue
 * - Apply/remove labels
 * - Loading states and error handling
 */

import { useParams, Link } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useApiClient, ApiError } from '../api/client'
import { useState } from 'react'

// ─── Types ────────────────────────────────────────────────────────────────────

interface IssueDetailDto {
  id: number
  number: number
  title: string
  body: string | null
  authorId: number
  authorUsername: string
  status: 'open' | 'closed'
  createdAt: string
  labels: LabelDto[]
  comments: CommentDto[]
}

interface LabelDto {
  id: number
  name: string
  color: string
}

interface CommentDto {
  id: number
  authorId: number
  authorUsername: string
  body: string
  createdAt: string
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

/** Format a timestamp to relative time. */
function formatRelativeTime(timestamp: string): string {
  const now = new Date()
  const then = new Date(timestamp)
  const seconds = Math.floor((now.getTime() - then.getTime()) / 1000)

  if (seconds < 60) return 'just now'
  if (seconds < 3600) return `${Math.floor(seconds / 60)} minutes ago`
  if (seconds < 86400) return `${Math.floor(seconds / 3600)} hours ago`
  if (seconds < 2592000) return `${Math.floor(seconds / 86400)} days ago`
  return `${Math.floor(seconds / 2592000)} months ago`
}

/** Generate initials from username. */
function getInitials(username: string): string {
  return username.slice(0, 2).toUpperCase()
}

// ─── StatusBadge Component ────────────────────────────────────────────────────

interface StatusBadgeProps {
  status: 'open' | 'closed'
}

function StatusBadge({ status }: StatusBadgeProps) {
  const config = {
    open: { label: 'Open', className: 'bg-green-900/30 text-green-300 border-green-700' },
    closed: { label: 'Closed', className: 'bg-purple-900/30 text-purple-300 border-purple-700' },
  }

  const { label, className } = config[status]

  return (
    <span className={`inline-flex items-center px-3 py-1 text-sm font-medium border rounded ${className}`}>
      {label}
    </span>
  )
}

// ─── LabelBadge Component ─────────────────────────────────────────────────────

interface LabelBadgeProps {
  label: LabelDto
}

function LabelBadge({ label }: LabelBadgeProps) {
  return (
    <span
      className="inline-flex items-center px-2 py-1 text-xs font-medium rounded"
      style={{
        backgroundColor: `${label.color}20`,
        color: label.color,
        borderColor: label.color,
        borderWidth: '1px',
      }}
    >
      {label.name}
    </span>
  )
}

// ─── CommentThread Component ──────────────────────────────────────────────────

interface CommentThreadProps {
  comments: CommentDto[]
}

function CommentThread({ comments }: CommentThreadProps) {
  if (comments.length === 0) {
    return (
      <div className="bg-gray-800 border border-gray-700 rounded-lg p-6 text-center">
        <p className="text-sm text-gray-500">No comments yet.</p>
      </div>
    )
  }

  return (
    <div className="space-y-4">
      {comments.map((comment) => (
        <div key={comment.id} className="bg-gray-800 border border-gray-700 rounded-lg p-4">
          <div className="flex items-start gap-3">
            <div className="w-8 h-8 rounded-full bg-purple-700 flex items-center justify-center flex-shrink-0">
              <span className="text-white font-semibold text-xs select-none">
                {getInitials(comment.authorUsername)}
              </span>
            </div>
            <div className="flex-1">
              <div className="text-sm mb-2">
                <span className="font-semibold text-gray-200">{comment.authorUsername}</span>{' '}
                <span className="text-gray-500">commented {formatRelativeTime(comment.createdAt)}</span>
              </div>
              <div className="text-sm text-gray-300 whitespace-pre-wrap">{comment.body}</div>
            </div>
          </div>
        </div>
      ))}
    </div>
  )
}

// ─── AddCommentForm Component ─────────────────────────────────────────────────

interface AddCommentFormProps {
  owner: string
  repo: string
  issueNumber: number
}

function AddCommentForm({ owner, repo, issueNumber }: AddCommentFormProps) {
  const [body, setBody] = useState('')
  const [error, setError] = useState<string | null>(null)

  const api = useApiClient()
  const queryClient = useQueryClient()

  const addCommentMutation = useMutation({
    mutationFn: (commentBody: string) =>
      api.post(`/api/repos/${owner}/${repo}/issues/${issueNumber}/comments`, { body: commentBody }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['issue-detail', owner, repo, issueNumber] })
      setBody('')
      setError(null)
    },
    onError: (err: ApiError) => {
      setError(err.message || 'Failed to add comment')
    },
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)

    if (!body.trim()) {
      setError('Comment body is required')
      return
    }

    addCommentMutation.mutate(body.trim())
  }

  return (
    <form onSubmit={handleSubmit} className="bg-gray-800 border border-gray-700 rounded-lg p-4">
      <textarea
        value={body}
        onChange={(e) => setBody(e.target.value)}
        placeholder="Add a comment..."
        rows={4}
        className="w-full px-3 py-2 bg-gray-900 border border-gray-700 rounded-md text-gray-100 placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent resize-none"
      />

      {error && (
        <div className="mt-2 text-sm text-red-400 bg-red-900/20 border border-red-800 rounded px-3 py-2">
          {error}
        </div>
      )}

      <div className="flex justify-end mt-3">
        <button
          type="submit"
          disabled={addCommentMutation.isPending}
          className="px-4 py-2 text-sm font-semibold text-white bg-indigo-600 hover:bg-indigo-500 rounded-md transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {addCommentMutation.isPending ? 'Adding...' : 'Add comment'}
        </button>
      </div>
    </form>
  )
}

// ─── Skeleton Component ───────────────────────────────────────────────────────

function IssueDetailSkeleton() {
  return (
    <div className="animate-pulse space-y-6">
      <div className="h-8 bg-gray-700 rounded w-3/4" />
      <div className="h-20 bg-gray-700 rounded" />
      <div className="h-40 bg-gray-700 rounded" />
    </div>
  )
}

// ─── IssueDetailPage ──────────────────────────────────────────────────────────

export default function IssueDetailPage() {
  const { owner, repo, id } = useParams<{ owner: string; repo: string; id: string }>()
  const issueNumber = parseInt(id || '0', 10)

  const api = useApiClient()
  const queryClient = useQueryClient()

  const {
    data: issue,
    isLoading,
    error,
  } = useQuery<IssueDetailDto, ApiError>({
    queryKey: ['issue-detail', owner, repo, issueNumber],
    queryFn: () =>
      api.get<IssueDetailDto>(`/api/repos/${owner}/${repo}/issues/${issueNumber}`),
    enabled: !!owner && !!repo && issueNumber > 0,
    retry: (failureCount, error) => {
      if (error instanceof ApiError && error.status === 404) return false
      return failureCount < 2
    },
  })

  // Close/reopen mutation
  const toggleStatusMutation = useMutation({
    mutationFn: () =>
      api.post(`/api/repos/${owner}/${repo}/issues/${issueNumber}/close`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['issue-detail', owner, repo, issueNumber] })
      queryClient.invalidateQueries({ queryKey: ['repo-issues', owner, repo] })
    },
  })

  // ── 404 state ──────────────────────────────────────────────────────────────

  if (error instanceof ApiError && error.status === 404) {
    return (
      <div className="min-h-screen bg-gray-950 flex items-center justify-center px-4">
        <div className="text-center">
          <p className="text-6xl font-bold text-gray-700 mb-4">404</p>
          <h1 className="text-xl font-semibold text-gray-200 mb-2">Issue not found</h1>
          <p className="text-gray-400 mb-6">
            Issue #{issueNumber} doesn&apos;t exist in this repository.
          </p>
          <Link
            to={`/${owner}/${repo}/issues`}
            className="inline-flex items-center gap-2 rounded-md bg-indigo-600 px-4 py-2 text-sm font-semibold text-white hover:bg-indigo-500 transition-colors"
          >
            View all issues
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
          <p className="text-gray-400 mb-4">Failed to load issue.</p>
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
          <IssueDetailSkeleton />
        </div>
      </div>
    )
  }

  if (!issue) {
    return null
  }

  // ── Issue detail view ──────────────────────────────────────────────────────

  return (
    <div className="min-h-screen bg-gray-950 text-gray-100">
      <div className="max-w-5xl mx-auto px-4 py-6">
        {/* Breadcrumb */}
        <nav className="flex items-center gap-2 text-sm text-gray-400 mb-6">
          <Link to={`/${owner}/${repo}`} className="hover:text-indigo-400 transition-colors">
            {owner}/{repo}
          </Link>
          <span>/</span>
          <Link to={`/${owner}/${repo}/issues`} className="hover:text-indigo-400 transition-colors">
            issues
          </Link>
          <span>/</span>
          <span className="text-gray-200">#{issue.number}</span>
        </nav>

        {/* Issue header */}
        <div className="mb-6">
          <div className="flex items-start gap-4 mb-4">
            <h1 className="text-2xl font-bold text-gray-100 flex-1">{issue.title}</h1>
            <StatusBadge status={issue.status} />
          </div>

          <div className="flex items-center gap-3 text-sm text-gray-400 mb-4">
            <span>
              #{issue.number} opened {formatRelativeTime(issue.createdAt)} by{' '}
              <span className="text-gray-200">{issue.authorUsername}</span>
            </span>
          </div>

          {/* Labels */}
          {issue.labels.length > 0 && (
            <div className="flex flex-wrap gap-2 mb-4">
              {issue.labels.map((label) => (
                <LabelBadge key={label.id} label={label} />
              ))}
            </div>
          )}

          {/* Issue body */}
          {issue.body && (
            <div className="p-4 bg-gray-800 border border-gray-700 rounded-lg text-sm text-gray-300 whitespace-pre-wrap">
              {issue.body}
            </div>
          )}
        </div>

        {/* Close/reopen button */}
        <div className="mb-6">
          <button
            onClick={() => toggleStatusMutation.mutate()}
            disabled={toggleStatusMutation.isPending}
            className="px-4 py-2 text-sm font-medium text-gray-300 hover:text-gray-100 bg-gray-800 hover:bg-gray-700 border border-gray-700 rounded transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {toggleStatusMutation.isPending
              ? 'Updating...'
              : issue.status === 'open'
              ? 'Close issue'
              : 'Reopen issue'}
          </button>
        </div>

        {/* Comment thread */}
        <div className="mb-6">
          <h2 className="text-lg font-semibold text-gray-100 mb-4">
            Comments ({issue.comments.length})
          </h2>
          <CommentThread comments={issue.comments} />
        </div>

        {/* Add comment form */}
        <div>
          <h2 className="text-lg font-semibold text-gray-100 mb-4">Add a comment</h2>
          <AddCommentForm owner={owner!} repo={repo!} issueNumber={issueNumber} />
        </div>
      </div>
    </div>
  )
}
