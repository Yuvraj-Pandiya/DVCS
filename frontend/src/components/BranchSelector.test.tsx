/**
 * BranchSelector.test.tsx
 *
 * Tests for the BranchSelector component covering:
 * - Renders all branches (and tags) initially when the dropdown is opened
 * - Typing in the search input reduces the displayed list via fuzzy filter
 * - Pressing ↓ moves keyboard focus to the next item
 * - Pressing Enter calls `onChange` with the selected branch name
 * - Pressing Escape closes the dropdown
 */

import { describe, it, expect, vi, beforeEach, beforeAll } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import BranchSelector from './BranchSelector'

// jsdom does not implement scrollIntoView — mock it globally
beforeAll(() => {
  window.HTMLElement.prototype.scrollIntoView = vi.fn()
})

// ─── Mocks ────────────────────────────────────────────────────────────────────

// Mock the API client hook so BranchSelector doesn't need a real AuthContext.
// We expose a mutable `mockGet` so individual tests can control the response.
const mockGet = vi.fn()

vi.mock('../api/client', () => ({
  useApiClient: () => ({
    get: mockGet,
    post: vi.fn(),
    patch: vi.fn(),
    delete: vi.fn(),
  }),
}))

// ─── Test data ────────────────────────────────────────────────────────────────

const BRANCHES = [
  { name: 'main',          headSha: 'abc123', protected: true  },
  { name: 'develop',       headSha: 'def456', protected: false },
  { name: 'feature/login', headSha: 'ghi789', protected: false },
]

const TAGS = [
  { name: 'v1.0.0', commitSha: 'aaa111' },
  { name: 'v2.0.0', commitSha: 'bbb222' },
]

// ─── Helpers ──────────────────────────────────────────────────────────────────

/**
 * Default mock: branches endpoint returns BRANCHES, tags endpoint returns TAGS.
 */
function setupDefaultMocks() {
  mockGet.mockImplementation((path: string) => {
    if (path.includes('/branches')) return Promise.resolve(BRANCHES)
    if (path.includes('/tags'))     return Promise.resolve(TAGS)
    return Promise.resolve([])
  })
}

/**
 * Renders BranchSelector with sensible defaults and opens the dropdown.
 * Returns the userEvent instance and the rendered utilities.
 */
async function renderAndOpen(props?: Partial<Parameters<typeof BranchSelector>[0]>) {
  const user = userEvent.setup()
  const onChange = vi.fn()

  const utils = render(
    <BranchSelector
      repoOwner="alice"
      repoName="myrepo"
      currentRef="main"
      onChange={onChange}
      {...props}
    />
  )

  // Open the dropdown by clicking the trigger button
  const trigger = screen.getByRole('button', { name: /current branch/i })
  await user.click(trigger)

  // Wait for the async fetch to complete and items to appear
  await waitFor(() => {
    expect(screen.getByPlaceholderText(/search branches and tags/i)).toBeInTheDocument()
  })

  return { user, onChange, ...utils }
}

/**
 * Returns the dropdown dialog element (scoped queries avoid matching the trigger button).
 */
function getDropdown() {
  return screen.getByRole('dialog', { name: /select branch or tag/i })
}

// ─── Tests ────────────────────────────────────────────────────────────────────

describe('BranchSelector', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setupDefaultMocks()
  })

  // ── Initial render ──────────────────────────────────────────────────────────

  describe('initial render', () => {
    it('renders the trigger button with the current ref label', () => {
      render(
        <BranchSelector
          repoOwner="alice"
          repoName="myrepo"
          currentRef="main"
          onChange={vi.fn()}
        />
      )

      // The trigger button contains the current ref text
      const trigger = screen.getByRole('button', { name: /current branch/i })
      expect(trigger).toHaveTextContent('main')
    })

    it('does not show the dropdown before the trigger is clicked', () => {
      render(
        <BranchSelector
          repoOwner="alice"
          repoName="myrepo"
          currentRef="main"
          onChange={vi.fn()}
        />
      )

      expect(screen.queryByPlaceholderText(/search branches and tags/i)).not.toBeInTheDocument()
    })

    it('opens the dropdown when the trigger button is clicked', async () => {
      const user = userEvent.setup()
      render(
        <BranchSelector
          repoOwner="alice"
          repoName="myrepo"
          currentRef="main"
          onChange={vi.fn()}
        />
      )

      await user.click(screen.getByRole('button', { name: /current branch/i }))

      expect(screen.getByPlaceholderText(/search branches and tags/i)).toBeInTheDocument()
    })

    it('fetches branches from the correct API endpoint on open', async () => {
      await renderAndOpen()

      expect(mockGet).toHaveBeenCalledWith(
        '/api/repos/alice/myrepo/branches'
      )
    })

    it('renders all branches initially after opening', async () => {
      await renderAndOpen()

      // Scope queries to the dropdown to avoid matching the trigger button
      const dropdown = getDropdown()

      await waitFor(() => {
        expect(within(dropdown).getByRole('button', { name: /branch: main/i })).toBeInTheDocument()
        expect(within(dropdown).getByRole('button', { name: /branch: develop/i })).toBeInTheDocument()
        expect(within(dropdown).getByRole('button', { name: /branch: feature\/login/i })).toBeInTheDocument()
      })
    })

    it('renders all tags initially after opening', async () => {
      await renderAndOpen()

      const dropdown = getDropdown()

      await waitFor(() => {
        expect(within(dropdown).getByRole('button', { name: /tag: v1\.0\.0/i })).toBeInTheDocument()
        expect(within(dropdown).getByRole('button', { name: /tag: v2\.0\.0/i })).toBeInTheDocument()
      })
    })

    it('renders a "Branches" section header', async () => {
      await renderAndOpen()

      const dropdown = getDropdown()

      await waitFor(() => {
        expect(within(dropdown).getByText(/branches/i)).toBeInTheDocument()
      })
    })

    it('renders a "Tags" section header', async () => {
      await renderAndOpen()

      const dropdown = getDropdown()

      await waitFor(() => {
        expect(within(dropdown).getByText(/tags/i)).toBeInTheDocument()
      })
    })

    it('shows "No branches or tags found" when both lists are empty', async () => {
      mockGet.mockResolvedValue([])

      await renderAndOpen()

      await waitFor(() => {
        expect(screen.getByText(/no branches or tags found/i)).toBeInTheDocument()
      })
    })
  })

  // ── Fuzzy filter ────────────────────────────────────────────────────────────

  describe('fuzzy filter', () => {
    it('reduces the displayed list when the user types in the search input', async () => {
      const { user } = await renderAndOpen()

      const dropdown = getDropdown()

      // Wait for all items to appear
      await waitFor(() => {
        expect(within(dropdown).getByRole('button', { name: /branch: main/i })).toBeInTheDocument()
      })

      const input = screen.getByPlaceholderText(/search branches and tags/i)
      await user.type(input, 'develop')

      // Only "develop" should remain visible; "main" and "feature/login" should be gone
      await waitFor(() => {
        expect(within(dropdown).getByRole('button', { name: /branch: develop/i })).toBeInTheDocument()
        expect(within(dropdown).queryByRole('button', { name: /branch: main/i })).not.toBeInTheDocument()
      })
    })

    it('filters branches by partial name match', async () => {
      const { user } = await renderAndOpen()

      const dropdown = getDropdown()

      await waitFor(() => {
        expect(within(dropdown).getByRole('button', { name: /branch: main/i })).toBeInTheDocument()
      })

      const input = screen.getByPlaceholderText(/search branches and tags/i)
      await user.type(input, 'feat')

      await waitFor(() => {
        expect(within(dropdown).getByRole('button', { name: /branch: feature\/login/i })).toBeInTheDocument()
        expect(within(dropdown).queryByRole('button', { name: /branch: main/i })).not.toBeInTheDocument()
        expect(within(dropdown).queryByRole('button', { name: /branch: develop/i })).not.toBeInTheDocument()
      })
    })

    it('filters tags by name', async () => {
      const { user } = await renderAndOpen()

      const dropdown = getDropdown()

      await waitFor(() => {
        expect(within(dropdown).getByRole('button', { name: /tag: v1\.0\.0/i })).toBeInTheDocument()
      })

      const input = screen.getByPlaceholderText(/search branches and tags/i)
      await user.type(input, 'v1')

      await waitFor(() => {
        expect(within(dropdown).getByRole('button', { name: /tag: v1\.0\.0/i })).toBeInTheDocument()
        expect(within(dropdown).queryByRole('button', { name: /tag: v2\.0\.0/i })).not.toBeInTheDocument()
      })
    })

    it('shows "No branches or tags found" when the search matches nothing', async () => {
      const { user } = await renderAndOpen()

      const dropdown = getDropdown()

      await waitFor(() => {
        expect(within(dropdown).getByRole('button', { name: /branch: main/i })).toBeInTheDocument()
      })

      const input = screen.getByPlaceholderText(/search branches and tags/i)
      await user.type(input, 'zzzzzzzzz')

      await waitFor(() => {
        expect(screen.getByText(/no branches or tags found/i)).toBeInTheDocument()
      })
    })

    it('restores the full list when the search input is cleared', async () => {
      const { user } = await renderAndOpen()

      const dropdown = getDropdown()

      await waitFor(() => {
        expect(within(dropdown).getByRole('button', { name: /branch: main/i })).toBeInTheDocument()
      })

      const input = screen.getByPlaceholderText(/search branches and tags/i)
      await user.type(input, 'develop')

      await waitFor(() => {
        expect(within(dropdown).queryByRole('button', { name: /branch: main/i })).not.toBeInTheDocument()
      })

      await user.clear(input)

      await waitFor(() => {
        expect(within(dropdown).getByRole('button', { name: /branch: main/i })).toBeInTheDocument()
        expect(within(dropdown).getByRole('button', { name: /branch: develop/i })).toBeInTheDocument()
      })
    })
  })

  // ── Keyboard navigation ─────────────────────────────────────────────────────

  describe('keyboard navigation', () => {
    it('pressing ↓ moves keyboard focus to the next item (data-index increments)', async () => {
      const { user } = await renderAndOpen()

      const dropdown = getDropdown()

      // Wait for items to load
      await waitFor(() => {
        expect(within(dropdown).getByRole('button', { name: /branch: main/i })).toBeInTheDocument()
      })

      const input = screen.getByPlaceholderText(/search branches and tags/i)

      // Initially selectedIndex = 0 → first item (main) has bg-indigo-600
      const mainBtn = within(dropdown).getByRole('button', { name: /branch: main/i })
      expect(mainBtn).toHaveClass('bg-indigo-600')

      // Press ↓ once → selectedIndex = 1 → "develop" should be highlighted
      await user.type(input, '{ArrowDown}')

      await waitFor(() => {
        const developBtn = within(dropdown).getByRole('button', { name: /branch: develop/i })
        expect(developBtn).toHaveClass('bg-indigo-600')
        expect(mainBtn).not.toHaveClass('bg-indigo-600')
      })
    })

    it('pressing ↓ multiple times advances the selection through the list', async () => {
      const { user } = await renderAndOpen()

      const dropdown = getDropdown()

      await waitFor(() => {
        expect(within(dropdown).getByRole('button', { name: /branch: main/i })).toBeInTheDocument()
      })

      const input = screen.getByPlaceholderText(/search branches and tags/i)

      // Press ↓ twice → selectedIndex = 2 → "feature/login" should be highlighted
      await user.type(input, '{ArrowDown}{ArrowDown}')

      await waitFor(() => {
        const featureBtn = within(dropdown).getByRole('button', { name: /branch: feature\/login/i })
        expect(featureBtn).toHaveClass('bg-indigo-600')
      })
    })

    it('pressing ↓ does not go past the last item', async () => {
      const { user } = await renderAndOpen()

      const dropdown = getDropdown()

      await waitFor(() => {
        expect(within(dropdown).getByRole('button', { name: /branch: main/i })).toBeInTheDocument()
      })

      const input = screen.getByPlaceholderText(/search branches and tags/i)

      // Press ↓ many times — should clamp at last item (v2.0.0 tag, index 4)
      // There are 5 items total: 3 branches + 2 tags
      await user.type(input, '{ArrowDown}{ArrowDown}{ArrowDown}{ArrowDown}{ArrowDown}{ArrowDown}{ArrowDown}')

      await waitFor(() => {
        // The last item (v2.0.0 tag at index 4) should be highlighted
        const lastBtn = within(dropdown).getByRole('button', { name: /tag: v2\.0\.0/i })
        expect(lastBtn).toHaveClass('bg-indigo-600')
      })
    })

    it('pressing ↑ moves keyboard focus to the previous item', async () => {
      const { user } = await renderAndOpen()

      const dropdown = getDropdown()

      await waitFor(() => {
        expect(within(dropdown).getByRole('button', { name: /branch: main/i })).toBeInTheDocument()
      })

      const input = screen.getByPlaceholderText(/search branches and tags/i)

      // Move down to index 1, then back up to index 0
      await user.type(input, '{ArrowDown}{ArrowUp}')

      await waitFor(() => {
        const mainBtn = within(dropdown).getByRole('button', { name: /branch: main/i })
        expect(mainBtn).toHaveClass('bg-indigo-600')
      })
    })

    it('pressing ↑ does not go above the first item', async () => {
      const { user } = await renderAndOpen()

      const dropdown = getDropdown()

      await waitFor(() => {
        expect(within(dropdown).getByRole('button', { name: /branch: main/i })).toBeInTheDocument()
      })

      const input = screen.getByPlaceholderText(/search branches and tags/i)

      // Press ↑ multiple times from index 0 — should stay at index 0
      await user.type(input, '{ArrowUp}{ArrowUp}{ArrowUp}')

      await waitFor(() => {
        const mainBtn = within(dropdown).getByRole('button', { name: /branch: main/i })
        expect(mainBtn).toHaveClass('bg-indigo-600')
      })
    })
  })

  // ── Enter key selection ─────────────────────────────────────────────────────

  describe('Enter key selection', () => {
    it('pressing Enter calls onChange with the currently selected branch name', async () => {
      const { user, onChange } = await renderAndOpen()

      const dropdown = getDropdown()

      await waitFor(() => {
        expect(within(dropdown).getByRole('button', { name: /branch: main/i })).toBeInTheDocument()
      })

      const input = screen.getByPlaceholderText(/search branches and tags/i)

      // selectedIndex starts at 0 → "main" is selected
      await user.type(input, '{Enter}')

      expect(onChange).toHaveBeenCalledWith('main')
      expect(onChange).toHaveBeenCalledTimes(1)
    })

    it('pressing ↓ then Enter calls onChange with the second branch', async () => {
      const { user, onChange } = await renderAndOpen()

      const dropdown = getDropdown()

      await waitFor(() => {
        expect(within(dropdown).getByRole('button', { name: /branch: develop/i })).toBeInTheDocument()
      })

      const input = screen.getByPlaceholderText(/search branches and tags/i)

      // Move to index 1 (develop) then confirm
      await user.type(input, '{ArrowDown}{Enter}')

      expect(onChange).toHaveBeenCalledWith('develop')
    })

    it('pressing Enter closes the dropdown after selection', async () => {
      const { user } = await renderAndOpen()

      const dropdown = getDropdown()

      await waitFor(() => {
        expect(within(dropdown).getByRole('button', { name: /branch: main/i })).toBeInTheDocument()
      })

      const input = screen.getByPlaceholderText(/search branches and tags/i)
      await user.type(input, '{Enter}')

      await waitFor(() => {
        expect(screen.queryByPlaceholderText(/search branches and tags/i)).not.toBeInTheDocument()
      })
    })

    it('clicking a branch item calls onChange with that branch name', async () => {
      const { user, onChange } = await renderAndOpen()

      const dropdown = getDropdown()

      await waitFor(() => {
        expect(within(dropdown).getByRole('button', { name: /branch: develop/i })).toBeInTheDocument()
      })

      await user.click(within(dropdown).getByRole('button', { name: /branch: develop/i }))

      expect(onChange).toHaveBeenCalledWith('develop')
    })

    it('clicking a branch item closes the dropdown', async () => {
      const { user } = await renderAndOpen()

      const dropdown = getDropdown()

      await waitFor(() => {
        expect(within(dropdown).getByRole('button', { name: /branch: develop/i })).toBeInTheDocument()
      })

      await user.click(within(dropdown).getByRole('button', { name: /branch: develop/i }))

      await waitFor(() => {
        expect(screen.queryByPlaceholderText(/search branches and tags/i)).not.toBeInTheDocument()
      })
    })
  })

  // ── Escape key ──────────────────────────────────────────────────────────────

  describe('Escape key', () => {
    it('pressing Escape closes the dropdown', async () => {
      const { user } = await renderAndOpen()

      await waitFor(() => {
        expect(screen.getByPlaceholderText(/search branches and tags/i)).toBeInTheDocument()
      })

      const input = screen.getByPlaceholderText(/search branches and tags/i)
      await user.type(input, '{Escape}')

      await waitFor(() => {
        expect(screen.queryByPlaceholderText(/search branches and tags/i)).not.toBeInTheDocument()
      })
    })

    it('pressing Escape clears the search query', async () => {
      const { user } = await renderAndOpen()

      const dropdown = getDropdown()

      await waitFor(() => {
        expect(within(dropdown).getByRole('button', { name: /branch: main/i })).toBeInTheDocument()
      })

      const input = screen.getByPlaceholderText(/search branches and tags/i)
      await user.type(input, 'develop')

      // Verify filter is active
      await waitFor(() => {
        expect(within(dropdown).queryByRole('button', { name: /branch: main/i })).not.toBeInTheDocument()
      })

      await user.type(input, '{Escape}')

      // Dropdown is closed; re-open to verify search was cleared
      const trigger = screen.getByRole('button', { name: /current branch/i })
      await user.click(trigger)

      const newDropdown = getDropdown()

      await waitFor(() => {
        // All branches should be visible again (search was reset)
        expect(within(newDropdown).getByRole('button', { name: /branch: main/i })).toBeInTheDocument()
        expect(within(newDropdown).getByRole('button', { name: /branch: develop/i })).toBeInTheDocument()
      })
    })

    it('pressing Escape does not call onChange', async () => {
      const { user, onChange } = await renderAndOpen()

      await waitFor(() => {
        expect(screen.getByPlaceholderText(/search branches and tags/i)).toBeInTheDocument()
      })

      const input = screen.getByPlaceholderText(/search branches and tags/i)
      await user.type(input, '{Escape}')

      expect(onChange).not.toHaveBeenCalled()
    })
  })

  // ── Outside click ───────────────────────────────────────────────────────────

  describe('outside click', () => {
    it('closes the dropdown when clicking outside the component', async () => {
      const { user } = await renderAndOpen()

      await waitFor(() => {
        expect(screen.getByPlaceholderText(/search branches and tags/i)).toBeInTheDocument()
      })

      // Click outside the component
      await user.click(document.body)

      await waitFor(() => {
        expect(screen.queryByPlaceholderText(/search branches and tags/i)).not.toBeInTheDocument()
      })
    })
  })

  // ── aria attributes ─────────────────────────────────────────────────────────

  describe('accessibility', () => {
    it('trigger button has aria-expanded="false" when closed', () => {
      render(
        <BranchSelector
          repoOwner="alice"
          repoName="myrepo"
          currentRef="main"
          onChange={vi.fn()}
        />
      )

      const trigger = screen.getByRole('button', { name: /current branch/i })
      expect(trigger).toHaveAttribute('aria-expanded', 'false')
    })

    it('trigger button has aria-expanded="true" when open', async () => {
      const user = userEvent.setup()
      render(
        <BranchSelector
          repoOwner="alice"
          repoName="myrepo"
          currentRef="main"
          onChange={vi.fn()}
        />
      )

      const trigger = screen.getByRole('button', { name: /current branch/i })
      await user.click(trigger)

      expect(trigger).toHaveAttribute('aria-expanded', 'true')
    })

    it('dropdown has role="dialog" with an accessible label', async () => {
      await renderAndOpen()

      expect(screen.getByRole('dialog', { name: /select branch or tag/i })).toBeInTheDocument()
    })
  })
})
