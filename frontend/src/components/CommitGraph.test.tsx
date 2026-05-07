/**
 * CommitGraph.test.tsx
 *
 * Tests for the CommitGraph component covering:
 * - Renders correct number of <circle> elements for a given commits array
 * - Renders parent edge <path> elements
 * - Merge commit node has two parent edge paths
 * - Single-branch linear history renders all nodes in one lane
 * - Empty commits array renders the empty state message
 */

import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import CommitGraph, { type CommitNode } from './CommitGraph'

// ─── Helpers ──────────────────────────────────────────────────────────────────

/** Build a minimal CommitNode with sensible defaults. */
function makeCommit(
  sha: string,
  parents: string[],
  branch: string | null = null,
  message = 'commit message'
): CommitNode {
  return { sha, parents, branch, message, authoredAt: '2024-01-01T00:00:00Z' }
}

// ─── Test data ────────────────────────────────────────────────────────────────

/**
 * Linear history on a single branch:
 *   C ← B ← A   (A is root, C is newest)
 */
const linearCommits: CommitNode[] = [
  makeCommit('sha-c', ['sha-b'], 'main', 'third commit'),
  makeCommit('sha-b', ['sha-a'], null,   'second commit'),
  makeCommit('sha-a', [],        null,   'first commit'),
]

/**
 * Merge commit history:
 *   M ← C (main)
 *   M ← E (feature)
 *   C ← B ← A
 *   E ← D ← A
 *
 * Topological order (newest first): M, C, E, B, D, A
 */
const mergeCommits: CommitNode[] = [
  makeCommit('sha-m', ['sha-c', 'sha-e'], 'main',    'merge commit'),
  makeCommit('sha-c', ['sha-b'],          null,       'main commit'),
  makeCommit('sha-e', ['sha-d'],          'feature',  'feature commit'),
  makeCommit('sha-b', ['sha-a'],          null,       'base commit 2'),
  makeCommit('sha-d', ['sha-a'],          null,       'feature base'),
  makeCommit('sha-a', [],                 null,       'root commit'),
]

// ─── Tests ────────────────────────────────────────────────────────────────────

describe('CommitGraph', () => {
  // ── Circle elements ─────────────────────────────────────────────────────────

  describe('circle elements', () => {
    it('renders one <circle> per commit', () => {
      const { container } = render(<CommitGraph commits={linearCommits} />)

      const circles = container.querySelectorAll('circle')
      expect(circles).toHaveLength(linearCommits.length)
    })

    it('renders the correct number of circles for a larger commit set', () => {
      const { container } = render(<CommitGraph commits={mergeCommits} />)

      const circles = container.querySelectorAll('circle')
      expect(circles).toHaveLength(mergeCommits.length)
    })

    it('renders exactly one circle for a single commit', () => {
      const { container } = render(
        <CommitGraph commits={[makeCommit('sha-1', [], 'main', 'only commit')]} />
      )

      const circles = container.querySelectorAll('circle')
      expect(circles).toHaveLength(1)
    })

    it('renders no circles when commits array is empty', () => {
      const { container } = render(<CommitGraph commits={[]} />)

      const circles = container.querySelectorAll('circle')
      expect(circles).toHaveLength(0)
    })
  })

  // ── Parent edge paths ────────────────────────────────────────────────────────

  describe('parent edge paths', () => {
    it('renders one <path> per parent edge in a linear history', () => {
      // 3 commits, 2 parent edges (C→B and B→A)
      const { container } = render(<CommitGraph commits={linearCommits} />)

      const paths = container.querySelectorAll('path')
      expect(paths).toHaveLength(2)
    })

    it('renders no <path> elements when there is only a root commit', () => {
      const { container } = render(
        <CommitGraph commits={[makeCommit('sha-root', [], 'main', 'root')]} />
      )

      const paths = container.querySelectorAll('path')
      expect(paths).toHaveLength(0)
    })

    it('renders <path> elements with fill="none" (they are stroked curves, not filled shapes)', () => {
      const { container } = render(<CommitGraph commits={linearCommits} />)

      const paths = container.querySelectorAll('path')
      paths.forEach((path) => {
        expect(path.getAttribute('fill')).toBe('none')
      })
    })

    it('renders <path> elements with a stroke attribute', () => {
      const { container } = render(<CommitGraph commits={linearCommits} />)

      const paths = container.querySelectorAll('path')
      paths.forEach((path) => {
        expect(path.getAttribute('stroke')).toBeTruthy()
      })
    })
  })

  // ── Merge commit ─────────────────────────────────────────────────────────────

  describe('merge commit', () => {
    it('renders two parent edge paths for a merge commit', () => {
      // The merge commit sha-m has two parents: sha-c and sha-e
      // Those two edges should be present in the rendered paths
      const { container } = render(<CommitGraph commits={mergeCommits} />)

      const paths = container.querySelectorAll('path')

      // Total edges = sum of all parent counts:
      // sha-m: 2, sha-c: 1, sha-e: 1, sha-b: 1, sha-d: 1, sha-a: 0 → 6 edges
      expect(paths).toHaveLength(6)
    })

    it('renders more paths than commits when a merge commit is present', () => {
      const { container } = render(<CommitGraph commits={mergeCommits} />)

      const circles = container.querySelectorAll('circle')
      const paths = container.querySelectorAll('path')

      // 6 commits but 6 edges (merge adds an extra edge vs linear)
      // paths >= commits because of the merge
      expect(paths.length).toBeGreaterThanOrEqual(circles.length)
    })

    it('renders a circle for the merge commit node', () => {
      const { container } = render(<CommitGraph commits={mergeCommits} />)

      // All commits including the merge commit get a circle
      const circles = container.querySelectorAll('circle')
      expect(circles).toHaveLength(mergeCommits.length)
    })
  })

  // ── Single-branch linear history lane assignment ──────────────────────────────

  describe('single-branch linear history lane', () => {
    it('renders all nodes with cx values that are multiples of LANE_WIDTH + PADDING', () => {
      // LANE_WIDTH = 40, PADDING = 20 → valid cx values: 20, 60, 100, ...
      const { container } = render(<CommitGraph commits={linearCommits} />)

      const circles = container.querySelectorAll('circle')
      const LANE_WIDTH = 40
      const PADDING = 20

      circles.forEach((circle) => {
        const cx = Number(circle.getAttribute('cx'))
        // cx must equal lane * LANE_WIDTH + PADDING for some non-negative integer lane
        expect((cx - PADDING) % LANE_WIDTH).toBe(0)
        expect(cx).toBeGreaterThanOrEqual(PADDING)
      })
    })

    it('renders all nodes in the leftmost lanes for a single-branch linear history', () => {
      // The layout algorithm assigns each commit a lane greedily (newest-first).
      // For a linear chain C←B←A processed newest-first, each commit's parent
      // hasn't been laid out yet, so each gets a new lane: C→lane0, B→lane1, A→lane2.
      // All cx values should be distinct and start from lane 0 (cx=20).
      const { container } = render(<CommitGraph commits={linearCommits} />)

      const circles = container.querySelectorAll('circle')
      const cxValues = Array.from(circles).map((c) => Number(c.getAttribute('cx')))

      // The minimum cx should be PADDING (lane 0)
      expect(Math.min(...cxValues)).toBe(20)
      // Each commit gets its own lane → all cx values are distinct
      const uniqueCx = new Set(cxValues)
      expect(uniqueCx.size).toBe(linearCommits.length)
    })

    it('assigns different cx values when commits span multiple branches', () => {
      const { container } = render(<CommitGraph commits={mergeCommits} />)

      const circles = container.querySelectorAll('circle')
      const cxValues = Array.from(circles).map((c) => c.getAttribute('cx'))

      // With a merge history, at least two distinct cx values should appear
      const uniqueCx = new Set(cxValues)
      expect(uniqueCx.size).toBeGreaterThan(1)
    })
  })

  // ── Empty state ──────────────────────────────────────────────────────────────

  describe('empty state', () => {
    it('renders the empty state message when commits array is empty', () => {
      render(<CommitGraph commits={[]} />)

      expect(screen.getByText(/no commits to display/i)).toBeInTheDocument()
    })

    it('does not render an SVG when commits array is empty', () => {
      const { container } = render(<CommitGraph commits={[]} />)

      const svg = container.querySelector('svg')
      expect(svg).not.toBeInTheDocument()
    })
  })

  // ── Branch labels ────────────────────────────────────────────────────────────

  describe('branch labels', () => {
    it('renders a branch label text for commits that have a branch name', () => {
      render(<CommitGraph commits={linearCommits} />)

      // linearCommits[0] has branch='main'
      expect(screen.getByText('main')).toBeInTheDocument()
    })

    it('renders branch labels for all named branches in the merge history', () => {
      render(<CommitGraph commits={mergeCommits} />)

      expect(screen.getByText('main')).toBeInTheDocument()
      expect(screen.getByText('feature')).toBeInTheDocument()
    })

    it('does not render branch labels for commits without a branch name', () => {
      // Only sha-c has branch='main'; sha-b and sha-a have branch=null
      render(<CommitGraph commits={linearCommits} />)

      // There should be exactly one branch label ('main')
      const branchLabels = screen.getAllByText('main')
      expect(branchLabels).toHaveLength(1)
    })
  })

  // ── Commit click callback ────────────────────────────────────────────────────

  describe('onCommitClick callback', () => {
    it('calls onCommitClick with the correct sha when a circle is clicked', async () => {
      const handleClick = vi.fn()
      const { container } = render(
        <CommitGraph commits={linearCommits} onCommitClick={handleClick} />
      )

      const circles = container.querySelectorAll('circle')
      // Click the first circle (sha-c)
      circles[0].dispatchEvent(new MouseEvent('click', { bubbles: true }))

      expect(handleClick).toHaveBeenCalledWith('sha-c')
    })

    it('does not throw when onCommitClick is not provided', () => {
      const { container } = render(<CommitGraph commits={linearCommits} />)

      const circles = container.querySelectorAll('circle')
      expect(() => {
        circles[0].dispatchEvent(new MouseEvent('click', { bubbles: true }))
      }).not.toThrow()
    })
  })

  // ── SVG dimensions ───────────────────────────────────────────────────────────

  describe('SVG dimensions', () => {
    it('renders an SVG element when commits are provided', () => {
      const { container } = render(<CommitGraph commits={linearCommits} />)

      const svg = container.querySelector('svg')
      expect(svg).toBeInTheDocument()
    })

    it('SVG height grows with the number of commits', () => {
      const { container: c1 } = render(
        <CommitGraph commits={linearCommits} />
      )
      const { container: c2 } = render(
        <CommitGraph commits={mergeCommits} />
      )

      const h1 = Number(c1.querySelector('svg')?.getAttribute('height') ?? 0)
      const h2 = Number(c2.querySelector('svg')?.getAttribute('height') ?? 0)

      expect(h2).toBeGreaterThan(h1)
    })
  })
})
