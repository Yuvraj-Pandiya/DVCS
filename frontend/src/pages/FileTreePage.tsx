/**
 * FileTreePage — repository file tree browser at route /:owner/:repo/tree/:ref/*
 *
 * Displays:
 * - Breadcrumb navigation showing current path
 * - Recursive file tree with collapsible folder nodes
 * - File-type icons using react-icons
 * - Last-commit message and relative timestamp for each entry
 * - Fetches tree data via GET /api/repos/{owner}/{repo}/tree/{ref}/{path}
 *
 * Features:
 * - Folders are collapsible/expandable
 * - Clicking a file navigates to blob view
 * - Clicking a folder expands it in place
 * - Breadcrumb allows navigation to parent directories
 */

import { useState } from 'react'
import { useParams, Link, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useApiClient, ApiError } from '../api/client'
import {
  FaFolder,
  FaFolderOpen,
  FaFile,
  FaFileCode,
  FaFileImage,
  FaFileArchive,
  FaFilePdf,
  FaFileAlt,
  FaMarkdown,
} from 'react-icons/fa'
import { SiJavascript, SiTypescript, SiPython, SiCplusplus, SiRust, SiGo } from 'react-icons/si'

// ─── Types ────────────────────────────────────────────────────────────────────

interface TreeEntry {
  name: string
  type: 'blob' | 'tree'
  size: number | null
  lastCommitSha: string | null
  lastCommitMessage: string | null
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

/** Format a timestamp to relative time (e.g., "2 hours ago"). */
function formatRelativeTime(timestamp: string | null): string {
  if (!timestamp) return ''
  
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

/** Get file icon based on file extension. */
function getFileIcon(name: string): JSX.Element {
  const ext = name.split('.').pop()?.toLowerCase()
  
  // Language-specific icons
  if (ext === 'js' || ext === 'jsx') return <SiJavascript className="text-yellow-400" />
  if (ext === 'ts' || ext === 'tsx') return <SiTypescript className="text-blue-400" />
  if (ext === 'py') return <SiPython className="text-blue-500" />
  if (ext === 'java') return <FaFileCode className="text-red-500" />
  if (ext === 'cpp' || ext === 'cc' || ext === 'cxx' || ext === 'c' || ext === 'h' || ext === 'hpp') 
    return <SiCplusplus className="text-blue-600" />
  if (ext === 'rs') return <SiRust className="text-orange-600" />
  if (ext === 'go') return <SiGo className="text-cyan-400" />
  
  // File type icons
  if (ext === 'md' || ext === 'markdown') return <FaMarkdown className="text-gray-300" />
  if (ext === 'pdf') return <FaFilePdf className="text-red-400" />
  if (['png', 'jpg', 'jpeg', 'gif', 'svg', 'webp', 'ico'].includes(ext || ''))
    return <FaFileImage className="text-purple-400" />
  if (['zip', 'tar', 'gz', 'rar', '7z', 'bz2'].includes(ext || ''))
    return <FaFileArchive className="text-orange-400" />
  if (['json', 'xml', 'yaml', 'yml', 'toml', 'ini', 'conf', 'config'].includes(ext || ''))
    return <FaFileCode className="text-green-400" />
  if (['txt', 'log', 'csv'].includes(ext || ''))
    return <FaFileAlt className="text-gray-400" />
  
  // Default file icon
  return <FaFile className="text-gray-400" />
}

/** Parse path segments from wildcard route parameter. */
function parsePath(pathParam: string | undefined): string[] {
  if (!pathParam) return []
  return pathParam.split('/').filter(Boolean)
}

/** Build full path string from segments. */
function buildPath(segments: string[]): string {
  return segments.join('/')
}

// ─── Breadcrumb Component ─────────────────────────────────────────────────────

interface BreadcrumbProps {
  owner: string
  repo: string
  ref: string
  pathSegments: string[]
}

function Breadcrumb({ owner, repo, ref, pathSegments }: BreadcrumbProps) {
  return (
    <nav className="flex items-center gap-2 text-sm text-gray-400 mb-4">
      <Link
        to={`/${owner}/${repo}`}
        className="hover:text-indigo-400 transition-colors"
      >
        {repo}
      </Link>
      <span>/</span>
      <Link
        to={`/${owner}/${repo}/tree/${ref}`}
        className="hover:text-indigo-400 transition-colors"
      >
        {ref}
      </Link>
      {pathSegments.map((segment, index) => {
        const isLast = index === pathSegments.length - 1
        const pathUpToHere = buildPath(pathSegments.slice(0, index + 1))
        
        return (
          <span key={index} className="flex items-center gap-2">
            <span>/</span>
            {isLast ? (
              <span className="text-gray-200 font-medium">{segment}</span>
            ) : (
              <Link
                to={`/${owner}/${repo}/tree/${ref}/${pathUpToHere}`}
                className="hover:text-indigo-400 transition-colors"
              >
                {segment}
              </Link>
            )}
          </span>
        )
      })}
    </nav>
  )
}

// ─── FileTree Component ───────────────────────────────────────────────────────

interface FileTreeProps {
  owner: string
  repo: string
  ref: string
  basePath: string
  entries: TreeEntry[]
}

function FileTree({ owner, repo, ref, basePath, entries }: FileTreeProps) {
  const [expandedFolders, setExpandedFolders] = useState<Set<string>>(new Set())
  const navigate = useNavigate()
  
  const toggleFolder = (folderName: string) => {
    setExpandedFolders((prev) => {
      const next = new Set(prev)
      if (next.has(folderName)) {
        next.delete(folderName)
      } else {
        next.add(folderName)
      }
      return next
    })
  }
  
  const handleEntryClick = (entry: TreeEntry) => {
    if (entry.type === 'tree') {
      toggleFolder(entry.name)
    } else {
      const fullPath = basePath ? `${basePath}/${entry.name}` : entry.name
      navigate(`/${owner}/${repo}/blob/${ref}/${fullPath}`)
    }
  }
  
  // Sort: folders first, then files, both alphabetically
  const sortedEntries = [...entries].sort((a, b) => {
    if (a.type === b.type) return a.name.localeCompare(b.name)
    return a.type === 'tree' ? -1 : 1
  })
  
  return (
    <div className="divide-y divide-gray-700">
      {sortedEntries.map((entry) => {
        const isFolder = entry.type === 'tree'
        const isExpanded = expandedFolders.has(entry.name)
        const fullPath = basePath ? `${basePath}/${entry.name}` : entry.name
        
        return (
          <div key={entry.name}>
            {/* Entry row */}
            <div
              className="flex items-center gap-3 px-4 py-3 hover:bg-gray-700 transition-colors cursor-pointer"
              onClick={() => handleEntryClick(entry)}
              role="button"
              tabIndex={0}
              onKeyDown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                  e.preventDefault()
                  handleEntryClick(entry)
                }
              }}
            >
              {/* Icon */}
              <div className="flex-shrink-0 w-5 h-5 flex items-center justify-center">
                {isFolder ? (
                  isExpanded ? (
                    <FaFolderOpen className="text-blue-400" />
                  ) : (
                    <FaFolder className="text-blue-400" />
                  )
                ) : (
                  getFileIcon(entry.name)
                )}
              </div>
              
              {/* Name */}
              <div className="flex-1 min-w-0">
                <div className="text-sm text-gray-200 truncate font-medium">
                  {entry.name}
                </div>
              </div>
              
              {/* Last commit message */}
              <div className="hidden md:block flex-1 min-w-0">
                <div className="text-sm text-gray-400 truncate">
                  {entry.lastCommitMessage || '—'}
                </div>
              </div>
              
              {/* Timestamp and size */}
              <div className="flex items-center gap-4 flex-shrink-0">
                {entry.lastCommitSha && (
                  <Link
                    to={`/${owner}/${repo}/commit/${entry.lastCommitSha}`}
                    className="text-xs text-gray-500 hover:text-indigo-400 transition-colors"
                    onClick={(e) => e.stopPropagation()}
                  >
                    {formatRelativeTime(entry.lastCommitSha)}
                  </Link>
                )}
                {!isFolder && entry.size !== null && (
                  <span className="text-xs text-gray-500 w-16 text-right">
                    {formatBytes(entry.size)}
                  </span>
                )}
              </div>
            </div>
            
            {/* Nested tree (if folder is expanded) */}
            {isFolder && isExpanded && (
              <NestedTree
                owner={owner}
                repo={repo}
                ref={ref}
                path={fullPath}
              />
            )}
          </div>
        )
      })}
    </div>
  )
}

// ─── NestedTree Component ─────────────────────────────────────────────────────

interface NestedTreeProps {
  owner: string
  repo: string
  ref: string
  path: string
}

function NestedTree({ owner, repo, ref, path }: NestedTreeProps) {
  const api = useApiClient()
  
  const { data: entries, isLoading, error } = useQuery<TreeEntry[], ApiError>({
    queryKey: ['repo-tree', owner, repo, ref, path],
    queryFn: () => api.get<TreeEntry[]>(`/api/repos/${owner}/${repo}/tree/${ref}/${path}`),
    retry: false,
  })
  
  if (isLoading) {
    return (
      <div className="pl-8 py-2 text-sm text-gray-500">
        Loading...
      </div>
    )
  }
  
  if (error) {
    return (
      <div className="pl-8 py-2 text-sm text-red-400">
        Failed to load folder contents
      </div>
    )
  }
  
  if (!entries || entries.length === 0) {
    return (
      <div className="pl-8 py-2 text-sm text-gray-500">
        Empty folder
      </div>
    )
  }
  
  return (
    <div className="pl-8 border-l-2 border-gray-700">
      <FileTree
        owner={owner}
        repo={repo}
        ref={ref}
        basePath={path}
        entries={entries}
      />
    </div>
  )
}

// ─── Skeleton Component ───────────────────────────────────────────────────────

function FileTreeSkeleton() {
  return (
    <div className="animate-pulse divide-y divide-gray-700">
      {[...Array(8)].map((_, i) => (
        <div key={i} className="flex items-center gap-3 px-4 py-3">
          <div className="w-5 h-5 bg-gray-700 rounded" />
          <div className="flex-1 h-4 bg-gray-700 rounded" />
          <div className="hidden md:block flex-1 h-4 bg-gray-700 rounded" />
          <div className="w-20 h-4 bg-gray-700 rounded" />
        </div>
      ))}
    </div>
  )
}

// ─── FileTreePage ─────────────────────────────────────────────────────────────

export default function FileTreePage() {
  const { owner, repo, ref, '*': pathParam } = useParams<{
    owner: string
    repo: string
    ref: string
    '*': string
  }>()
  
  const api = useApiClient()
  const pathSegments = parsePath(pathParam)
  const currentPath = buildPath(pathSegments)
  
  // Fetch tree entries for current path
  const {
    data: entries,
    isLoading,
    error,
  } = useQuery<TreeEntry[], ApiError>({
    queryKey: ['repo-tree', owner, repo, ref, currentPath],
    queryFn: () => {
      const path = currentPath ? `/${currentPath}` : '/'
      return api.get<TreeEntry[]>(`/api/repos/${owner}/${repo}/tree/${ref}${path}`)
    },
    enabled: !!owner && !!repo && !!ref,
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
            Path not found
          </h1>
          <p className="text-gray-400 mb-6">
            The path{' '}
            <span className="font-mono text-gray-300">
              {currentPath || '/'}
            </span>{' '}
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
          <p className="text-gray-400 mb-4">Failed to load file tree.</p>
          <p className="text-sm text-red-400">
            {error instanceof Error ? error.message : 'Unknown error'}
          </p>
        </div>
      </div>
    )
  }
  
  // ── Loading state ──────────────────────────────────────────────────────────
  
  if (isLoading && !entries) {
    return (
      <div className="min-h-screen bg-gray-950">
        <div className="max-w-7xl mx-auto px-4 py-6">
          <div className="mb-4 h-6 bg-gray-700 rounded w-96 animate-pulse" />
          <div className="bg-gray-800 border border-gray-700 rounded-lg overflow-hidden">
            <FileTreeSkeleton />
          </div>
        </div>
      </div>
    )
  }
  
  if (!entries) return null
  
  // ── File tree view ─────────────────────────────────────────────────────────
  
  return (
    <div className="min-h-screen bg-gray-950 text-gray-100">
      <div className="max-w-7xl mx-auto px-4 py-6">
        {/* Breadcrumb navigation */}
        <Breadcrumb
          owner={owner!}
          repo={repo!}
          ref={ref!}
          pathSegments={pathSegments}
        />
        
        {/* File tree */}
        <div className="bg-gray-800 border border-gray-700 rounded-lg overflow-hidden">
          {entries.length > 0 ? (
            <FileTree
              owner={owner!}
              repo={repo!}
              ref={ref!}
              basePath={currentPath}
              entries={entries}
            />
          ) : (
            <div className="px-4 py-8 text-center text-sm text-gray-500">
              This directory is empty
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
