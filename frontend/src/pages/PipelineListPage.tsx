/**
 * PipelineListPage — pipeline runs at route /:owner/:repo/pipelines
 *
 * Displays:
 * - Paginated list of pipeline runs
 * - Each row: commit SHA (link), status badge, triggered-at timestamp, duration
 * - Click row expands stage detail with per-stage status badges and timing
 */

import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useApiClient, ApiError } from '../api/client'

// ─── Types ────────────────────────────────────────────────────────────────────

type PipelineStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILURE'

interface PipelineStage {
  name: string
  status: PipelineStatus
  startedAt: string | null
  finishedAt: string | null
  log?: string
}

interface PipelineRun {
  id: number
  repoId: number
  commitSha: string
  status: PipelineStatus
  stagesJson: { stages: PipelineStage[] } | null
  startedAt: string | null
  finishedAt: string | null
  createdAt: string
}

interface PipelinePage {
  content: PipelineRun[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

/** Format a timestamp to a human-readable relative time. */
function formatRelativeTime(timestamp: string): string {
  const now = new Date()
  const then = new Date(timestamp)
  const seconds = Math.floor((now.getTime() - then.getTime()) / 1000)

  if (seconds < 60) return 'just now'
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m ago`
  if (seconds < 86400) return `${Math.floor(seconds / 3600)}h ago`
  return `${Math.floor(seconds / 86400)}d ago`
}

/** Format a timestamp to a short absolute date/time. */
function formatDateTime(timestamp: string): string {
  return new Date(timestamp).toLocaleString(undefined, {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

/** Compute duration string between two ISO timestamps. */
function formatDuration(startedAt: string | null, finishedAt: string | null): string {
  if (!startedAt) return '—'
  const start = new Date(startedAt).getTime()
  const end = finishedAt ? new Date(finishedAt).getTime() : Date.now()
  const seconds = Math.floor((end - start) / 1000)
  if (seconds < 60) return `${seconds}s`
  const mins = Math.floor(seconds / 60)
  const secs = seconds % 60
  return `${mins}m ${secs}s`
}

/** Shorten a SHA to 7 characters. */
function shortSha(sha: string): string {
  return sha.slice(0, 7)
}

// ─── StatusBadge Component ────────────────────────────────────────────────────

interface StatusBadgeProps {
  status: PipelineStatus
  size?: 'sm' | 'xs'
}

function StatusBadge({ status, size = 'sm' }: StatusBadgeProps) {
  const config: Record<PipelineStatus, { label: string; className: string; dot: string }> = {
    PENDING: {
      label: 'Pending',
      className: 'bg-gray-700 text-gray-300 border-gray-600',
      dot: 'bg-gray-400',
    },
    RUNNING: {
      label: 'Running',
      className: 'bg-blue-900/40 text-blue-300 border-blue-700',
      dot: 'bg-blue-400 animate-pulse',
    },
    SUCCESS: {
      label: 'Success',
      className: 'bg-green-900/40 text-green-300 border-green-700',
      dot: 'bg-green-400',
    },
    FAILURE: {
      label: 'Failure',
      className: 'bg-red-900/40 text-red-300 border-red-700',
      dot: 'bg-red-400',
    },
  }

  const { label, className, dot } = config[status] ?? config.PENDING
  const textSize = size === 'xs' ? 'text-xs' : 'text-xs'
  const padding = size === 'xs' ? 'px-1.5 py-0.5' : 'px-2 py-1'

  return (
    <span className={`inline-flex items-center gap-1.5 font-medium border rounded ${textSize} ${padding} ${className}`}>
      <span className={`w-1.5 h-1.5 rounded-full flex-shrink-0 ${dot}`} aria-hidden="true" />
      {label}
    </span>
  )
}

// ─── StageDetail Component ────────────────────────────────────────────────────

interface StageDetailProps {
  stages: PipelineStage[]
}

function StageDetail({ stages }: StageDetailProps) {
  if (stages.length === 0) {
    return <p className="text-xs text-gray-500 py-2">No stage data available.</p>
  }

  return (
    <div className="mt-3 space-y-2">
      {stages.map((stage) => (
        <div
          key={stage.name}
          className="flex items-center justify-between gap-4 bg-gray-900 border border-gray-700 rounded px-3 py-2"
        >
          <div className="flex items-center gap-3 min-w-0">
            <StatusBadge status={stage.status} size="xs" />
            <span className="text-sm font-medium text-gray-200 capitalize">{stage.name}</span>
          </div>
          <div className="flex items-center gap-4 text-xs text-gray-500 flex-shrink-0">
            {stage.startedAt && (
              <span title={formatDateTime(stage.startedAt)}>
                Started {formatRelativeTime(stage.startedAt)}
              </span>
            )}
            <span className="font-mono">
              {formatDuration(stage.startedAt, stage.finishedAt)}
            </span>
          </div>
        </div>
      ))}
    </div>
  )
}

// ─── PipelineRow Component ────────────────────────────────────────────────────

interface PipelineRowProps {
  run: PipelineRun
  owner: string
  repo: string
}

function PipelineRow({ run, owner, repo }: PipelineRowProps) {
  const [expanded, setExpanded] = useState(false)

  const stages: PipelineStage[] = run.stagesJson?.stages ?? []

  return (
    <li className="bg-gray-800 border border-gray-700 rounded-lg overflow-hidden">
      {/* Main row — clickable to expand */}
      <button
        type="button"
        onClick={() => setExpanded((v) => !v)}
        aria-expanded={expanded}
        className="w-full text-left px-4 py-3 hover:bg-gray-750 transition-colors focus:outline-none focus:ring-2 focus:ring-inset focus:ring-indigo-500"
      >
        <div className="flex items-center justify-between gap-4">
          {/* Left: status + commit SHA */}
          <div className="flex items-center gap-3 min-w-0">
            <StatusBadge status={run.status} />
            <Link
              to={`/${owner}/${repo}/commit/${run.commitSha}`}
              onClick={(e) => e.stopPropagation()}
              className="font-mono text-sm text-indigo-400 hover:text-indigo-300 transition-colors flex-shrink-0"
              title={run.commitSha}
            >
              {shortSha(run.commitSha)}
            </Link>
            <span className="text-xs text-gray-500 truncate">
              Pipeline #{run.id}
            </span>
          </div>

          {/* Right: timing info + expand chevron */}
          <div className="flex items-center gap-4 flex-shrink-0">
            <div className="text-right hidden sm:block">
              <p className="text-xs text-gray-400" title={run.createdAt ? formatDateTime(run.createdAt) : ''}>
                {run.createdAt ? formatRelativeTime(run.createdAt) : '—'}
              </p>
              <p className="text-xs text-gray-500 font-mono">
                {formatDuration(run.startedAt, run.finishedAt)}
              </p>
            </div>
            <svg
              className={`w-4 h-4 text-gray-400 transition-transform flex-shrink-0 ${expanded ? 'rotate-180' : ''}`}
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
              aria-hidden="true"
            >
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
            </svg>
          </div>
        </div>
      </button>

      {/* Expanded stage detail */}
      {expanded && (
        <div className="px-4 pb-4 border-t border-gray-700">
          <StageDetail stages={stages} />
        </div>
      )}
    </li>
  )
}

// ─── Skeleton Component ───────────────────────────────────────────────────────

function PipelineListSkeleton() {
  return (
    <div className="space-y-3 animate-pulse" aria-busy="true" aria-label="Loading pipelines">
      {[1, 2, 3, 4, 5].map((i) => (
        <div key={i} className="h-14 bg-gray-800 rounded-lg border border-gray-700" />
      ))}
    </div>
  )
}

// ─── PipelineListPage ─────────────────────────────────────────────────────────

const PAGE_SIZE = 20

export default function PipelineListPage() {
  const { owner, repo } = useParams<{ owner: string; repo: string }>()
  const [page, setPage] = useState(0)

  const api = useApiClient()

  const { data, isLoading, error } = useQuery<PipelinePage, ApiError>({
    queryKey: ['pipelines', owner, repo, page],
    queryFn: () =>
      api.get<PipelinePage>(
        `/api/repos/${owner}/${repo}/pipelines?page=${page}&size=${PAGE_SIZE}`
      ),
    enabled: !!owner && !!repo,
    // Refetch every 5s while any run is PENDING or RUNNING
    refetchInterval: (query) => {
      const runs = query.state.data?.content ?? []
      const hasActive = runs.some((r) => r.status === 'PENDING' || r.status === 'RUNNING')
      return hasActive ? 5000 : false
    },
  })

  const runs = data?.content ?? []
  const totalPages = data?.totalPages ?? 0
  const totalElements = data?.totalElements ?? 0

  // ── Error state ────────────────────────────────────────────────────────────

  if (error) {
    return (
      <div className="min-h-screen bg-gray-950 flex items-center justify-center px-4">
        <div className="text-center">
          <p className="text-gray-400 mb-2">Failed to load pipelines.</p>
          <p className="text-sm text-red-400">
            {error instanceof Error ? error.message : 'Unknown error'}
          </p>
        </div>
      </div>
    )
  }

  // ── Main view ──────────────────────────────────────────────────────────────

  return (
    <div className="min-h-screen bg-gray-950 text-gray-100">
      <div className="max-w-4xl mx-auto px-4 py-6">

        {/* Breadcrumb */}
        <nav className="flex items-center gap-2 text-sm text-gray-400 mb-6" aria-label="Breadcrumb">
          <Link to={`/${owner}/${repo}`} className="hover:text-indigo-400 transition-colors">
            {owner}/{repo}
          </Link>
          <span aria-hidden="true">/</span>
          <span className="text-gray-200">pipelines</span>
        </nav>

        {/* Header */}
        <div className="flex items-center justify-between mb-6">
          <div>
            <h1 className="text-2xl font-bold text-gray-100">Pipelines</h1>
            {!isLoading && (
              <p className="text-sm text-gray-400 mt-1">
                {totalElements} {totalElements === 1 ? 'run' : 'runs'} total
              </p>
            )}
          </div>
        </div>

        {/* Pipeline list */}
        {isLoading ? (
          <PipelineListSkeleton />
        ) : runs.length === 0 ? (
          <div className="bg-gray-800 border border-gray-700 rounded-lg p-12 text-center">
            <svg
              className="w-12 h-12 text-gray-600 mx-auto mb-4"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
              aria-hidden="true"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={1.5}
                d="M9 3H5a2 2 0 00-2 2v4m6-6h10a2 2 0 012 2v4M9 3v18m0 0h10a2 2 0 002-2V9M9 21H5a2 2 0 01-2-2V9m0 0h18"
              />
            </svg>
            <h2 className="text-lg font-semibold text-gray-300 mb-2">No pipeline runs yet</h2>
            <p className="text-sm text-gray-500">
              Pipeline runs are triggered automatically when you push commits to this repository.
            </p>
          </div>
        ) : (
          <ul className="space-y-2" aria-label="Pipeline runs">
            {runs.map((run) => (
              <PipelineRow key={run.id} run={run} owner={owner!} repo={repo!} />
            ))}
          </ul>
        )}

        {/* Pagination */}
        {totalPages > 1 && (
          <nav
            className="flex items-center justify-between mt-6"
            aria-label="Pipeline pagination"
          >
            <button
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              disabled={page === 0}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 text-sm font-medium text-gray-300 bg-gray-800 border border-gray-700 rounded-md hover:bg-gray-700 disabled:opacity-40 disabled:cursor-not-allowed transition-colors focus:outline-none focus:ring-2 focus:ring-indigo-500"
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
              </svg>
              Previous
            </button>

            <span className="text-sm text-gray-400">
              Page {page + 1} of {totalPages}
            </span>

            <button
              onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
              disabled={page >= totalPages - 1}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 text-sm font-medium text-gray-300 bg-gray-800 border border-gray-700 rounded-md hover:bg-gray-700 disabled:opacity-40 disabled:cursor-not-allowed transition-colors focus:outline-none focus:ring-2 focus:ring-indigo-500"
            >
              Next
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
              </svg>
            </button>
          </nav>
        )}
      </div>
    </div>
  )
}
