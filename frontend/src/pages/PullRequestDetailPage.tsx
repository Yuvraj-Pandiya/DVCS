/**
 * PullRequestDetailPage — PR detail at route /:owner/:repo/pulls/:id
 *
 * Displays:
 * - PR metadata: title, description, status, branches
 * - DiffViewer component showing changes
 * - Review timeline with reviews and comments
 * - Inline comment support
 * - Merge controls (merge button with strategy selection)
 *
 * Features:
 * - Submit review (approve, request changes, comment)
 * - Add inline comments on diff lines
 * - Merge PR with strategy selection (merge, squash, rebase)
 * - Close/reopen PR
 * - Loading states and error handling
 */

import { useParams, Link } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useApiClient, ApiError } from '../api/client'
import DiffViewer, { DiffHunk } from '../components/DiffViewer'
import { useState } from 'react'

// ─── Types ────────────────────────────────────────────────────────────────────

interface PullRequestDetailDto {
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
  diff: DiffHunk[]
  reviews: ReviewDto[]
  comments: CommentDto[]
}

interface ReviewDto {
  id: number
  reviewerId: number
  reviewerUsername: string
  verdict: 'APPROVE' | 'CHANGES_REQUESTED' | 'COMMENT'
  body: string | null
  submittedAt: string
}

interface CommentDto {
  id: number
  authorId: number
  authorUsername: string
  body: string
  filePath: string | null
  lineNumber: number | null
  createdAt: string
}

type MergeStrategy = 'merge' | 'squash' | 'rebase'

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
  status: 'open' | 'closed' | 'merged'
}

function StatusBadge({ status }: StatusBadgeProps) {
  const config = {
    open: { label: 'Open', className: 'bg-green-900/30 text-green-300 border-green-700' },
    closed: { label: 'Closed', className: 'bg-red-900/30 text-red-300 border-red-700' },
    merged: { label: 'Merged', className: 'bg-purple-900/30 text-purple-300 border-purple-700' },
  }

  const { label, className } = config[status]

  return (
    <span className={`inline-flex items-center px-3 py-1 text-sm font-medium border rounded ${className}`}>
      {label}
    </span>
  )
}

// ─── ReviewTimeline Component ─────────────────────────────────────────────────

interface ReviewTimelineProps {
  reviews: ReviewDto[]
  comments: CommentDto[]
}

function ReviewTimeline({ reviews, comments }: ReviewTimelineProps) {
  // Combine and sort reviews and comments by timestamp
  const timeline = [
    ...reviews.map((r) => ({ type: 'review' as const, data: r, timestamp: r.submittedAt })),
    ...comments.map((c) => ({ type: 'comment' as const, data: c, timestamp: c.createdAt })),
  ].sort((a, b) => new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime())

  if (timeline.length === 0) {
    return (
      <div className="bg-gray-800 border border-gray-700 rounded-lg p-6 text-center">
        <p className="text-sm text-gray-500">No reviews or comments yet.</p>
      </div>
    )
  }

  return (
    <div className="space-y-4">
      {timeline.map((item, index) => {
        if (item.type === 'review') {
          const review = item.data as ReviewDto
          const verdictConfig = {
            APPROVE: { label: 'approved', className: 'text-green-400', icon: '✓' },
            CHANGES_REQUESTED: { label: 'requested changes', className: 'text-red-400', icon: '✗' },
            COMMENT: { label: 'commented', className: 'text-gray-400', icon: '💬' },
          }
          const { label, className, icon } = verdictConfig[review.verdict]

          return (
            <div key={`review-${review.id}`} className="bg-gray-800 border border-gray-700 rounded-lg p-4">
              <div className="flex items-start gap-3">
                <div className="w-8 h-8 rounded-full bg-indigo-700 flex items-center justify-center flex-shrink-0">
                  <span className="text-white font-semibold text-xs select-none">
                    {getInitials(review.reviewerUsername)}
                  </span>
                </div>
                <div className="flex-1">
                  <div className="text-sm mb-1">
                    <span className="font-semibold text-gray-200">{review.reviewerUsername}</span>{' '}
                    <span className={className}>
                      {icon} {label}
                    </span>{' '}
                    <span className="text-gray-500">{formatRelativeTime(review.submittedAt)}</span>
                  </div>
                  {review.body && (
                    <div className="text-sm text-gray-300 mt-2 whitespace-pre-wrap">{review.body}</div>
                  )}
                </div>
              </div>
            </div>
          )
        } else {
          const comment = item.data as CommentDto
          return (
            <div key={`comment-${comment.id}`} className="bg-gray-800 border border-gray-700 rounded-lg p-4">
              <div className="flex items-start gap-3">
                <div className="w-8 h-8 rounded-full bg-purple-700 flex items-center justify-center flex-shrink-0">
                  <span className="text-white font-semibold text-xs select-none">
                    {getInitials(comment.authorUsername)}
                  </span>
                </div>
                <div className="flex-1">
                  <div className="text-sm mb-1">
                    <span className="font-semibold text-gray-200">{comment.authorUsername}</span>{' '}
                    <span className="text-gray-500">commented {formatRelativeTime(comment.createdAt)}</span>
                    {comment.filePath && (
                      <span className="text-xs text-gray-600 ml-2">
                        on {comment.filePath}:{comment.lineNumber}
                      </span>
                    )}
                  </div>
                  <div className="text-sm text-gray-300 mt-2 whitespace-pre-wrap">{comment.body}</div>
                </div>
              </div>
            </div>
          )
        }
      })}
    </div>
  )
}

// ─── ReviewSubmitForm Component ───────────────────────────────────────────────

interface ReviewSubmitFormProps {
  owner: string
  repo: string
  prNumber: number
  onReviewSubmitted: () => void
}

function ReviewSubmitForm({ owner, repo, prNumber, onReviewSubmitted }: ReviewSubmitFormProps) {
  const [verdict, setVerdict] = useState<'APPROVE' | 'CHANGES_REQUESTED' | 'COMMENT'>('COMMENT')
  const [body, setBody] = useState('')
  const api = useApiClient()
  const queryClient = useQueryClient()

  const submitReviewMutation = useMutation({
    mutationFn: () =>
      api.post(`/api/repos/${owner}/${repo}/pulls/${prNumber}/review`, {
        verdict,
        body: body.trim() || null,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['pr-detail', owner, repo, prNumber] })
      setBody('')
      setVerdict('COMMENT')
      onReviewSubmitted()
    },
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    submitReviewMutation.mutate()
  }

  return (
    <div className="bg-gray-800 border border-gray-700 rounded-lg p-6 mb-6">
      <h3 className="text-lg font-semibold text-gray-100 mb-4">Submit review</h3>

      <form onSubmit={handleSubmit} className="space-y-4">
        {/* Verdict selector */}
        <div>
          <label className="block text-sm font-medium text-gray-300 mb-2">Review verdict</label>
          <select
            value={verdict}
            onChange={(e) => setVerdict(e.target.value as typeof verdict)}
            className="w-full px-3 py-2 bg-gray-900 border border-gray-700 rounded-md text-gray-100 focus:outline-none focus:ring-2 focus:ring-indigo-500"
            disabled={submitReviewMutation.isPending}
          >
            <option value="COMMENT">Comment</option>
            <option value="APPROVE">Approve</option>
            <option value="CHANGES_REQUESTED">Request changes</option>
          </select>
        </div>

        {/* Comment textarea */}
        <div>
          <label className="block text-sm font-medium text-gray-300 mb-2">
            Review comment {verdict === 'COMMENT' ? '(required)' : '(optional)'}
          </label>
          <textarea
            value={body}
            onChange={(e) => setBody(e.target.value)}
            placeholder="Leave a comment..."
            className="w-full px-3 py-2 bg-gray-900 border border-gray-700 rounded-md text-gray-100 focus:outline-none focus:ring-2 focus:ring-indigo-500 resize-none"
            rows={4}
            disabled={submitReviewMutation.isPending}
          />
        </div>

        {/* Submit button */}
        <button
          type="submit"
          disabled={submitReviewMutation.isPending || (verdict === 'COMMENT' && !body.trim())}
          className="w-full px-4 py-2 text-sm font-semibold text-white bg-green-600 hover:bg-green-500 rounded-md transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {submitReviewMutation.isPending ? 'Submitting...' : 'Submit review'}
        </button>

        {/* Error message */}
        {submitReviewMutation.isError && (
          <div className="p-3 bg-red-900/20 border border-red-700 rounded text-sm text-red-300">
            Failed to submit review. Please try again.
          </div>
        )}
      </form>
    </div>
  )
}

// ─── MergeControls Component ──────────────────────────────────────────────────

interface MergeControlsProps {
  pr: PullRequestDetailDto
  owner: string
  repo: string
  onMerge: (strategy: MergeStrategy) => void
  isMerging: boolean
}

function MergeControls({ pr, owner, repo, onMerge, isMerging }: MergeControlsProps) {
  const [strategy, setStrategy] = useState<MergeStrategy>('merge')

  if (pr.status !== 'open') {
    return null
  }

  // Check if PR is mergeable (at least one APPROVE, no CHANGES_REQUESTED)
  const hasApproval = pr.reviews.some((r) => r.verdict === 'APPROVE')
  const hasChangesRequested = pr.reviews.some((r) => r.verdict === 'CHANGES_REQUESTED')
  const isMergeable = hasApproval && !hasChangesRequested

  return (
    <div className="bg-gray-800 border border-gray-700 rounded-lg p-6">
      <h3 className="text-lg font-semibold text-gray-100 mb-4">Merge pull request</h3>

      {!isMergeable && (
        <div className="mb-4 p-3 bg-yellow-900/20 border border-yellow-700 rounded text-sm text-yellow-300">
          {!hasApproval && 'This PR requires at least one approval before merging.'}
          {hasChangesRequested && 'This PR has unresolved change requests.'}
        </div>
      )}

      <div className="space-y-4">
        {/* Strategy selection */}
        <div>
          <label className="block text-sm font-medium text-gray-300 mb-2">Merge strategy</label>
          <select
            value={strategy}
            onChange={(e) => setStrategy(e.target.value as MergeStrategy)}
            className="w-full px-3 py-2 bg-gray-900 border border-gray-700 rounded-md text-gray-100 focus:outline-none focus:ring-2 focus:ring-indigo-500"
            disabled={!isMergeable || isMerging}
          >
            <option value="merge">Create a merge commit</option>
            <option value="squash">Squash and merge</option>
            <option value="rebase">Rebase and merge</option>
          </select>
        </div>

        {/* Merge button */}
        <button
          onClick={() => onMerge(strategy)}
          disabled={!isMergeable || isMerging}
          className="w-full px-4 py-2 text-sm font-semibold text-white bg-indigo-600 hover:bg-indigo-500 rounded-md transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {isMerging ? 'Merging...' : `Merge pull request (#${pr.number})`}
        </button>
      </div>
    </div>
  )
}

// ─── Skeleton Component ───────────────────────────────────────────────────────

function PullRequestDetailSkeleton() {
  return (
    <div className="animate-pulse space-y-6">
      <div className="h-8 bg-gray-700 rounded w-3/4" />
      <div className="h-20 bg-gray-700 rounded" />
      <div className="h-64 bg-gray-700 rounded" />
    </div>
  )
}

// ─── PullRequestDetailPage ────────────────────────────────────────────────────

export default function PullRequestDetailPage() {
  const { owner, repo, id } = useParams<{ owner: string; repo: string; id: string }>()
  const prNumber = parseInt(id || '0', 10)

  const api = useApiClient()
  const queryClient = useQueryClient()

  const {
    data: pr,
    isLoading,
    error,
  } = useQuery<PullRequestDetailDto, ApiError>({
    queryKey: ['pr-detail', owner, repo, prNumber],
    queryFn: () =>
      api.get<PullRequestDetailDto>(`/api/repos/${owner}/${repo}/pulls/${prNumber}`),
    enabled: !!owner && !!repo && prNumber > 0,
    retry: (failureCount, error) => {
      if (error instanceof ApiError && error.status === 404) return false
      return failureCount < 2
    },
  })

  // Merge mutation
  const mergeMutation = useMutation({
    mutationFn: (strategy: MergeStrategy) =>
      api.post(`/api/repos/${owner}/${repo}/pulls/${prNumber}/merge?strategy=${strategy}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['pr-detail', owner, repo, prNumber] })
      queryClient.invalidateQueries({ queryKey: ['repo-pulls', owner, repo] })
    },
  })

  // ── 404 state ──────────────────────────────────────────────────────────────

  if (error instanceof ApiError && error.status === 404) {
    return (
      <div className="min-h-screen bg-gray-950 flex items-center justify-center px-4">
        <div className="text-center">
          <p className="text-6xl font-bold text-gray-700 mb-4">404</p>
          <h1 className="text-xl font-semibold text-gray-200 mb-2">Pull request not found</h1>
          <p className="text-gray-400 mb-6">
            Pull request #{prNumber} doesn&apos;t exist in this repository.
          </p>
          <Link
            to={`/${owner}/${repo}/pulls`}
            className="inline-flex items-center gap-2 rounded-md bg-indigo-600 px-4 py-2 text-sm font-semibold text-white hover:bg-indigo-500 transition-colors"
          >
            View all pull requests
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
          <p className="text-gray-400 mb-4">Failed to load pull request.</p>
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
        <div className="max-w-6xl mx-auto px-4 py-6">
          <PullRequestDetailSkeleton />
        </div>
      </div>
    )
  }

  if (!pr) {
    return null
  }

  // ── PR detail view ─────────────────────────────────────────────────────────

  return (
    <div className="min-h-screen bg-gray-950 text-gray-100">
      <div className="max-w-6xl mx-auto px-4 py-6">
        {/* Breadcrumb */}
        <nav className="flex items-center gap-2 text-sm text-gray-400 mb-6">
          <Link to={`/${owner}/${repo}`} className="hover:text-indigo-400 transition-colors">
            {owner}/{repo}
          </Link>
          <span>/</span>
          <Link to={`/${owner}/${repo}/pulls`} className="hover:text-indigo-400 transition-colors">
            pulls
          </Link>
          <span>/</span>
          <span className="text-gray-200">#{pr.number}</span>
        </nav>

        {/* PR header */}
        <div className="mb-6">
          <div className="flex items-start gap-4 mb-4">
            <h1 className="text-2xl font-bold text-gray-100 flex-1">{pr.title}</h1>
            <StatusBadge status={pr.status} />
          </div>

          <div className="flex items-center gap-3 text-sm text-gray-400">
            <span>
              #{pr.number} opened {formatRelativeTime(pr.createdAt)} by{' '}
              <span className="text-gray-200">{pr.authorUsername}</span>
            </span>
            <span>•</span>
            <span>
              <span className="font-mono text-indigo-400">{pr.headBranch}</span> →{' '}
              <span className="font-mono text-indigo-400">{pr.baseBranch}</span>
            </span>
          </div>

          {pr.body && (
            <div className="mt-4 p-4 bg-gray-800 border border-gray-700 rounded-lg text-sm text-gray-300 whitespace-pre-wrap">
              {pr.body}
            </div>
          )}
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* Main content (diff + timeline) */}
          <div className="lg:col-span-2 space-y-6">
            {/* Diff viewer */}
            <div>
              <h2 className="text-lg font-semibold text-gray-100 mb-4">Changes</h2>
              <DiffViewer 
                hunks={pr.diff} 
                comments={pr.comments}
                owner={owner}
                repo={repo}
                prNumber={prNumber}
              />
            </div>

            {/* Review timeline */}
            <div>
              <h2 className="text-lg font-semibold text-gray-100 mb-4">
                Reviews and comments
              </h2>
              <ReviewTimeline reviews={pr.reviews} comments={pr.comments} />
            </div>
          </div>

          {/* Sidebar (review form + merge controls) */}
          <div className="lg:col-span-1 space-y-6">
            {/* Review submit form (only for open PRs) */}
            {pr.status === 'open' && (
              <ReviewSubmitForm
                owner={owner!}
                repo={repo!}
                prNumber={prNumber}
                onReviewSubmitted={() => {
                  // Optionally show a success message
                }}
              />
            )}

            {/* Merge controls */}
            <MergeControls
              pr={pr}
              owner={owner!}
              repo={repo!}
              onMerge={(strategy) => mergeMutation.mutate(strategy)}
              isMerging={mergeMutation.isPending}
            />
          </div>
        </div>
      </div>
    </div>
  )
}
