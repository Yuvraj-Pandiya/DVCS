/**
 * FileBlobPage — file blob viewer at route /:owner/:repo/blob/:ref/*
 *
 * Displays:
 * - File content with syntax highlighting using Prism.js
 * - File metadata (size, last-commit SHA link)
 * - Raw download button
 * - Breadcrumb navigation
 *
 * Features:
 * - Automatic language detection from file extension
 * - Syntax highlighting for code files
 * - Plain text display for unsupported file types
 * - Binary file detection and message
 * - Fetches blob data via GET /api/repos/{owner}/{repo}/blob/{ref}/{path}
 */

import { useEffect } from 'react'
import { useParams, Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useApiClient, ApiError } from '../api/client'
import Prism from 'prismjs'

// Import Prism language components
import 'prismjs/components/prism-javascript'
import 'prismjs/components/prism-typescript'
import 'prismjs/components/prism-jsx'
import 'prismjs/components/prism-tsx'
import 'prismjs/components/prism-python'
import 'prismjs/components/prism-java'
import 'prismjs/components/prism-c'
import 'prismjs/components/prism-cpp'
import 'prismjs/components/prism-rust'
import 'prismjs/components/prism-go'
import 'prismjs/components/prism-bash'
import 'prismjs/components/prism-json'
import 'prismjs/components/prism-yaml'
import 'prismjs/components/prism-markdown'
import 'prismjs/components/prism-sql'
import 'prismjs/components/prism-css'
import 'prismjs/components/prism-scss'
import 'prismjs/components/prism-markup'

// Import Prism theme
import 'prismjs/themes/prism-tomorrow.css'

// ─── Types ────────────────────────────────────────────────────────────────────

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

/** Decode base64 content to UTF-8 string. */
function decodeBase64(base64: string): string {
  try {
    return atob(base64)
  } catch {
    return ''
  }
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

/** Get filename from path segments. */
function getFilename(pathSegments: string[]): string {
  return pathSegments[pathSegments.length - 1] || ''
}

/**
 * Detect Prism language from file extension.
 * Returns the Prism language identifier or null if unsupported.
 */
function detectLanguage(filename: string): string | null {
  const ext = filename.split('.').pop()?.toLowerCase()
  
  const languageMap: Record<string, string> = {
    // JavaScript/TypeScript
    js: 'javascript',
    jsx: 'jsx',
    ts: 'typescript',
    tsx: 'tsx',
    mjs: 'javascript',
    cjs: 'javascript',
    
    // Python
    py: 'python',
    
    // Java
    java: 'java',
    
    // C/C++
    c: 'c',
    h: 'c',
    cpp: 'cpp',
    cc: 'cpp',
    cxx: 'cpp',
    hpp: 'cpp',
    
    // Rust
    rs: 'rust',
    
    // Go
    go: 'go',
    
    // Shell
    sh: 'bash',
    bash: 'bash',
    zsh: 'bash',
    
    // Markup
    html: 'markup',
    xml: 'markup',
    svg: 'markup',
    
    // Styles
    css: 'css',
    scss: 'scss',
    sass: 'scss',
    
    // Data formats
    json: 'json',
    yaml: 'yaml',
    yml: 'yaml',
    toml: 'toml',
    
    // Markdown
    md: 'markdown',
    markdown: 'markdown',
    
    // SQL
    sql: 'sql',
    
    // Other
    txt: '', // plain text, no highlighting
    log: '',
  }
  
  return languageMap[ext || ''] || null
}

/**
 * Check if content appears to be binary.
 * Simple heuristic: check for null bytes in first 8000 bytes.
 */
function isBinaryContent(content: string): boolean {
  const checkLength = Math.min(content.length, 8000)
  for (let i = 0; i < checkLength; i++) {
    if (content.charCodeAt(i) === 0) {
      return true
    }
  }
  return false
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

// ─── File Header Component ────────────────────────────────────────────────────

interface FileHeaderProps {
  owner: string
  repo: string
  ref: string
  filename: string
  size: number
  lastCommitSha: string | null
  filePath: string
}

function FileHeader({
  owner,
  repo,
  ref,
  filename,
  size,
  lastCommitSha,
  filePath,
}: FileHeaderProps) {
  const rawUrl = `/api/repos/${owner}/${repo}/raw/${ref}/${filePath}`
  
  return (
    <div className="flex items-center justify-between px-4 py-3 border-b border-gray-700 bg-gray-800">
      <div className="flex items-center gap-4">
        <span className="text-sm font-medium text-gray-300">{filename}</span>
        <span className="text-xs text-gray-500">{formatBytes(size)}</span>
        {lastCommitSha && (
          <Link
            to={`/${owner}/${repo}/commit/${lastCommitSha}`}
            className="text-xs text-gray-500 hover:text-indigo-400 transition-colors font-mono"
          >
            {lastCommitSha.substring(0, 7)}
          </Link>
        )}
      </div>
      
      <a
        href={rawUrl}
        download={filename}
        className="flex items-center gap-2 px-3 py-1.5 text-xs font-medium text-gray-300 bg-gray-700 hover:bg-gray-600 rounded transition-colors"
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
        Raw
      </a>
    </div>
  )
}

// ─── Code Display Component ───────────────────────────────────────────────────

interface CodeDisplayProps {
  content: string
  language: string | null
}

function CodeDisplay({ content, language }: CodeDisplayProps) {
  useEffect(() => {
    // Trigger Prism syntax highlighting after content is rendered
    Prism.highlightAll()
  }, [content, language])
  
  if (!language) {
    // Plain text display with line numbers
    const lines = content.split('\n')
    return (
      <div className="flex font-mono text-sm">
        {/* Line numbers */}
        <div className="flex-shrink-0 px-4 py-4 text-right text-gray-500 bg-gray-900 border-r border-gray-700 select-none">
          {lines.map((_, index) => (
            <div key={index} className="leading-6">
              {index + 1}
            </div>
          ))}
        </div>
        
        {/* Content */}
        <pre className="flex-1 px-4 py-4 overflow-x-auto">
          <code className="text-gray-300">{content}</code>
        </pre>
      </div>
    )
  }
  
  // Syntax-highlighted display with line numbers
  const lines = content.split('\n')
  
  return (
    <div className="flex font-mono text-sm">
      {/* Line numbers */}
      <div className="flex-shrink-0 px-4 py-4 text-right text-gray-500 bg-gray-900 border-r border-gray-700 select-none">
        {lines.map((_, index) => (
          <div key={index} className="leading-6">
            {index + 1}
          </div>
        ))}
      </div>
      
      {/* Syntax-highlighted content */}
      <pre className="flex-1 px-4 py-4 overflow-x-auto">
        <code className={`language-${language}`}>{content}</code>
      </pre>
    </div>
  )
}

// ─── Binary File Display ──────────────────────────────────────────────────────

interface BinaryFileDisplayProps {
  filename: string
  size: number
}

function BinaryFileDisplay({ filename, size }: BinaryFileDisplayProps) {
  return (
    <div className="px-4 py-12 text-center">
      <svg
        xmlns="http://www.w3.org/2000/svg"
        viewBox="0 0 24 24"
        fill="currentColor"
        className="w-16 h-16 mx-auto mb-4 text-gray-600"
      >
        <path d="M5.625 1.5c-1.036 0-1.875.84-1.875 1.875v17.25c0 1.035.84 1.875 1.875 1.875h12.75c1.035 0 1.875-.84 1.875-1.875V12.75A3.75 3.75 0 0 0 16.5 9h-1.875a1.875 1.875 0 0 1-1.875-1.875V5.25A3.75 3.75 0 0 0 9 1.5H5.625Z" />
        <path d="M12.971 1.816A5.23 5.23 0 0 1 14.25 5.25v1.875c0 .207.168.375.375.375H16.5a5.23 5.23 0 0 1 3.434 1.279 9.768 9.768 0 0 0-6.963-6.963Z" />
      </svg>
      <p className="text-gray-400 mb-2">Binary file</p>
      <p className="text-sm text-gray-500">
        {filename} ({formatBytes(size)})
      </p>
      <p className="text-xs text-gray-600 mt-4">
        This file cannot be displayed in the browser.
      </p>
    </div>
  )
}

// ─── Skeleton Component ───────────────────────────────────────────────────────

function FileBlobSkeleton() {
  return (
    <div className="animate-pulse">
      <div className="flex items-center justify-between px-4 py-3 border-b border-gray-700 bg-gray-800">
        <div className="flex items-center gap-4">
          <div className="h-4 bg-gray-700 rounded w-32" />
          <div className="h-3 bg-gray-700 rounded w-16" />
          <div className="h-3 bg-gray-700 rounded w-20" />
        </div>
        <div className="h-8 bg-gray-700 rounded w-20" />
      </div>
      <div className="p-4 space-y-2">
        {[...Array(20)].map((_, i) => (
          <div key={i} className="h-4 bg-gray-700 rounded" style={{ width: `${60 + Math.random() * 40}%` }} />
        ))}
      </div>
    </div>
  )
}

// ─── FileBlobPage ─────────────────────────────────────────────────────────────

export default function FileBlobPage() {
  const { owner, repo, ref, '*': pathParam } = useParams<{
    owner: string
    repo: string
    ref: string
    '*': string
  }>()
  
  const api = useApiClient()
  const pathSegments = parsePath(pathParam)
  const filePath = buildPath(pathSegments)
  const filename = getFilename(pathSegments)
  
  // Fetch blob content
  const {
    data: blob,
    isLoading,
    error,
  } = useQuery<BlobContent, ApiError>({
    queryKey: ['repo-blob', owner, repo, ref, filePath],
    queryFn: () => api.get<BlobContent>(`/api/repos/${owner}/${repo}/blob/${ref}/${filePath}`),
    enabled: !!owner && !!repo && !!ref && !!filePath,
    retry: (failureCount, error) => {
      if (error instanceof ApiError && error.status === 404) return false
      return failureCount < 2
    },
  })
  
  // Decode content and detect language
  const decodedContent = blob ? decodeBase64(blob.content) : ''
  const isBinary = blob ? isBinaryContent(decodedContent) : false
  const language = isBinary ? null : detectLanguage(filename)
  
  // ── 404 state ──────────────────────────────────────────────────────────────
  
  if (error instanceof ApiError && error.status === 404) {
    return (
      <div className="min-h-screen bg-gray-950 flex items-center justify-center px-4">
        <div className="text-center">
          <p className="text-6xl font-bold text-gray-700 mb-4" aria-hidden="true">
            404
          </p>
          <h1 className="text-xl font-semibold text-gray-200 mb-2">
            File not found
          </h1>
          <p className="text-gray-400 mb-6">
            The file{' '}
            <span className="font-mono text-gray-300">
              {filePath}
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
          <p className="text-gray-400 mb-4">Failed to load file.</p>
          <p className="text-sm text-red-400">
            {error instanceof Error ? error.message : 'Unknown error'}
          </p>
        </div>
      </div>
    )
  }
  
  // ── Loading state ──────────────────────────────────────────────────────────
  
  if (isLoading && !blob) {
    return (
      <div className="min-h-screen bg-gray-950">
        <div className="max-w-7xl mx-auto px-4 py-6">
          <div className="mb-4 h-6 bg-gray-700 rounded w-96 animate-pulse" />
          <div className="bg-gray-800 border border-gray-700 rounded-lg overflow-hidden">
            <FileBlobSkeleton />
          </div>
        </div>
      </div>
    )
  }
  
  if (!blob) return null
  
  // ── File blob view ─────────────────────────────────────────────────────────
  
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
        
        {/* File viewer */}
        <div className="bg-gray-800 border border-gray-700 rounded-lg overflow-hidden">
          <FileHeader
            owner={owner!}
            repo={repo!}
            ref={ref!}
            filename={filename}
            size={blob.size}
            lastCommitSha={blob.lastCommitSha}
            filePath={filePath}
          />
          
          {isBinary ? (
            <BinaryFileDisplay filename={filename} size={blob.size} />
          ) : (
            <CodeDisplay content={decodedContent} language={language} />
          )}
        </div>
      </div>
    </div>
  )
}
