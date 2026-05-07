/**
 * BranchListPage — branch management at route /:owner/:repo/branches
 *
 * Displays:
 * - List of all branches with head SHA, protection badge, last-commit date
 * - Create branch button (opens modal with name + source ref inputs)
 * - Delete button for each branch (disabled for protected branches)
 * - Toggle protection button (OWNER only)
 *
 * Features:
 * - Branch creation modal with validation
 * - Branch deletion with confirmation
 * - Protection toggle (requires OWNER role)
 * - Visual indicators for protected branches
 * - Loading states and error handling
 */

import { useParams, Link } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useApiClient, ApiError } from '../api/client'
import { useAuth } from '../context/AuthContext'
import { useState } from 'react'

// ─── Types ────────────────────────────────────────────────────────────────────

interface BranchDto {
  id: number
  name: string
  headSha: string
  protected: boolean
  createdAt: string
  lastCommitDate: string | null
}

interface CreateBranchRequest {
  name: string
  sourceSha: string
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

/** Get short SHA (first 7 characters). */
function shortSha(sha: string): string {
  return sha.slice(0, 7)
}

/** Format a timestamp to a readable date string. */
function formatDate(timestamp: string): string {
  const date = new Date(timestamp)
  return date.toLocaleDateString('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  })
}

// ─── CreateBranchModal Component ──────────────────────────────────────────────

interface CreateBranchModalProps {
  owner: string
  repo: string
  branches: BranchDto[]
  onClose: () => void
  onSuccess: () => void
}

function CreateBranchModal({
  owner,
  repo,
  branches,
  onClose,
  onSuccess,
}: CreateBranchModalProps) {
  const [name, setName] = useState('')
  const [sourceRef, setSourceRef] = useState(branches[0]?.name || 'main')
  const [error, setError] = useState<string | null>(null)

  const api = useApiClient()
  const queryClient = useQueryClient()

  const createMutation = useMutation({
    mutationFn: (request: CreateBranchRequest) =>
      api.post(`/api/repos/${owner}/${repo}/branches`, request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['repo-branches', owner, repo] })
      onSuccess()
      onClose()
    },
    onError: (err: ApiError) => {
      setError(err.message || 'Failed to create branch')
    },
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)

    if (!name.trim()) {
      setError('Branch name is required')
      return
    }

    // Find the source branch to get its head SHA
    const sourceBranch = branches.find((b) => b.name === sourceRef)
    if (!sourceBranch) {
      setError('Source branch not found')
      return
    }

    createMutation.mutate({
      name: name.trim(),
      sourceSha: sourceBranch.headSha,
    })
  }

  return (
    <div
      className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 px-4"
      onClick={onClose}
    >
      <div
        className="bg-gray-800 border border-gray-700 rounded-lg p-6 max-w-md w-full"
        onClick={(e) => e.stopPropagation()}
      >
        <h2 className="text-xl font-bold text-gray-100 mb-4">Create new branch</h2>

        <form onSubmit={handleSubmit} className="space-y-4">
          {/* Branch name input */}
          <div>
            <label
              htmlFor="branch-name"
              className="block text-sm font-medium text-gray-300 mb-2"
            >
              Branch name
            </label>
            <input
              id="branch-name"
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="feature/my-feature"
              className="w-full px-3 py-2 bg-gray-900 border border-gray-700 rounded-md text-gray-100 placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
              autoFocus
            />
          </div>

          {/* Source ref select */}
          <div>
            <label
              htmlFor="source-ref"
              className="block text-sm font-medium text-gray-300 mb-2"
            >
              Source branch
            </label>
            <select
              id="source-ref"
              value={sourceRef}
              onChange={(e) => setSourceRef(e.target.value)}
              className="w-full px-3 py-2 bg-gray-900 border border-gray-700 rounded-md text-gray-100 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
            >
              {branches.map((branch) => (
                <option key={branch.id} value={branch.name}>
                  {branch.name}
                </option>
              ))}
            </select>
          </div>

          {/* Error message */}
          {error && (
            <div className="text-sm text-red-400 bg-red-900/20 border border-red-800 rounded px-3 py-2">
              {error}
            </div>
          )}

          {/* Action buttons */}
          <div className="flex gap-3 justify-end pt-2">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 text-sm font-medium text-gray-300 hover:text-gray-100 transition-colors"
              disabled={createMutation.isPending}
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={createMutation.isPending}
              className="px-4 py-2 text-sm font-semibold text-white bg-indigo-600 hover:bg-indigo-500 rounded-md transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {createMutation.isPending ? 'Creating...' : 'Create branch'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

// ─── DeleteConfirmModal Component ─────────────────────────────────────────────

interface DeleteConfirmModalProps {
  branchName: string
  onConfirm: () => void
  onCancel: () => void
  isDeleting: boolean
}

function DeleteConfirmModal({
  branchName,
  onConfirm,
  onCancel,
  isDeleting,
}: DeleteConfirmModalProps) {
  return (
    <div
      className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 px-4"
      onClick={onCancel}
    >
      <div
        className="bg-gray-800 border border-gray-700 rounded-lg p-6 max-w-md w-full"
        onClick={(e) => e.stopPropagation()}
      >
        <h2 className="text-xl font-bold text-gray-100 mb-4">Delete branch</h2>
        <p className="text-sm text-gray-300 mb-6">
          Are you sure you want to delete the branch{' '}
          <span className="font-mono text-indigo-400">{branchName}</span>? This
          action cannot be undone.
        </p>

        <div className="flex gap-3 justify-end">
          <button
            type="button"
            onClick={onCancel}
            className="px-4 py-2 text-sm font-medium text-gray-300 hover:text-gray-100 transition-colors"
            disabled={isDeleting}
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={onConfirm}
            disabled={isDeleting}
            className="px-4 py-2 text-sm font-semibold text-white bg-red-600 hover:bg-red-500 rounded-md transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {isDeleting ? 'Deleting...' : 'Delete branch'}
          </button>
        </div>
      </div>
    </div>
  )
}

// ─── BranchRow Component ──────────────────────────────────────────────────────

interface BranchRowProps {
  branch: BranchDto
  owner: string
  repo: string
  isOwner: boolean
  onDeleteClick: (branch: BranchDto) => void
  onToggleProtection: (branch: BranchDto) => void
}

function BranchRow({
  branch,
  owner,
  repo,
  isOwner,
  onDeleteClick,
  onToggleProtection,
}: BranchRowProps) {
  return (
    <div className="flex items-center gap-4 px-4 py-3 hover:bg-gray-700 transition-colors border-b border-gray-700 last:border-b-0">
      {/* Branch name */}
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2 mb-1">
          <Link
            to={`/${owner}/${repo}/tree/${branch.name}`}
            className="text-sm font-medium text-indigo-400 hover:text-indigo-300 transition-colors truncate"
          >
            {branch.name}
          </Link>
          {branch.protected && (
            <span className="inline-flex items-center gap-1 px-2 py-0.5 text-xs font-medium text-yellow-300 bg-yellow-900/30 border border-yellow-700 rounded">
              <svg
                className="w-3 h-3"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z"
                />
              </svg>
              Protected
            </span>
          )}
        </div>
        <div className="flex items-center gap-3 text-xs text-gray-500">
          {branch.lastCommitDate ? (
            <span>Last commit {formatDate(branch.lastCommitDate)}</span>
          ) : (
            <span>No commits yet</span>
          )}
        </div>
      </div>

      {/* Head SHA */}
      <div className="flex-shrink-0">
        <Link
          to={`/${owner}/${repo}/commit/${branch.headSha}`}
          className="font-mono text-xs text-gray-400 hover:text-indigo-400 transition-colors px-2 py-1 rounded bg-gray-900 border border-gray-700 hover:border-indigo-500"
          title={branch.headSha}
        >
          {shortSha(branch.headSha)}
        </Link>
      </div>

      {/* Actions */}
      <div className="flex-shrink-0 flex items-center gap-2">
        {/* Toggle protection (OWNER only) */}
        {isOwner && (
          <button
            onClick={() => onToggleProtection(branch)}
            className="px-3 py-1 text-xs font-medium text-gray-300 hover:text-gray-100 bg-gray-900 hover:bg-gray-700 border border-gray-700 rounded transition-colors"
            title={
              branch.protected ? 'Remove protection' : 'Protect this branch'
            }
          >
            {branch.protected ? 'Unprotect' : 'Protect'}
          </button>
        )}

        {/* Delete button */}
        <button
          onClick={() => onDeleteClick(branch)}
          disabled={branch.protected}
          className="px-3 py-1 text-xs font-medium text-red-400 hover:text-red-300 bg-red-900/20 hover:bg-red-900/30 border border-red-800 rounded transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          title={
            branch.protected
              ? 'Cannot delete protected branch'
              : 'Delete this branch'
          }
        >
          Delete
        </button>
      </div>
    </div>
  )
}

// ─── Skeleton Component ───────────────────────────────────────────────────────

function BranchListSkeleton() {
  return (
    <div className="animate-pulse divide-y divide-gray-700">
      {[...Array(5)].map((_, i) => (
        <div key={i} className="flex items-center gap-4 px-4 py-3">
          <div className="flex-1 space-y-2">
            <div className="h-4 bg-gray-700 rounded w-1/3" />
            <div className="h-3 bg-gray-700 rounded w-1/4" />
          </div>
          <div className="w-16 h-6 bg-gray-700 rounded" />
          <div className="w-20 h-6 bg-gray-700 rounded" />
          <div className="w-16 h-6 bg-gray-700 rounded" />
        </div>
      ))}
    </div>
  )
}

// ─── BranchListPage ───────────────────────────────────────────────────────────

export default function BranchListPage() {
  const { owner, repo } = useParams<{ owner: string; repo: string }>()
  const [showCreateModal, setShowCreateModal] = useState(false)
  const [branchToDelete, setBranchToDelete] = useState<BranchDto | null>(null)
  const { user } = useAuth()

  const api = useApiClient()
  const queryClient = useQueryClient()

  // Fetch branches
  const {
    data: branches,
    isLoading,
    error,
  } = useQuery<BranchDto[], ApiError>({
    queryKey: ['repo-branches', owner, repo],
    queryFn: () => api.get<BranchDto[]>(`/api/repos/${owner}/${repo}/branches`),
    enabled: !!owner && !!repo,
    retry: (failureCount, error) => {
      if (error instanceof ApiError && error.status === 404) return false
      return failureCount < 2
    },
  })

  // Delete branch mutation
  const deleteMutation = useMutation({
    mutationFn: (branchName: string) =>
      api.delete(`/api/repos/${owner}/${repo}/branches/${branchName}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['repo-branches', owner, repo] })
      setBranchToDelete(null)
    },
  })

  // Toggle protection mutation
  const toggleProtectionMutation = useMutation({
    mutationFn: ({ branchName, protect }: { branchName: string; protect: boolean }) =>
      api.patch(`/api/repos/${owner}/${repo}/branches/${branchName}/protect`, {
        protected: protect,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['repo-branches', owner, repo] })
    },
  })

  // Check if current user is the owner
  const isOwner = user?.username === owner

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
          <p className="text-gray-400 mb-4">Failed to load branches.</p>
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
          {/* Branch list skeleton */}
          <div className="bg-gray-800 border border-gray-700 rounded-lg overflow-hidden">
            <BranchListSkeleton />
          </div>
        </div>
      </div>
    )
  }

  if (!branches || branches.length === 0) {
    return (
      <div className="min-h-screen bg-gray-950">
        <div className="max-w-5xl mx-auto px-4 py-6">
          {/* Header */}
          <div className="mb-6">
            <h1 className="text-2xl font-bold text-gray-100 mb-2">Branches</h1>
            <nav className="flex items-center gap-2 text-sm text-gray-400">
              <Link
                to={`/${owner}/${repo}`}
                className="hover:text-indigo-400 transition-colors"
              >
                {owner}/{repo}
              </Link>
              <span>/</span>
              <span className="text-gray-200">branches</span>
            </nav>
          </div>

          {/* Empty state */}
          <div className="bg-gray-800 border border-gray-700 rounded-lg p-8 text-center">
            <p className="text-sm text-gray-500">No branches found.</p>
          </div>
        </div>
      </div>
    )
  }

  // ── Branch list view ───────────────────────────────────────────────────────

  return (
    <div className="min-h-screen bg-gray-950 text-gray-100">
      <div className="max-w-5xl mx-auto px-4 py-6">
        {/* Header */}
        <div className="mb-6 flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold text-gray-100 mb-2">Branches</h1>
            <nav className="flex items-center gap-2 text-sm text-gray-400">
              <Link
                to={`/${owner}/${repo}`}
                className="hover:text-indigo-400 transition-colors"
              >
                {owner}/{repo}
              </Link>
              <span>/</span>
              <span className="text-gray-200">branches</span>
            </nav>
            <p className="text-sm text-gray-500 mt-2">
              {branches.length} {branches.length === 1 ? 'branch' : 'branches'}
            </p>
          </div>

          {/* Create branch button */}
          <button
            onClick={() => setShowCreateModal(true)}
            className="px-4 py-2 text-sm font-semibold text-white bg-indigo-600 hover:bg-indigo-500 rounded-md transition-colors"
          >
            New branch
          </button>
        </div>

        {/* Branch list */}
        <div className="bg-gray-800 border border-gray-700 rounded-lg overflow-hidden">
          {branches.map((branch) => (
            <BranchRow
              key={branch.id}
              branch={branch}
              owner={owner!}
              repo={repo!}
              isOwner={isOwner}
              onDeleteClick={setBranchToDelete}
              onToggleProtection={(b) =>
                toggleProtectionMutation.mutate({
                  branchName: b.name,
                  protect: !b.protected,
                })
              }
            />
          ))}
        </div>
      </div>

      {/* Create branch modal */}
      {showCreateModal && (
        <CreateBranchModal
          owner={owner!}
          repo={repo!}
          branches={branches}
          onClose={() => setShowCreateModal(false)}
          onSuccess={() => {
            // Success feedback could be added here
          }}
        />
      )}

      {/* Delete confirmation modal */}
      {branchToDelete && (
        <DeleteConfirmModal
          branchName={branchToDelete.name}
          onConfirm={() => deleteMutation.mutate(branchToDelete.name)}
          onCancel={() => setBranchToDelete(null)}
          isDeleting={deleteMutation.isPending}
        />
      )}
    </div>
  )
}
