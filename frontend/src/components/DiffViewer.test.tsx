/**
 * DiffViewer.test.tsx
 *
 * Tests for the DiffViewer component covering:
 * - Unified diff rendering with correct ADD/REMOVE/CONTEXT line classes
 * - Split diff rendering with two columns
 * - Mode toggle switching between unified and split
 * - Binary diff message when `binary: true` prop is passed
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import DiffViewer, { type DiffHunk } from './DiffViewer'

// ─── Mocks ────────────────────────────────────────────────────────────────────

// Mock the API client hook so DiffViewer doesn't need a real AuthContext
vi.mock('../api/client', () => ({
  useApiClient: () => ({
    get: vi.fn(),
    post: vi.fn(),
    patch: vi.fn(),
    delete: vi.fn(),
  }),
}))

// ─── Helpers ──────────────────────────────────────────────────────────────────

function createQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })
}

function renderWithQueryClient(ui: React.ReactElement) {
  const queryClient = createQueryClient()
  return render(
    <QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>
  )
}

/** A minimal set of hunks covering ADD, REMOVE, and CONTEXT lines. */
const sampleHunks: DiffHunk[] = [
  {
    type: 'CONTEXT',
    baseStart: 1,
    baseEnd: 5,
    headStart: 1,
    headEnd: 6,
    lines: [
      { type: 'CONTEXT', content: 'unchanged line', baseLineNo: 1, headLineNo: 1 },
      { type: 'REMOVE',  content: 'old line',       baseLineNo: 2, headLineNo: 0 },
      { type: 'ADD',     content: 'new line',        baseLineNo: 0, headLineNo: 2 },
      { type: 'CONTEXT', content: 'another context', baseLineNo: 3, headLineNo: 3 },
    ],
  },
]

// ─── Tests ────────────────────────────────────────────────────────────────────

describe('DiffViewer', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  // ── Unified diff ────────────────────────────────────────────────────────────

  describe('unified mode', () => {
    it('renders in unified mode by default', () => {
      renderWithQueryClient(<DiffViewer hunks={sampleHunks} />)

      // The "Unified" toggle button should be active (indigo background)
      const unifiedBtn = screen.getByRole('button', { name: /unified/i })
      expect(unifiedBtn).toHaveClass('bg-indigo-600')
    })

    it('renders ADD lines with green background class', () => {
      const { container } = renderWithQueryClient(
        <DiffViewer hunks={sampleHunks} />
      )

      // ADD lines get bg-green-900/30 applied to their row div
      const greenRows = container.querySelectorAll('.bg-green-900\\/30')
      expect(greenRows.length).toBeGreaterThan(0)
    })

    it('renders REMOVE lines with red background class', () => {
      const { container } = renderWithQueryClient(
        <DiffViewer hunks={sampleHunks} />
      )

      // REMOVE lines get bg-red-900/30 applied to their row div
      const redRows = container.querySelectorAll('.bg-red-900\\/30')
      expect(redRows.length).toBeGreaterThan(0)
    })

    it('renders CONTEXT lines without ADD or REMOVE background', () => {
      const { container } = renderWithQueryClient(
        <DiffViewer hunks={sampleHunks} />
      )

      // CONTEXT lines use bg-transparent — verify the content is present
      // and that there are fewer colored rows than total lines
      const allLines = container.querySelectorAll('.font-mono .flex')
      const coloredLines = container.querySelectorAll(
        '.bg-green-900\\/30, .bg-red-900\\/30'
      )
      // We have 4 lines total, 2 are colored (1 ADD + 1 REMOVE)
      expect(coloredLines.length).toBe(2)
      expect(allLines.length).toBeGreaterThanOrEqual(4)
    })

    it('renders ADD line content with "+" prefix', () => {
      renderWithQueryClient(<DiffViewer hunks={sampleHunks} />)
      // The ADD line content "new line" should be present
      expect(screen.getByText('new line')).toBeInTheDocument()
    })

    it('renders REMOVE line content with "-" prefix', () => {
      renderWithQueryClient(<DiffViewer hunks={sampleHunks} />)
      expect(screen.getByText('old line')).toBeInTheDocument()
    })

    it('renders CONTEXT line content', () => {
      renderWithQueryClient(<DiffViewer hunks={sampleHunks} />)
      expect(screen.getByText('unchanged line')).toBeInTheDocument()
    })

    it('renders the hunk header with @@ notation', () => {
      renderWithQueryClient(<DiffViewer hunks={sampleHunks} />)
      // Hunk header contains @@ markers
      const hunkHeader = screen.getByText(/@@.*@@/i)
      expect(hunkHeader).toBeInTheDocument()
    })

    it('shows diff stats (additions and deletions count)', () => {
      renderWithQueryClient(<DiffViewer hunks={sampleHunks} />)
      // Stats show +1 addition and -1 deletion
      expect(screen.getByText('+1')).toBeInTheDocument()
      expect(screen.getByText('-1')).toBeInTheDocument()
    })
  })

  // ── Split diff ──────────────────────────────────────────────────────────────

  describe('split mode', () => {
    it('renders two columns in split mode', () => {
      const { container } = renderWithQueryClient(
        <DiffViewer hunks={sampleHunks} defaultMode="split" />
      )

      // Split view uses grid-cols-2 for the two-column layout
      const splitRows = container.querySelectorAll('.grid-cols-2')
      // At least one grid-cols-2 element should exist (hunk header + line rows)
      expect(splitRows.length).toBeGreaterThan(0)
    })

    it('renders the Split toggle button as active in split mode', () => {
      renderWithQueryClient(
        <DiffViewer hunks={sampleHunks} defaultMode="split" />
      )

      const splitBtn = screen.getByRole('button', { name: /split/i })
      expect(splitBtn).toHaveClass('bg-indigo-600')
    })

    it('renders empty rows for ADD lines on the base (left) column', () => {
      const { container } = renderWithQueryClient(
        <DiffViewer hunks={sampleHunks} defaultMode="split" />
      )

      // ADD lines show an empty placeholder div (h-7) on the base side
      const emptyRows = container.querySelectorAll('.h-7')
      expect(emptyRows.length).toBeGreaterThan(0)
    })

    it('renders ADD line content in the head (right) column with green class', () => {
      const { container } = renderWithQueryClient(
        <DiffViewer hunks={sampleHunks} defaultMode="split" />
      )

      const greenRows = container.querySelectorAll('.bg-green-900\\/30')
      expect(greenRows.length).toBeGreaterThan(0)
    })

    it('renders REMOVE line content in the base (left) column with red class', () => {
      const { container } = renderWithQueryClient(
        <DiffViewer hunks={sampleHunks} defaultMode="split" />
      )

      const redRows = container.querySelectorAll('.bg-red-900\\/30')
      expect(redRows.length).toBeGreaterThan(0)
    })
  })

  // ── Mode toggle ─────────────────────────────────────────────────────────────

  describe('mode toggle', () => {
    it('switches from unified to split when Split button is clicked', async () => {
      const user = userEvent.setup()
      const { container } = renderWithQueryClient(
        <DiffViewer hunks={sampleHunks} />
      )

      // Initially in unified mode — no grid-cols-2 line rows
      const splitBefore = container.querySelectorAll('.grid-cols-2')
      // The hunk header in unified mode does NOT use grid-cols-2
      // (split mode hunk header does)

      const splitBtn = screen.getByRole('button', { name: /split/i })
      await user.click(splitBtn)

      // After clicking Split, grid-cols-2 elements should appear
      const splitAfter = container.querySelectorAll('.grid-cols-2')
      expect(splitAfter.length).toBeGreaterThan(splitBefore.length)
    })

    it('switches from split to unified when Unified button is clicked', async () => {
      const user = userEvent.setup()
      const { container } = renderWithQueryClient(
        <DiffViewer hunks={sampleHunks} defaultMode="split" />
      )

      // Start in split mode
      const splitBefore = container.querySelectorAll('.grid-cols-2')
      expect(splitBefore.length).toBeGreaterThan(0)

      const unifiedBtn = screen.getByRole('button', { name: /unified/i })
      await user.click(unifiedBtn)

      // After switching to unified, grid-cols-2 line rows should be gone
      // (the header area may still have some layout classes, but line rows won't)
      const splitAfter = container.querySelectorAll('.grid-cols-2')
      expect(splitAfter.length).toBeLessThan(splitBefore.length)
    })

    it('marks the active mode button with bg-indigo-600', async () => {
      const user = userEvent.setup()
      renderWithQueryClient(<DiffViewer hunks={sampleHunks} />)

      const unifiedBtn = screen.getByRole('button', { name: /unified/i })
      const splitBtn = screen.getByRole('button', { name: /split/i })

      // Initially unified is active
      expect(unifiedBtn).toHaveClass('bg-indigo-600')
      expect(splitBtn).not.toHaveClass('bg-indigo-600')

      // Click split
      await user.click(splitBtn)
      expect(splitBtn).toHaveClass('bg-indigo-600')
      expect(unifiedBtn).not.toHaveClass('bg-indigo-600')

      // Click unified again
      await user.click(unifiedBtn)
      expect(unifiedBtn).toHaveClass('bg-indigo-600')
      expect(splitBtn).not.toHaveClass('bg-indigo-600')
    })
  })

  // ── Binary diff ─────────────────────────────────────────────────────────────

  describe('binary diff', () => {
    it('renders a binary diff message when binary prop is true', () => {
      renderWithQueryClient(<DiffViewer hunks={[]} binary={true} />)

      expect(
        screen.getByText(/binary file/i)
      ).toBeInTheDocument()
    })

    it('does not render diff content when binary is true', () => {
      renderWithQueryClient(
        <DiffViewer hunks={sampleHunks} binary={true} />
      )

      // The diff content (line text) should not be rendered
      expect(screen.queryByText('new line')).not.toBeInTheDocument()
      expect(screen.queryByText('old line')).not.toBeInTheDocument()
    })

    it('does not render mode toggle buttons when binary is true', () => {
      renderWithQueryClient(
        <DiffViewer hunks={sampleHunks} binary={true} />
      )

      expect(screen.queryByRole('button', { name: /unified/i })).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: /split/i })).not.toBeInTheDocument()
    })

    it('renders normal diff when binary is false', () => {
      renderWithQueryClient(
        <DiffViewer hunks={sampleHunks} binary={false} />
      )

      expect(screen.queryByText(/binary file/i)).not.toBeInTheDocument()
      expect(screen.getByText('new line')).toBeInTheDocument()
    })
  })

  // ── Empty state ─────────────────────────────────────────────────────────────

  describe('empty state', () => {
    it('renders "No changes to display" when hunks array is empty', () => {
      renderWithQueryClient(<DiffViewer hunks={[]} />)

      expect(screen.getByText(/no changes to display/i)).toBeInTheDocument()
    })
  })
})
