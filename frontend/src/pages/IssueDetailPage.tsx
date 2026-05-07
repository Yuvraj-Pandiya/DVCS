/**
 * IssueDetailPage — issue detail at route /:owner/:repo/issues/:id
 *
 * Displays:
 * - Issue metadata: title, description (sanitized HTML), status, labels
 * - Comment thread (author avatar, body, timestamp)
 * - Add comment form
 * - Close/reopen issue button (author or WRITE/OWNER only)
 * - Label management sidebar
 *
 * Layout: two-column on desktop (main content left, sidebar right)
 */

import { useParams, Link } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useApiClient, ApiError } from '../api/client'
import { useAuth } from '../context/AuthContext'
import { useState } from 'react'

// ─── Types ────────────────────────────────────────────────────────────────────

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

// ─── Sanitizer ────────────────────────────────────────────────────────────────

/**
 * Simple HTML sanitizer that strips <script> tags and on* event attributes.
 * Used to safely render issue body HTML via dangerouslySetInnerHTML.
 * DOMPurify is not available in this project's dependencies.
 */
function sanitizeHtml(html: string): string {
  // Remove <script> blocks (including content)
  let sanitized = html.replace(/<script\b[^<]*(?:(?!<\/script>)<[^<]*)*<\/script>/gi, '')
  // Remove <iframe>, <object>, <embed>, <form> tags
  sanitized = sanitized.replace(/<\s*(iframe|object|embed|form|base|meta|link)[^>]*>/gi, '')
  // Remove on* event attributes (onclick, onload, onerror, etc.)
  sanitized = sanitized.replace(/\s+on\w+\s*=\s*(?:"[^"]*"|'[^']*'|[^\s>]*)/gi, '')
  // Remove javascript: hrefs
  sanitized = sanitized.replace(/href\s*=\s*(?:"javascript:[^"]*"|'javascript:[^']*')/gi, 'href="#"')
  return sanitized
}

/**
 * Converts plain text to simple HTML (preserves line breaks).
 * Used when the body doesn't appear to contain HTML markup.
 */
function textToHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/\n/g, '<br />')
}

/**
 * Determines if a string contains HTML markup.
 */
function looksLikeHtml(text: string): boolean {
  return /<[a-z][\s\S]*>/i.test(text)
}

/**
 * Prepares issue body for safe rendering.
 * If the body contains HTML, sanitize it. Otherwise, convert plain text to HTML.
 */
function prepareBodyHtml(body: string): string {
  if (looksLikeHtml(body)) {
    return sanitizeHtml(body)
  }
  return textToHtml(body)
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
  onRemove?: (labelId: number) => void
  isRemoving?: boolean
}

function LabelBadge({ label, onRemove, isRemoving }: LabelBadgeProps) {
  return (
    <span
      className="inline-flex items-center gap-1 px-2 py-1 text-xs font-medium rounded"
      style={{
        backgroundColor: `${label.color}20`,
        color: label.color,
        borderColor: label.color,
        borderWidth: '1px',
      }}
    >
      {label.name}
      {onRemove && (
        <button
          type="button"
          onClick={() => onRemove(label.id)}
          disabled={isRemoving}
          className="ml-0.5 opacity-70 hover:opacity-100 transition-opacity disabled:opacity-30"
          aria-label={`Remove label ${label.name}`}
          title={`Remove label ${label.name}`}
        >
          <svg className="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>
      )}
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
            {/* Author avatar */}
            <div
              className="w-8 h-8 rounded-full bg-purple-700 flex items-center justify-center flex-shrink-0"
              aria-label={`Avatar for ${comment.authorUsername}`}
            >
              <span className="text-white font-semibold text-xs select-none">
                {getInitials(comment.authorUsername)}
              </span>
            </div>
            <div className="flex-1 min-w-0">
              <div className="text-sm mb-2">
                <span className="font-semibold text-gray-200">{comment.authorUsername}</span>{' '}
                <span className="text-gray-500">commented {formatRelativeTime(comment.createdAt)}</span>
              </div>
              <div className="text-sm text-gray-300 whitespace-pre-wrap break-words">{comment.body}</div>
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
    onError: (err: unknown) => {
      setError(err instanceof ApiError ? err.message : 'Failed to add comment')
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
        aria-label="Comment body"
        className="w-full px-3 py-2 bg-gray-900 border border-gray-700 rounded-md text-gray-100 placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent resize-none"
      />

      {error && (
        <div className="mt-2 text-sm text-red-400 bg-red-900/20 border border-red-800 rounded px-3 py-2" role="alert">
          {error}
        </div>
      )}

      <div className="flex justify-end mt-3">
        <button
          type="submit"
          disabled={addCommentMutation.isPending || !body.trim()}
          className="px-4 py-2 text-sm font-semibold text-white bg-indigo-600 hover:bg-indigo-500 rounded-md transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {addCommentMutation.isPending ? 'Adding...' : 'Add comment'}
        </button>
      </div>
    </form>
  )
}

// ─── LabelSidebar Component ───────────────────────────────────────────────────

interface LabelSidebarProps {
  owner: string
  repo: string
  issueNumber: number
  currentLabels: LabelDto[]
}

function LabelSidebar({ owner, repo, issueNumber, currentLabels }: LabelSidebarProps) {
  const [showDropdown, setShowDropdown] = useState(false)
  const [applyError, setApplyError] = useState<string | null>(null)

  const api = useApiClient()
  const queryClient = useQueryClient()

  // Fetch all repo labels
  const { data: repoLabels = [], isLoading: labelsLoading } = useQuery<LabelDto[], ApiError>({
    queryKey: ['repo-labels', owner, repo],
    queryFn: () => api.get<LabelDto[]>(`/api/repos/${owner}/${repo}/labels`),
    retry: (failureCount, error) => {
      // Don't retry on 404 — endpoint may not exist
      if (error instanceof ApiError && (error.status === 404 || error.status === 405)) return false
      return failureCount < 1
    },
  })

  // Apply label mutation
  const applyLabelMutation = useMutation({
    mutationFn: (labelId: number) =>
      api.post(`/api/repos/${owner}/${repo}/issues/${issueNumber}/labels`, { labelId }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['issue-detail', owner, repo, issueNumber] })
      setApplyError(null)
      setShowDropdown(false)
    },
    onError: (err: unknown) => {
      setApplyError(err instanceof ApiError ? err.message : 'Failed to apply label')
    },
  })

  // Remove label mutation
  const removeLabelMutation = useMutation({
    mutationFn: (labelId: number) =>
      api.delete(`/api/repos/${owner}/${repo}/issues/${issueNumber}/labels/${labelId}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['issue-detail', owner, repo, issueNumber] })
    },
    onError: (err: unknown) => {
      setApplyError(err instanceof ApiError ? err.message : 'Failed to remove label')
    },
  })

  // Labels not yet applied to this issue
  const currentLabelIds = new Set(currentLabels.map((l) => l.id))
  const availableLabels = repoLabels.filter((l) => !currentLabelIds.has(l.id))

  return (
    <div className="bg-gray-800 border border-gray-700 rounded-lg p-4">
      <div className="flex items-center justify-between mb-3">
        <h3 className="text-sm font-semibold text-gray-200">Labels</h3>
        <button
          type="button"
          onClick={() => setShowDropdown((v) => !v)}
          className="text-gray-400 hover:text-gray-200 transition-colors"
          aria-label="Manage labels"
          aria-expanded={showDropdown}
        >
          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
          </svg>
        </button>
      </div>

      {/* Current labels */}
      {currentLabels.length === 0 ? (
        <p className="text-xs text-gray-500 mb-3">No labels applied.</p>
      ) : (
        <div className="flex flex-wrap gap-1.5 mb-3">
          {currentLabels.map((label) => (
            <LabelBadge
              key={label.id}
              label={label}
              onRemove={(id) => removeLabelMutation.mutate(id)}
              isRemoving={removeLabelMutation.isPending}
            />
          ))}
        </div>
      )}

      {/* Add label dropdown */}
      {showDropdown && (
        <div className="border-t border-gray-700 pt-3">
          <p className="text-xs text-gray-400 mb-2 font-medium">Apply a label</p>
          {labelsLoading ? (
            <p className="text-xs text-gray-500">Loading labels...</p>
          ) : availableLabels.length === 0 ? (
            <p className="text-xs text-gray-500">
              {repoLabels.length === 0
                ? 'No labels defined for this repository.'
                : 'All labels are already applied.'}
            </p>
          ) : (
            <div className="space-y-1 max-h-48 overflow-y-auto">
              {availableLabels.map((label) => (
                <button
                  key={label.id}
                  type="button"
                  onClick={() => applyLabelMutation.mutate(label.id)}
                  disabled={applyLabelMutation.isPending}
                  className="w-full flex items-center gap-2 px-2 py-1.5 rounded hover:bg-gray-700 transition-colors text-left disabled:opacity-50"
                >
                  <span
                    className="w-3 h-3 rounded-full flex-shrink-0"
                    style={{ backgroundColor: label.color }}
                    aria-hidden="true"
                  />
                  <span className="text-xs text-gray-300">{label.name}</span>
                </button>
              ))}
            </div>
          )}
        </div>
      )}

      {applyError && (
        <p className="mt-2 text-xs text-red-400" role="alert">{applyError}</p>
      )}
    </div>
  )
}

// ─── Skeleton Component ───────────────────────────────────────────────────────

function IssueDetailSkeleton() {
  return (
    <div className="animate-pulse space-y-6" aria-busy="true" aria-label="Loading issue">
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
  const { user, isAuthenticated } = useAuth()

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
          <p className="text-6xl font-bold text-gray-700 mb-4" aria-hidden="true">404</p>
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

  if (error) {
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

  // ── Permission check for close/reopen button ───────────────────────────────
  // Show the button if the current user is the issue author.
  // The backend also allows WRITE/OWNER collaborators — they'll get a 403 if
  // they don't have permission, but we show the button optimistically for all
  // authenticated users since we don't have a collaborator role endpoint here.
  const isAuthor = isAuthenticated && user?.id === issue.authorId
  const canToggleStatus = isAuthenticated && isAuthor

  // Safe defaults for optional fields the backend may not yet return
  const labels: LabelDto[] = issue.labels ?? []
  const comments: CommentDto[] = issue.comments ?? []

  // ── Issue detail view ──────────────────────────────────────────────────────

  return (
    <div className="min-h-screen bg-gray-950 text-gray-100">
      <div className="max-w-5xl mx-auto px-4 py-6">

        {/* Breadcrumb */}
        <nav className="flex items-center gap-2 text-sm text-gray-400 mb-6" aria-label="Breadcrumb">
          <Link to={`/${owner}/${repo}`} className="hover:text-indigo-400 transition-colors">
            {owner}/{repo}
          </Link>
          <span aria-hidden="true">/</span>
          <Link to={`/${owner}/${repo}/issues`} className="hover:text-indigo-400 transition-colors">
            issues
          </Link>
          <span aria-hidden="true">/</span>
          <span className="text-gray-200">#{issue.number}</span>
        </nav>

        {/* Issue header */}
        <div className="mb-6">
          <div className="flex items-start gap-4 mb-3">
            <h1 className="text-2xl font-bold text-gray-100 flex-1">{issue.title}</h1>
            <StatusBadge status={issue.status} />
          </div>

          <div className="flex items-center gap-3 text-sm text-gray-400">
            <span>
              #{issue.number} opened {formatRelativeTime(issue.createdAt)} by{' '}
              <span className="text-gray-200">{issue.authorUsername}</span>
            </span>
          </div>
        </div>

        {/* Two-column layout: main content + sidebar */}
        <div className="grid grid-cols-1 lg:grid-cols-4 gap-6">

          {/* ── Main content (left, wider) ─────────────────────────────────── */}
          <div className="lg:col-span-3 space-y-6">

            {/* Issue body */}
            {issue.body ? (
              <div className="bg-gray-800 border border-gray-700 rounded-lg overflow-hidden">
                {/* Header bar */}
                <div className="flex items-center gap-3 px-4 py-3 border-b border-gray-700 bg-gray-800/80">
                  <div
                    className="w-8 h-8 rounded-full bg-indigo-700 flex items-center justify-center flex-shrink-0"
                    aria-label={`Avatar for ${issue.authorUsername}`}
                  >
                    <span className="text-white font-semibold text-xs select-none">
                      {getInitials(issue.authorUsername)}
                    </span>
                  </div>
                  <div className="text-sm">
                    <span className="font-semibold text-gray-200">{issue.authorUsername}</span>{' '}
                    <span className="text-gray-500">opened this issue {formatRelativeTime(issue.createdAt)}</span>
                  </div>
                </div>
                {/* Body content — sanitized HTML */}
                <div
                  className="px-4 py-4 text-sm text-gray-300 prose prose-invert prose-sm max-w-none"
                  // eslint-disable-next-line react/no-danger
                  dangerouslySetInnerHTML={{ __html: prepareBodyHtml(issue.body) }}
                />
              </div>
            ) : (
              <div className="bg-gray-800 border border-gray-700 rounded-lg p-4 text-sm text-gray-500 italic">
                No description provided.
              </div>
            )}

            {/* Close/reopen button */}
            {canToggleStatus && (
              <div>
                <button
                  onClick={() => toggleStatusMutation.mutate()}
                  disabled={toggleStatusMutation.isPending}
                  className={`px-4 py-2 text-sm font-medium rounded transition-colors disabled:opacity-50 disabled:cursor-not-allowed border ${
                    issue.status === 'open'
                      ? 'text-red-300 hover:text-red-200 bg-red-900/20 hover:bg-red-900/30 border-red-800'
                      : 'text-green-300 hover:text-green-200 bg-green-900/20 hover:bg-green-900/30 border-green-800'
                  }`}
                >
                  {toggleStatusMutation.isPending
                    ? 'Updating...'
                    : issue.status === 'open'
                    ? 'Close issue'
                    : 'Reopen issue'}
                </button>
                {toggleStatusMutation.isError && (
                  <p className="mt-2 text-sm text-red-400" role="alert">
                    {toggleStatusMutation.error instanceof ApiError
                      ? toggleStatusMutation.error.message
                      : 'Failed to update issue status'}
                  </p>
                )}
              </div>
            )}

            {/* Comment thread */}
            <div>
              <h2 className="text-lg font-semibold text-gray-100 mb-4">
                Comments ({comments.length})
              </h2>
              <CommentThread comments={comments} />
            </div>

            {/* Add comment form */}
            {isAuthenticated && (
              <div>
                <h2 className="text-lg font-semibold text-gray-100 mb-4">Add a comment</h2>
                <AddCommentForm owner={owner!} repo={repo!} issueNumber={issueNumber} />
              </div>
            )}

            {!isAuthenticated && (
              <div className="bg-gray-800 border border-gray-700 rounded-lg p-4 text-sm text-gray-400 text-center">
                <Link to="/login" className="text-indigo-400 hover:text-indigo-300 transition-colors">
                  Sign in
                </Link>{' '}
                to leave a comment.
              </div>
            )}
          </div>

          {/* ── Sidebar (right, narrower) ──────────────────────────────────── */}
          <div className="lg:col-span-1 space-y-4">

            {/* Label management */}
            <LabelSidebar
              owner={owner!}
              repo={repo!}
              issueNumber={issueNumber}
              currentLabels={labels}
            />

            {/* Issue metadata */}
            <div className="bg-gray-800 border border-gray-700 rounded-lg p-4">
              <h3 className="text-sm font-semibold text-gray-200 mb-3">Details</h3>
              <dl className="space-y-2 text-xs">
                <div>
                  <dt className="text-gray-500 mb-0.5">Status</dt>
                  <dd>
                    <StatusBadge status={issue.status} />
                  </dd>
                </div>
                <div>
                  <dt className="text-gray-500 mb-0.5">Author</dt>
                  <dd className="flex items-center gap-1.5">
                    <div className="w-5 h-5 rounded-full bg-indigo-700 flex items-center justify-center flex-shrink-0">
                      <span className="text-white font-semibold select-none" style={{ fontSize: '0.6rem' }}>
                        {getInitials(issue.authorUsername)}
                      </span>
                    </div>
                    <span className="text-gray-300">{issue.authorUsername}</span>
                  </dd>
                </div>
                <div>
                  <dt className="text-gray-500 mb-0.5">Opened</dt>
                  <dd className="text-gray-300">{formatRelativeTime(issue.createdAt)}</dd>
                </div>
                <div>
                  <dt className="text-gray-500 mb-0.5">Comments</dt>
                  <dd className="text-gray-300">{comments.length}</dd>
                </div>
              </dl>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
