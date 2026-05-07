/**
 * CommitGraph — SVG-based commit DAG visualization
 *
 * Displays:
 * - Topologically sorted commits (newest first)
 * - Each branch assigned a lane (x-coordinate)
 * - Commit nodes as circles
 * - Parent edges as cubic bezier paths
 * - Branch labels at lane heads
 * - Color-coded lanes (deterministic from branch name hash)
 *
 * Features:
 * - Handles merge commits (multiple parent edges)
 * - Automatic lane assignment to minimize edge crossings
 * - Responsive SVG rendering
 * - Hover states for commits
 */

import { useMemo } from 'react'

// ─── Types ────────────────────────────────────────────────────────────────────

export interface CommitNode {
  sha: string
  parents: string[]
  branch: string | null
  message: string
  authoredAt: string
}

interface LayoutNode {
  sha: string
  parents: string[]
  branch: string | null
  message: string
  x: number // lane index
  y: number // vertical position
  color: string
}

interface Edge {
  from: { x: number; y: number }
  to: { x: number; y: number }
  color: string
}

// ─── Constants ────────────────────────────────────────────────────────────────

const LANE_WIDTH = 40
const ROW_HEIGHT = 60
const NODE_RADIUS = 6
const PADDING = 20

// ─── Color Palette ────────────────────────────────────────────────────────────

const COLORS = [
  '#6366f1', // indigo
  '#8b5cf6', // purple
  '#ec4899', // pink
  '#f59e0b', // amber
  '#10b981', // emerald
  '#3b82f6', // blue
  '#ef4444', // red
  '#14b8a6', // teal
  '#f97316', // orange
  '#a855f7', // violet
]

/** Hash a string to a deterministic color from the palette. */
function hashColor(str: string): string {
  let hash = 0
  for (let i = 0; i < str.length; i++) {
    hash = (hash << 5) - hash + str.charCodeAt(i)
    hash = hash & hash // Convert to 32-bit integer
  }
  return COLORS[Math.abs(hash) % COLORS.length]
}

// ─── Layout Algorithm ─────────────────────────────────────────────────────────

/**
 * Assigns lanes (x-coordinates) to commits to minimize edge crossings.
 * Uses a greedy algorithm: each commit tries to use the same lane as its first parent,
 * or finds the leftmost available lane.
 */
function layoutCommits(commits: CommitNode[]): LayoutNode[] {
  const layoutNodes: LayoutNode[] = []
  const shaToNode = new Map<string, LayoutNode>()
  const laneBranches = new Map<number, string>() // lane -> branch name
  let nextFreeLane = 0

  for (let i = 0; i < commits.length; i++) {
    const commit = commits[i]
    const y = i * ROW_HEIGHT + PADDING

    // Determine lane assignment
    let lane: number

    if (commit.parents.length === 0) {
      // Root commit — assign a new lane
      lane = nextFreeLane++
    } else {
      // Try to use the same lane as the first parent
      const firstParent = shaToNode.get(commit.parents[0])
      if (firstParent) {
        lane = firstParent.x
      } else {
        // Parent not yet laid out (shouldn't happen with topological sort)
        // Assign a new lane
        lane = nextFreeLane++
      }
    }

    // Determine color (from branch name or default)
    const branchName = commit.branch || 'default'
    const color = hashColor(branchName)

    // Update lane-branch mapping
    if (commit.branch) {
      laneBranches.set(lane, commit.branch)
    }

    const node: LayoutNode = {
      sha: commit.sha,
      parents: commit.parents,
      branch: commit.branch,
      message: commit.message,
      x: lane,
      y,
      color,
    }

    layoutNodes.push(node)
    shaToNode.set(commit.sha, node)
  }

  return layoutNodes
}

/**
 * Generates edges (parent links) for the commit graph.
 */
function generateEdges(nodes: LayoutNode[]): Edge[] {
  const edges: Edge[] = []
  const shaToNode = new Map<string, LayoutNode>()

  for (const node of nodes) {
    shaToNode.set(node.sha, node)
  }

  for (const node of nodes) {
    for (const parentSha of node.parents) {
      const parent = shaToNode.get(parentSha)
      if (parent) {
        edges.push({
          from: { x: node.x * LANE_WIDTH + PADDING, y: node.y },
          to: { x: parent.x * LANE_WIDTH + PADDING, y: parent.y },
          color: node.color,
        })
      }
    }
  }

  return edges
}

// ─── CommitGraph Component ────────────────────────────────────────────────────

interface CommitGraphProps {
  commits: CommitNode[]
  onCommitClick?: (sha: string) => void
}

export default function CommitGraph({ commits, onCommitClick }: CommitGraphProps) {
  const { layoutNodes, edges, width, height } = useMemo(() => {
    if (commits.length === 0) {
      return { layoutNodes: [], edges: [], width: 0, height: 0 }
    }

    const layoutNodes = layoutCommits(commits)
    const edges = generateEdges(layoutNodes)

    const maxLane = Math.max(...layoutNodes.map((n) => n.x), 0)
    const width = (maxLane + 1) * LANE_WIDTH + PADDING * 2
    const height = commits.length * ROW_HEIGHT + PADDING * 2

    return { layoutNodes, edges, width, height }
  }, [commits])

  if (commits.length === 0) {
    return (
      <div className="bg-gray-800 border border-gray-700 rounded-lg p-8 text-center">
        <p className="text-sm text-gray-500">No commits to display.</p>
      </div>
    )
  }

  return (
    <div className="bg-gray-800 border border-gray-700 rounded-lg overflow-auto">
      <svg
        width={width}
        height={height}
        className="block"
        style={{ minWidth: '100%' }}
      >
        {/* Render edges (parent links) */}
        <g>
          {edges.map((edge, i) => {
            // Cubic bezier curve for smooth edges
            const dx = edge.to.x - edge.from.x
            const dy = edge.to.y - edge.from.y
            const controlOffset = Math.min(Math.abs(dy) / 2, 30)

            const path = `
              M ${edge.from.x} ${edge.from.y}
              C ${edge.from.x} ${edge.from.y + controlOffset},
                ${edge.to.x} ${edge.to.y - controlOffset},
                ${edge.to.x} ${edge.to.y}
            `

            return (
              <path
                key={i}
                d={path}
                stroke={edge.color}
                strokeWidth={2}
                fill="none"
                opacity={0.6}
              />
            )
          })}
        </g>

        {/* Render commit nodes */}
        <g>
          {layoutNodes.map((node) => {
            const cx = node.x * LANE_WIDTH + PADDING
            const cy = node.y

            return (
              <g key={node.sha}>
                {/* Commit circle */}
                <circle
                  cx={cx}
                  cy={cy}
                  r={NODE_RADIUS}
                  fill={node.color}
                  stroke="#1f2937"
                  strokeWidth={2}
                  className="cursor-pointer hover:r-8 transition-all"
                  onClick={() => onCommitClick?.(node.sha)}
                >
                  <title>
                    {node.message.split('\n')[0]} ({node.sha.slice(0, 7)})
                  </title>
                </circle>

                {/* Branch label (if this is a branch head) */}
                {node.branch && (
                  <g>
                    <rect
                      x={cx + NODE_RADIUS + 8}
                      y={cy - 10}
                      width={node.branch.length * 6 + 12}
                      height={20}
                      fill={node.color}
                      rx={4}
                      opacity={0.9}
                    />
                    <text
                      x={cx + NODE_RADIUS + 14}
                      y={cy + 4}
                      fontSize={12}
                      fill="white"
                      fontWeight="600"
                      className="select-none"
                    >
                      {node.branch}
                    </text>
                  </g>
                )}

                {/* Commit message (truncated) */}
                <text
                  x={cx + NODE_RADIUS + (node.branch ? node.branch.length * 6 + 28 : 12)}
                  y={cy + 4}
                  fontSize={12}
                  fill="#9ca3af"
                  className="select-none"
                >
                  {node.message.split('\n')[0].slice(0, 50)}
                  {node.message.split('\n')[0].length > 50 ? '...' : ''}
                </text>
              </g>
            )
          })}
        </g>
      </svg>
    </div>
  )
}
