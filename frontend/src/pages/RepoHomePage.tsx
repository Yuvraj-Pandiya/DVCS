/**
 * RepoHomePage — repository home page at route /:owner/:repo
 *
 * Displays:
 * - Repository metadata (name, description, visibility, default branch)
 * - README markdown content (rendered with marked library)
 * - File tree preview (top-level entries only)
 * - Clone modal with HTTP and SSH URLs
 * - Repository statistics (commits, contributors, size)
 *
 * Handles loading skeleton, 404 (repo not found), and generic errors.
 */

import { useState } from 'react'
import { useParams, Link, useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useApiClient, ApiError } from '../api/client'
import { marked } from 'marked'

// ─── Types ────────────────────────────────────────────────────────────────────

interface Repository {
  id: number
  name: string
  description: string | null
  isPrivate: boolean
  defaultBranch: string
  ownerId: number
  ownerUsername: string
  createdAt: string
}

interface TreeEntry {
  name: string
  type: 'blob' | 'tree'
  size: number | null
  lastCommitSha: string | null
  lastCommitMessage: string | null
}

interface RepoStats {
  commitCount: number
  contributorCount: number
  sizeBytes: number
}

interface BlobContent {
  content: string // base64 encoded
  size: number
  encoding: string
  lastCommitSha: string | null
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

/** Format bytes to human-readable size (KB, MB, GB). */
function formatBytes(bytes: number): string {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return `${(bytes / Math.pow(k, i)).toFixed(1)} ${sizes[i]}`
}

/** Format a number with commas (e.g., 1234 → 1,234). */
function formatNumber(num: number): string {
  return num.toLocaleString('en-US')
}

/** Decode base64 content to UTF-8 string. */
function decodeBase64(base64: string): string {
  try {
    return atob(base64)
  } catch {
    return ''
  }
}

/** Get icon for file type. */
function getFileIcon(type: 'blob' | 'tree'): string {
  return type === 'tree' ? '📁' : '📄'
}

// ─── Clone Modal ──────────────────────────────────────────────────────────────

interface CloneModalProps {
  owner: string
  repo: string
  onClose: () => void
}

function CloneModal({ owner, repo, onClose }: CloneModalProps) {
  const [copied, setCopied] = useState<'http' | 'ssh' | null>(null)

  const httpUrl = `http://localhost/api/git/${owner}/${repo}`
  const sshUrl = `git@localhost:${owner}/${repo}.git`

  function copyToClipboard(text: string, type: 'http' | 'ssh') {
    navigator.clipboard.writeText(text).then(() => {
      setCopied(type)
      setTimeout(() => setCopied(null), 2000)
    })
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black bg-opacity-50"
      onClick={onClose}
      role="dialog"
      aria-modal="true"
      aria-labelledby="clone-modal-title"
    >
      <div
        className="bg-gray-800 border border-gray-700 rounded-lg p-6 max-w-lg w-full mx-4"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between mb-4">
          <h2 id="clone-modal-title" className="text-lg font-semibold text-gray-100">
            Clone Repository
          </h2>
          <button
            type="button"
            onClick={onClose}
            className="text-gray-400 hover:text-gray-200 transition-colors"
            aria-label="Close modal"
          >
            <svg
              xmlns="http://www.w3.org/2000/svg"
              viewBox="0 0 20 20"
              fill="currentColor"
              className="w-5 h-5"
            >
              <path d="M6.28 5.22a.75.75 0 0 0-1.06 1.06L8.94 10l-3.72 3.72a.75.75 0 1 0 1.06 1.06L10 11.06l3.72 3.72a.75.75 0 1 0 1.06-1.06L11.06 10l3.72-3.72a.75.75 0 0 0-1.06-1.06L10 8.94 6.28 5.22Z" />
            </svg>
          </button>
        </div>

        <div className="space-y-4">
          {/* HTTP URL */}
          <div>
            <label className="block text-sm font-medium text-gray-300 mb-2">
              HTTP
            </label>
            <div className="flex items-center gap-2">
              <input
                type="text"
                readOnly
                value={httpUrl}
                className="flex-1 px-3 py-2 bg-gray-900 border border-gray-600 rounded text-sm text-gray-200 font-mono"
              />
              <button
                type="button"
                onClick={() => copyToClipboard(httpUrl, 'http')}
                className="px-3 py-2 bg-indigo-600 text-white rounded text-sm hover:bg-indigo-500 transition-colors"
              >
                {copied === 'http' ? '✓' : 'Copy'}
              </button>
            </div>
          </div>

          {/* SSH URL */}
          <div>
            <label className="block text-sm font-medium text-gray-300 mb-2">
              SSH
            </label>
            <div className="flex items-center gap-2">
              <input
                type="text"
                readOnly
                value={sshUrl}
                className="flex-1 px-3 py-2 bg-gray-900 border border-gray-600 rounded text-sm text-gray-200 font-mono"
              />
              <button
                type="button"
                onClick={() => copyToClipboard(sshUrl, 'ssh')}
                className="px-3 py-2 bg-indigo-600 text-white rounded text-sm hover:bg-indigo-500 transition-colors"
              >
                {copied === 'ssh' ? '✓' : 'Copy'}
              </button>
            </div>
          </div>
        </div>

        <div className="mt-6 text-xs text-gray-500">
          <p>Use <code className="bg-gray-900 px-1 py-0.5 rounded">git clone</code> with either URL to clone this repository.</p>
        </div>
      </div>
    </div>
  )
}

// ─── Skeleton components ──────────────────────────────────────────────────────

function RepoHomeSkeleton() {
  return (
    <div className="animate-pulse p-6 space-y-6">
      {/* Header */}
      <div className="space-y-3">
        <div className="h-8 bg-gray-700 rounded w-64" />
        <div className="h-4 bg-gray-700 rounded w-96" />
      </div>

      {/* Stats */}
      <div className="flex gap-4">
        <div className="h-4 bg-gray-700 rounded w-24" />
        <div className="h-4 bg-gray-700 rounded w-24" />
        <div className="h-4 bg-gray-700 rounded w-24" />
      </div>

      {/* Content */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 space-y-4">
          <div className="h-64 bg-gray-700 rounded" />
        </div>
        <div className="space-y-4">
          <div className="h-32 bg-gray-700 rounded" />
        </div>
      </div>
    </div>
  )
}

// ─── RepoHomePage ─────────────────────────────────────────────────────────────

export default function RepoHomePage() {
  const { owner, repo } = useParams<{ owner: string; repo: string }>()
  const api = useApiClient()
  const [showCloneModal, setShowCloneModal] = useState(false)

  // Fetch repository metadata
  const {
    data: repository,
    isLoading: repoLoading,
    error: repoError,
  } = useQuery<Repository, ApiError>({
    queryKey: ['repo', owner, repo],
    queryFn: () => api.get<Repository>(`/api/repos/${owner}/${repo}`),
    enabled: !!owner && !!repo,
    retry: (failureCount, error) => {
      if (error instanceof ApiError && error.status === 404) return false
      return failureCount < 2
    },
  })

  // Fetch repository stats
  const { data: stats } = useQuery<RepoStats, ApiError>({
    queryKey: ['repo-stats', owner, repo],
    queryFn: () => api.get<RepoStats>(`/api/repos/${owner}/${repo}/stats`),
    enabled: !!owner && !!repo && !!repository,
    retry: false,
  })

  // Fetch top-level file tree
  const { data: tree } = useQuery<TreeEntry[], ApiError>({
    queryKey: ['repo-tree', owner, repo, repository?.defaultBranch],
    queryFn: () =>
      api.get<TreeEntry[]>(
        `/api/repos/${owner}/${repo}/tree/${repository?.defaultBranch || 'main'}/`
      ),
    enabled: !!owner && !!repo && !!repository,
    retry: false,
  })

  // Fetch README content
  const { data: readme } = useQuery<BlobContent, ApiError>({
    queryKey: ['repo-readme', owner, repo, repository?.defaultBranch],
    queryFn: async () => {
      // Try common README filenames
      const readmeNames = ['README.md', 'readme.md', 'Readme.md', 'README']
      for (const name of readmeNames) {
        try {
          return await api.get<BlobContent>(
            `/api/repos/${owner}/${repo}/blob/${repository?.defaultBranch || 'main'}/${name}`
          )
        } catch (err) {
          // Continue to next filename
          if (err instanceof ApiError && err.status === 404) continue
          throw err
        }
      }
      throw new ApiError(404, 'README not found')
    },
    enabled: !!owner && !!repo && !!repository,
    retry: false,
  })

  const isLoading = repoLoading

  // ── 404 state ──────────────────────────────────────────────────────────────

  if (repoError instanceof ApiError && repoError.status === 404) {
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
            Go home
          </Link>
        </div>
      </div>
    )
  }

  // ── Generic error state ────────────────────────────────────────────────────

  if (repoError && !(repoError instanceof ApiError && repoError.status === 404)) {
    return (
      <div className="min-h-screen bg-gray-950 flex items-center justify-center px-4">
        <div className="text-center">
          <p className="text-gray-400 mb-4">Failed to load repository.</p>
          <p className="text-sm text-red-400">
            {repoError instanceof Error ? repoError.message : 'Unknown error'}
          </p>
        </div>
      </div>
    )
  }

  // ── Loading state ──────────────────────────────────────────────────────────

  if (isLoading && !repository) {
    return (
      <div className="min-h-screen bg-gray-950">
        <RepoHomeSkeleton />
      </div>
    )
  }

  if (!repository) return null

  // Render README markdown
  const readmeHtml = readme
    ? marked.parse(decodeBase64(readme.content), { async: false }) as string
    : null

  // ── Repository view ────────────────────────────────────────────────────────

  return (
    <div className="min-h-screen bg-gray-950 text-gray-100">
      <div className="max-w-7xl mx-auto px-4 py-6">
        {/* Header */}
        <header className="mb-6">
          <div className="flex items-start justify-between gap-4 mb-3">
            <div className="flex-1 min-w-0">
              <h1 className="text-2xl font-bold text-gray-100 mb-2 flex items-center gap-2">
                <Link
                  to={`/${owner}`}
                  className="text-indigo-400 hover:text-indigo-300 transition-colors"
                >
                  {owner}
                </Link>
                <span className="text-gray-600">/</span>
                <span>{repository.name}</span>
                {repository.isPrivate && (
                  <span className="text-xs px-2 py-1 rounded border border-gray-600 text-gray-400 font-normal">
                    Private
                  </span>
                )}
              </h1>
              {repository.description && (
                <p className="text-gray-400 text-sm">{repository.description}</p>
              )}
            </div>

            {/* Action buttons */}
            <div className="flex-shrink-0 flex items-center gap-2">
              {/* Fork button */}
              <button
                type="button"
                onClick={() => forkMutation.mutate()}
                disabled={forkMutation.isPending}
                className="flex items-center gap-2 px-4 py-2 bg-gray-800 text-gray-200 border border-gray-700 rounded-md hover:bg-gray-700 transition-colors text-sm font-medium disabled:opacity-50"
              >
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  viewBox="0 0 20 20"
                  fill="currentColor"
                  className="w-4 h-4"
                >
                  <path d="M12.23 2.06c-.33-.2-.74-.23-1.1-.06-.37.16-.63.5-.63.9v1.65c-2.48.33-4.5 2.35-4.83 4.83h-1.65c-.4 0-.74.26-.9.63-.17.36-.14.77.06 1.1l3 5c.2.33.56.53.94.53s.74-.2 1.1-.53l3-5c.2-.33.23-.74.06-1.1-.16-.37-.5-.63-.9-.63h-1.65c.33-2.48 2.35-4.5 4.83-4.83v1.65c0 .4.26.74.63.9.36.17.77.14 1.1-.06l5-3c.33-.2.53-.56.53-.94s-.2-.74-.53-1.1l-5-3Z" />
                </svg>
                {forkMutation.isPending ? 'Forking...' : 'Fork'}
              </button>

              {/* Clone button */}
              <button
                type="button"
                onClick={() => setShowCloneModal(true)}
                className="flex items-center gap-2 px-4 py-2 bg-green-600 text-white rounded-md hover:bg-green-500 transition-colors text-sm font-medium"
              >
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  viewBox="0 0 20 20"
                  fill="currentColor"
                  className="w-4 h-4"
                >
                  <path d="M10.75 2.75a.75.75 0 0 0-1.5 0v8.614L6.295 8.235a.75.75 0 1 0-1.09 1.03l4.25 4.5a.75.75 0 0 0 1.09 0l4.25-4.5a.75.75 0 0 0-1.09-1.03l-2.955 3.129V2.75Z" />
                  <path d="M3.5 12.75a.75.75 0 0 0-1.5 0v2.5A2.75 2.75 0 0 0 4.75 18h10.5A2.75 2.75 0 0 0 18 15.25v-2.5a.75.75 0 0 0-1.5 0v2.5c0 .69-.56 1.25-1.25 1.25H4.75c-.69 0-1.25-.56-1.25-1.25v-2.5Z" />
                </svg>
                Clone
              </button>
            </div>
          </div>

          {/* Stats */}
          {stats && (
            <div className="flex items-center gap-4 text-sm text-gray-400">
              <div className="flex items-center gap-1">
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  viewBox="0 0 16 16"
                  fill="currentColor"
                  className="w-4 h-4"
                >
                  <path d="M8 2a.75.75 0 0 1 .75.75v4.5h4.5a.75.75 0 0 1 0 1.5h-4.5v4.5a.75.75 0 0 1-1.5 0v-4.5h-4.5a.75.75 0 0 1 0-1.5h4.5v-4.5A.75.75 0 0 1 8 2Z" />
                </svg>
                <span>{formatNumber(stats.commitCount)} commits</span>
              </div>
              <div className="flex items-center gap-1">
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  viewBox="0 0 16 16"
                  fill="currentColor"
                  className="w-4 h-4"
                >
                  <path d="M8 8a3 3 0 1 0 0-6 3 3 0 0 0 0 6ZM12.735 14c.618 0 1.093-.561.872-1.139a6.002 6.002 0 0 0-11.215 0c-.22.578.254 1.139.872 1.139h9.47Z" />
                </svg>
                <span>{formatNumber(stats.contributorCount)} contributors</span>
              </div>
              <div className="flex items-center gap-1">
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  viewBox="0 0 16 16"
                  fill="currentColor"
                  className="w-4 h-4"
                >
                  <path d="M2 3a1 1 0 0 1 1-1h10a1 1 0 0 1 1 1v1a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V3Z" />
                  <path
                    fillRule="evenodd"
                    d="M13 6H3v6a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2V6ZM5.72 7.47a.75.75 0 0 1 1.06 0L8 8.69l1.22-1.22a.75.75 0 1 1 1.06 1.06L9.06 9.75l1.22 1.22a.75.75 0 1 1-1.06 1.06L8 10.81l-1.22 1.22a.75.75 0 0 1-1.06-1.06l1.22-1.22-1.22-1.22a.75.75 0 0 1 0-1.06Z"
                    clipRule="evenodd"
                  />
                </svg>
                <span>{formatBytes(stats.sizeBytes)}</span>
              </div>
            </div>
          )}
        </header>

        {/* Main content grid */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* Left: README */}
          <div className="lg:col-span-2">
            <div className="bg-gray-800 border border-gray-700 rounded-lg overflow-hidden">
              <div className="px-4 py-3 border-b border-gray-700 flex items-center gap-2">
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  viewBox="0 0 16 16"
                  fill="currentColor"
                  className="w-4 h-4 text-gray-400"
                >
                  <path d="M2 3a1 1 0 0 1 1-1h10a1 1 0 0 1 1 1v1a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V3Z" />
                  <path
                    fillRule="evenodd"
                    d="M13 6H3v6a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2V6ZM8.75 7.75a.75.75 0 0 0-1.5 0v2.69L6.03 9.22a.75.75 0 0 0-1.06 1.06l2.5 2.5a.75.75 0 0 0 1.06 0l2.5-2.5a.75.75 0 1 0-1.06-1.06l-1.22 1.22V7.75Z"
                    clipRule="evenodd"
                  />
                </svg>
                <span className="text-sm font-medium text-gray-300">README.md</span>
              </div>
              <div className="p-6">
                {readmeHtml ? (
                  <div
                    className="prose prose-invert prose-sm max-w-none prose-headings:text-gray-100 prose-p:text-gray-300 prose-a:text-indigo-400 prose-code:text-pink-400 prose-pre:bg-gray-900 prose-pre:border prose-pre:border-gray-700"
                    dangerouslySetInnerHTML={{ __html: readmeHtml }}
                  />
                ) : (
                  <p className="text-sm text-gray-500 text-center py-8">
                    No README found
                  </p>
                )}
              </div>
            </div>
          </div>

          {/* Right: File tree preview */}
          <div>
            <div className="bg-gray-800 border border-gray-700 rounded-lg overflow-hidden">
              <div className="px-4 py-3 border-b border-gray-700 flex items-center justify-between">
                <span className="text-sm font-medium text-gray-300">Files</span>
                <Link
                  to={`/${owner}/${repo}/tree/${repository.defaultBranch}`}
                  className="text-xs text-indigo-400 hover:text-indigo-300 transition-colors"
                >
                  View all →
                </Link>
              </div>
              <div className="divide-y divide-gray-700">
                {tree && tree.length > 0 ? (
                  tree.slice(0, 10).map((entry) => (
                    <Link
                      key={entry.name}
                      to={`/${owner}/${repo}/${entry.type}/${repository.defaultBranch}/${entry.name}`}
                      className="flex items-center gap-2 px-4 py-2 hover:bg-gray-700 transition-colors"
                    >
                      <span className="text-lg" aria-hidden="true">
                        {getFileIcon(entry.type)}
                      </span>
                      <span className="text-sm text-gray-300 truncate flex-1">
                        {entry.name}
                      </span>
                      {entry.type === 'blob' && entry.size !== null && (
                        <span className="text-xs text-gray-500">
                          {formatBytes(entry.size)}
                        </span>
                      )}
                    </Link>
                  ))
                ) : (
                  <div className="px-4 py-8 text-center text-sm text-gray-500">
                    No files
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Clone modal */}
      {showCloneModal && (
        <CloneModal
          owner={owner!}
          repo={repo!}
          onClose={() => setShowCloneModal(false)}
        />
      )}
    </div>
  )
}
