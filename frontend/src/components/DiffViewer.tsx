/**
 * DiffViewer — unified and side-by-side diff viewer
 *
 * Displays:
 * - Unified diff view (single column with +/- lines)
 * - Side-by-side diff view (two columns: base | head)
 * - Line numbers for both base and head
 * - Syntax highlighting for added/removed/context lines
 * - Toggle between unified and split modes
 * - Inline comments on specific lines
 *
 * Features:
 * - Synchronized scrolling in split mode
 * - Color-coded additions (green) and deletions (red)
 * - Context lines (gray)
 * - Empty rows in split mode for alignment
 * - Click line number to add inline comment
 * - Threaded comments displayed below lines
 * - Responsive layout
 */

import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useApiClient } from '../api/client'

// ─── Types ────────────────────────────────────────────────────────────────────

export interface DiffHunk {
  type: 'ADD' | 'REMOVE' | 'CONTEXT'
  baseStart: number
  baseEnd: number
  headStart: number
  headEnd: number
  lines: DiffLine[]
}

export interface DiffLine {
  type: 'ADD' | 'REMOVE' | 'CONTEXT'
  content: string
  baseLineNo: number
  headLineNo: number
}

export interface PrComment {
  id: number
  prId: number
  reviewId?: number
  filePath?: string
  lineNumber?: number
  body: string
  authorId: number
  createdAt: string
}

type ViewMode = 'unified' | 'split'

// ─── CommentForm Component ────────────────────────────────────────────────────

interface CommentFormProps {
  onSubmit: (body: string) => void
  onCancel: () => void
  isSubmitting?: boolean
}

function CommentForm({ onSubmit, onCancel, isSubmitting }: CommentFormProps) {
  const [body, setBody] = useState('')

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (body.trim()) {
      onSubmit(body.trim())
      setBody('')
    }
  }

  return (
    <form onSubmit={handleSubmit} className="bg-gray-900 border border-gray-700 rounded p-3 my-2">
      <textarea
        value={body}
        onChange={(e) => setBody(e.target.value)}
        placeholder="Add a comment..."
        className="w-full bg-gray-800 text-gray-200 border border-gray-600 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 resize-none"
        rows={3}
        autoFocus
        disabled={isSubmitting}
      />
      <div className="flex gap-2 mt-2">
        <button
          type="submit"
          disabled={!body.trim() || isSubmitting}
          className="px-3 py-1 text-sm font-medium bg-indigo-600 text-white rounded hover:bg-indigo-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
        >
          {isSubmitting ? 'Submitting...' : 'Comment'}
        </button>
        <button
          type="button"
          onClick={onCancel}
          disabled={isSubmitting}
          className="px-3 py-1 text-sm font-medium bg-gray-700 text-gray-300 rounded hover:bg-gray-600 disabled:opacity-50 transition-colors"
        >
          Cancel
        </button>
      </div>
    </form>
  )
}

// ─── CommentThread Component ──────────────────────────────────────────────────

interface CommentThreadProps {
  comments: PrComment[]
}

function CommentThread({ comments }: CommentThreadProps) {
  if (comments.length === 0) return null

  return (
    <div className="bg-gray-900/50 border-l-2 border-indigo-500 ml-4 pl-4 py-2 space-y-2">
      {comments.map((comment) => (
        <div key={comment.id} className="text-sm">
          <div className="flex items-center gap-2 mb-1">
            <span className="font-medium text-gray-300">User {comment.authorId}</span>
            <span className="text-xs text-gray-500">
              {new Date(comment.createdAt).toLocaleString()}
            </span>
          </div>
          <div className="text-gray-400 whitespace-pre-wrap">{comment.body}</div>
        </div>
      ))}
    </div>
  )
}

// ─── UnifiedDiffView Component ────────────────────────────────────────────────

interface UnifiedDiffViewProps {
  hunks: DiffHunk[]
  comments: PrComment[]
  filePath?: string
  activeCommentLine: string | null
  onLineClick: (lineKey: string, lineNumber: number) => void
  onCommentSubmit: (lineNumber: number, body: string) => void
  onCommentCancel: () => void
  isSubmitting: boolean
}

function UnifiedDiffView({
  hunks,
  comments,
  filePath,
  activeCommentLine,
  onLineClick,
  onCommentSubmit,
  onCommentCancel,
  isSubmitting,
}: UnifiedDiffViewProps) {
  // Group comments by line number
  const commentsByLine = comments.reduce((acc, comment) => {
    if (comment.lineNumber && comment.filePath === filePath) {
      if (!acc[comment.lineNumber]) {
        acc[comment.lineNumber] = []
      }
      acc[comment.lineNumber].push(comment)
    }
    return acc
  }, {} as Record<number, PrComment[]>)

  return (
    <div className="divide-y divide-gray-700">
      {hunks.map((hunk, hunkIndex) => (
        <div key={hunkIndex} className="font-mono text-sm">
          {/* Hunk header */}
          <div className="bg-gray-900 px-4 py-2 text-xs text-gray-400 sticky top-0 z-10">
            @@ -{hunk.baseStart},{hunk.baseEnd - hunk.baseStart + 1} +
            {hunk.headStart},{hunk.headEnd - hunk.headStart + 1} @@
          </div>
          {/* Hunk lines */}
          <div>
            {hunk.lines.map((line, lineIndex) => {
              const bgColor =
                line.type === 'ADD'
                  ? 'bg-green-900/30'
                  : line.type === 'REMOVE'
                  ? 'bg-red-900/30'
                  : 'bg-transparent'
              const textColor =
                line.type === 'ADD'
                  ? 'text-green-300'
                  : line.type === 'REMOVE'
                  ? 'text-red-300'
                  : 'text-gray-300'
              const prefix =
                line.type === 'ADD' ? '+' : line.type === 'REMOVE' ? '-' : ' '

              // Use head line number for comments (the "new" version)
              const lineNumber = line.headLineNo > 0 ? line.headLineNo : line.baseLineNo
              const lineKey = `unified-${hunkIndex}-${lineIndex}`
              const lineComments = commentsByLine[lineNumber] || []
              const showCommentForm = activeCommentLine === lineKey

              return (
                <div key={lineIndex}>
                  <div
                    className={`flex ${bgColor} hover:bg-gray-700/50 transition-colors`}
                  >
                    {/* Line numbers */}
                    <div className="flex-shrink-0 w-24 px-2 py-1 text-right text-xs text-gray-600 select-none border-r border-gray-700">
                      <button
                        onClick={() => onLineClick(lineKey, lineNumber)}
                        className="inline-block w-10 hover:text-indigo-400 transition-colors cursor-pointer"
                        title="Add comment"
                      >
                        {line.baseLineNo > 0 ? line.baseLineNo : ''}
                      </button>
                      <button
                        onClick={() => onLineClick(lineKey, lineNumber)}
                        className="inline-block w-10 hover:text-indigo-400 transition-colors cursor-pointer"
                        title="Add comment"
                      >
                        {line.headLineNo > 0 ? line.headLineNo : ''}
                      </button>
                    </div>
                    {/* Line content */}
                    <div className={`flex-1 px-4 py-1 ${textColor} whitespace-pre overflow-x-auto`}>
                      <span className="select-none mr-2">{prefix}</span>
                      {line.content}
                    </div>
                  </div>

                  {/* Comment form */}
                  {showCommentForm && (
                    <div className="px-4 py-2 bg-gray-800/50">
                      <CommentForm
                        onSubmit={(body) => onCommentSubmit(lineNumber, body)}
                        onCancel={onCommentCancel}
                        isSubmitting={isSubmitting}
                      />
                    </div>
                  )}

                  {/* Existing comments */}
                  {lineComments.length > 0 && (
                    <div className="px-4 py-2 bg-gray-800/50">
                      <CommentThread comments={lineComments} />
                    </div>
                  )}
                </div>
              )
            })}
          </div>
        </div>
      ))}
    </div>
  )
}

// ─── SplitDiffView Component ──────────────────────────────────────────────────

interface SplitDiffViewProps {
  hunks: DiffHunk[]
  comments: PrComment[]
  filePath?: string
  activeCommentLine: string | null
  onLineClick: (lineKey: string, lineNumber: number) => void
  onCommentSubmit: (lineNumber: number, body: string) => void
  onCommentCancel: () => void
  isSubmitting: boolean
}

function SplitDiffView({
  hunks,
  comments,
  filePath,
  activeCommentLine,
  onLineClick,
  onCommentSubmit,
  onCommentCancel,
  isSubmitting,
}: SplitDiffViewProps) {
  // Group comments by line number
  const commentsByLine = comments.reduce((acc, comment) => {
    if (comment.lineNumber && comment.filePath === filePath) {
      if (!acc[comment.lineNumber]) {
        acc[comment.lineNumber] = []
      }
      acc[comment.lineNumber].push(comment)
    }
    return acc
  }, {} as Record<number, PrComment[]>)

  return (
    <div className="divide-y divide-gray-700">
      {hunks.map((hunk, hunkIndex) => (
        <div key={hunkIndex} className="font-mono text-sm">
          {/* Hunk header */}
          <div className="bg-gray-900 px-4 py-2 text-xs text-gray-400 sticky top-0 z-10 grid grid-cols-2 gap-px">
            <div>
              @@ -{hunk.baseStart},{hunk.baseEnd - hunk.baseStart + 1} @@
            </div>
            <div>
              @@ +{hunk.headStart},{hunk.headEnd - hunk.headStart + 1} @@
            </div>
          </div>
          {/* Hunk lines (split view) */}
          <div>
            {hunk.lines.map((line, lineIndex) => {
              const lineNumber = line.headLineNo > 0 ? line.headLineNo : line.baseLineNo
              const lineKey = `split-${hunkIndex}-${lineIndex}`
              const lineComments = commentsByLine[lineNumber] || []
              const showCommentForm = activeCommentLine === lineKey

              return (
                <div key={lineIndex}>
                  <div className="grid grid-cols-2 gap-px bg-gray-700">
                    {/* Base (left) column */}
                    <div className="bg-gray-800">
                      {line.type === 'ADD' ? (
                        // Empty row for additions (only in head)
                        <div className="flex bg-gray-900/50 h-7" />
                      ) : (
                        <div
                          className={`flex ${
                            line.type === 'REMOVE' ? 'bg-red-900/30' : 'bg-transparent'
                          } hover:bg-gray-700/50 transition-colors`}
                        >
                          {/* Line number */}
                          <div className="flex-shrink-0 w-12 px-2 py-1 text-right text-xs text-gray-600 select-none border-r border-gray-700">
                            <button
                              onClick={() => onLineClick(lineKey, lineNumber)}
                              className="hover:text-indigo-400 transition-colors cursor-pointer"
                              title="Add comment"
                            >
                              {line.baseLineNo > 0 ? line.baseLineNo : ''}
                            </button>
                          </div>
                          {/* Line content */}
                          <div
                            className={`flex-1 px-4 py-1 ${
                              line.type === 'REMOVE' ? 'text-red-300' : 'text-gray-300'
                            } whitespace-pre overflow-x-auto`}
                          >
                            <span className="select-none mr-2">
                              {line.type === 'REMOVE' ? '-' : ' '}
                            </span>
                            {line.content}
                          </div>
                        </div>
                      )}
                    </div>

                    {/* Head (right) column */}
                    <div className="bg-gray-800">
                      {line.type === 'REMOVE' ? (
                        // Empty row for deletions (only in base)
                        <div className="flex bg-gray-900/50 h-7" />
                      ) : (
                        <div
                          className={`flex ${
                            line.type === 'ADD' ? 'bg-green-900/30' : 'bg-transparent'
                          } hover:bg-gray-700/50 transition-colors`}
                        >
                          {/* Line number */}
                          <div className="flex-shrink-0 w-12 px-2 py-1 text-right text-xs text-gray-600 select-none border-r border-gray-700">
                            <button
                              onClick={() => onLineClick(lineKey, lineNumber)}
                              className="hover:text-indigo-400 transition-colors cursor-pointer"
                              title="Add comment"
                            >
                              {line.headLineNo > 0 ? line.headLineNo : ''}
                            </button>
                          </div>
                          {/* Line content */}
                          <div
                            className={`flex-1 px-4 py-1 ${
                              line.type === 'ADD' ? 'text-green-300' : 'text-gray-300'
                            } whitespace-pre overflow-x-auto`}
                          >
                            <span className="select-none mr-2">
                              {line.type === 'ADD' ? '+' : ' '}
                            </span>
                            {line.content}
                          </div>
                        </div>
                      )}
                    </div>
                  </div>

                  {/* Comment form (spans both columns) */}
                  {showCommentForm && (
                    <div className="px-4 py-2 bg-gray-800/50 col-span-2">
                      <CommentForm
                        onSubmit={(body) => onCommentSubmit(lineNumber, body)}
                        onCancel={onCommentCancel}
                        isSubmitting={isSubmitting}
                      />
                    </div>
                  )}

                  {/* Existing comments (spans both columns) */}
                  {lineComments.length > 0 && (
                    <div className="px-4 py-2 bg-gray-800/50 col-span-2">
                      <CommentThread comments={lineComments} />
                    </div>
                  )}
                </div>
              )
            })}
          </div>
        </div>
      ))}
    </div>
  )
}

// ─── DiffViewer Component ─────────────────────────────────────────────────────

interface DiffViewerProps {
  hunks: DiffHunk[]
  binary?: boolean
  comments?: PrComment[]
  filePath?: string
  owner?: string
  repo?: string
  prNumber?: number
  defaultMode?: ViewMode
}

export default function DiffViewer({
  hunks,
  binary = false,
  comments = [],
  filePath,
  owner,
  repo,
  prNumber,
  defaultMode = 'unified',
}: DiffViewerProps) {
  const [mode, setMode] = useState<ViewMode>(defaultMode)
  const [activeCommentLine, setActiveCommentLine] = useState<string | null>(null)
  const queryClient = useQueryClient()
  const apiClient = useApiClient()

  // Mutation for adding comments
  const addCommentMutation = useMutation({
    mutationFn: async ({
      body,
      lineNumber,
    }: {
      body: string
      lineNumber: number
    }) => {
      if (!owner || !repo || !prNumber) {
        throw new Error('Missing PR context for comment submission')
      }

      const response = await apiClient.post<PrComment>(
        `/api/repos/${owner}/${repo}/pulls/${prNumber}/comments`,
        {
          body,
          filePath,
          lineNumber,
        }
      )
      return response
    },
    onSuccess: () => {
      // Invalidate PR detail query to refetch comments
      if (owner && repo && prNumber) {
        queryClient.invalidateQueries({
          queryKey: ['pr-detail', owner, repo, prNumber],
        })
      }
      setActiveCommentLine(null)
    },
  })

  const handleLineClick = (lineKey: string, _lineNumber: number) => {
    // Toggle comment form for this line
    setActiveCommentLine(activeCommentLine === lineKey ? null : lineKey)
  }

  const handleCommentSubmit = (lineNumber: number, body: string) => {
    addCommentMutation.mutate({ body, lineNumber })
  }

  const handleCommentCancel = () => {
    setActiveCommentLine(null)
  }

  if (binary) {
    return (
      <div className="bg-gray-800 border border-gray-700 rounded-lg p-8 text-center">
        <p className="text-sm text-gray-400">Binary file — diff not available.</p>
      </div>
    )
  }

  if (hunks.length === 0) {
    return (
      <div className="bg-gray-800 border border-gray-700 rounded-lg p-8 text-center">
        <p className="text-sm text-gray-500">No changes to display.</p>
      </div>
    )
  }

  // Calculate diff stats
  const stats = hunks.reduce(
    (acc, hunk) => {
      for (const line of hunk.lines) {
        if (line.type === 'ADD') acc.additions++
        if (line.type === 'REMOVE') acc.deletions++
      }
      return acc
    },
    { additions: 0, deletions: 0 }
  )

  return (
    <div className="bg-gray-800 border border-gray-700 rounded-lg overflow-hidden">
      {/* Diff header with mode toggle */}
      <div className="flex items-center justify-between px-4 py-3 border-b border-gray-700 bg-gray-900">
        <div className="flex items-center gap-4 text-sm">
          <span className="text-gray-400">
            <span className="text-green-400 font-semibold">+{stats.additions}</span>
            {' / '}
            <span className="text-red-400 font-semibold">-{stats.deletions}</span>
          </span>
          {filePath && (
            <span className="text-gray-500 text-xs font-mono">{filePath}</span>
          )}
        </div>

        {/* View mode toggle */}
        <div className="flex items-center gap-2">
          <button
            onClick={() => setMode('unified')}
            className={`px-3 py-1 text-xs font-medium rounded transition-colors ${
              mode === 'unified'
                ? 'bg-indigo-600 text-white'
                : 'bg-gray-800 text-gray-400 hover:text-gray-200'
            }`}
          >
            Unified
          </button>
          <button
            onClick={() => setMode('split')}
            className={`px-3 py-1 text-xs font-medium rounded transition-colors ${
              mode === 'split'
                ? 'bg-indigo-600 text-white'
                : 'bg-gray-800 text-gray-400 hover:text-gray-200'
            }`}
          >
            Split
          </button>
        </div>
      </div>

      {/* Diff content */}
      <div className="overflow-auto max-h-[600px]">
        {mode === 'unified' ? (
          <UnifiedDiffView
            hunks={hunks}
            comments={comments}
            filePath={filePath}
            activeCommentLine={activeCommentLine}
            onLineClick={handleLineClick}
            onCommentSubmit={handleCommentSubmit}
            onCommentCancel={handleCommentCancel}
            isSubmitting={addCommentMutation.isPending}
          />
        ) : (
          <SplitDiffView
            hunks={hunks}
            comments={comments}
            filePath={filePath}
            activeCommentLine={activeCommentLine}
            onLineClick={handleLineClick}
            onCommentSubmit={handleCommentSubmit}
            onCommentCancel={handleCommentCancel}
            isSubmitting={addCommentMutation.isPending}
          />
        )}
      </div>

      {/* Error message */}
      {addCommentMutation.isError && (
        <div className="px-4 py-2 bg-red-900/20 border-t border-red-800 text-red-400 text-sm">
          Failed to submit comment. Please try again.
        </div>
      )}
    </div>
  )
}
