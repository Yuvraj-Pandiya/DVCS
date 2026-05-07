/**
 * DiffViewer — unified and side-by-side diff viewer
 *
 * Displays:
 * - Unified diff view (single column with +/- lines)
 * - Side-by-side diff view (two columns: base | head)
 * - Line numbers for both base and head
 * - Syntax highlighting for added/removed/context lines
 * - Toggle between unified and split modes
 *
 * Features:
 * - Synchronized scrolling in split mode
 * - Color-coded additions (green) and deletions (red)
 * - Context lines (gray)
 * - Empty rows in split mode for alignment
 * - Responsive layout
 */

import { useState } from 'react'

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

type ViewMode = 'unified' | 'split'

// ─── UnifiedDiffView Component ────────────────────────────────────────────────

interface UnifiedDiffViewProps {
  hunks: DiffHunk[]
}

function UnifiedDiffView({ hunks }: UnifiedDiffViewProps) {
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

              return (
                <div
                  key={lineIndex}
                  className={`flex ${bgColor} hover:bg-gray-700/50 transition-colors`}
                >
                  {/* Line numbers */}
                  <div className="flex-shrink-0 w-24 px-2 py-1 text-right text-xs text-gray-600 select-none border-r border-gray-700">
                    <span className="inline-block w-10">
                      {line.baseLineNo > 0 ? line.baseLineNo : ''}
                    </span>
                    <span className="inline-block w-10">
                      {line.headLineNo > 0 ? line.headLineNo : ''}
                    </span>
                  </div>
                  {/* Line content */}
                  <div className={`flex-1 px-4 py-1 ${textColor} whitespace-pre overflow-x-auto`}>
                    <span className="select-none mr-2">{prefix}</span>
                    {line.content}
                  </div>
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
}

function SplitDiffView({ hunks }: SplitDiffViewProps) {
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
          <div className="grid grid-cols-2 gap-px bg-gray-700">
            {/* Base (left) column */}
            <div className="bg-gray-800">
              {hunk.lines.map((line, lineIndex) => {
                if (line.type === 'ADD') {
                  // Empty row for additions (only in head)
                  return (
                    <div
                      key={lineIndex}
                      className="flex bg-gray-900/50 h-7"
                    />
                  )
                }

                const bgColor =
                  line.type === 'REMOVE' ? 'bg-red-900/30' : 'bg-transparent'
                const textColor =
                  line.type === 'REMOVE' ? 'text-red-300' : 'text-gray-300'
                const prefix = line.type === 'REMOVE' ? '-' : ' '

                return (
                  <div
                    key={lineIndex}
                    className={`flex ${bgColor} hover:bg-gray-700/50 transition-colors`}
                  >
                    {/* Line number */}
                    <div className="flex-shrink-0 w-12 px-2 py-1 text-right text-xs text-gray-600 select-none border-r border-gray-700">
                      {line.baseLineNo > 0 ? line.baseLineNo : ''}
                    </div>
                    {/* Line content */}
                    <div className={`flex-1 px-4 py-1 ${textColor} whitespace-pre overflow-x-auto`}>
                      <span className="select-none mr-2">{prefix}</span>
                      {line.content}
                    </div>
                  </div>
                )
              })}
            </div>

            {/* Head (right) column */}
            <div className="bg-gray-800">
              {hunk.lines.map((line, lineIndex) => {
                if (line.type === 'REMOVE') {
                  // Empty row for deletions (only in base)
                  return (
                    <div
                      key={lineIndex}
                      className="flex bg-gray-900/50 h-7"
                    />
                  )
                }

                const bgColor =
                  line.type === 'ADD' ? 'bg-green-900/30' : 'bg-transparent'
                const textColor =
                  line.type === 'ADD' ? 'text-green-300' : 'text-gray-300'
                const prefix = line.type === 'ADD' ? '+' : ' '

                return (
                  <div
                    key={lineIndex}
                    className={`flex ${bgColor} hover:bg-gray-700/50 transition-colors`}
                  >
                    {/* Line number */}
                    <div className="flex-shrink-0 w-12 px-2 py-1 text-right text-xs text-gray-600 select-none border-r border-gray-700">
                      {line.headLineNo > 0 ? line.headLineNo : ''}
                    </div>
                    {/* Line content */}
                    <div className={`flex-1 px-4 py-1 ${textColor} whitespace-pre overflow-x-auto`}>
                      <span className="select-none mr-2">{prefix}</span>
                      {line.content}
                    </div>
                  </div>
                )
              })}
            </div>
          </div>
        </div>
      ))}
    </div>
  )
}

// ─── DiffViewer Component ─────────────────────────────────────────────────────

interface DiffViewerProps {
  hunks: DiffHunk[]
  defaultMode?: ViewMode
}

export default function DiffViewer({ hunks, defaultMode = 'unified' }: DiffViewerProps) {
  const [mode, setMode] = useState<ViewMode>(defaultMode)

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
          <UnifiedDiffView hunks={hunks} />
        ) : (
          <SplitDiffView hunks={hunks} />
        )}
      </div>
    </div>
  )
}
