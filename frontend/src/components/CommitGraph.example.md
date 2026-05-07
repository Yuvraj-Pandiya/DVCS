# CommitGraph Component - Usage Examples

## Basic Usage

```tsx
import CommitGraph, { CommitNode } from './components/CommitGraph'

const commits: CommitNode[] = [
  {
    sha: 'abc123def456',
    parents: ['def456ghi789'],
    branch: 'main',
    message: 'Add new feature',
    authoredAt: '2024-01-15T10:30:00Z',
  },
  {
    sha: 'def456ghi789',
    parents: [],
    branch: null,
    message: 'Initial commit',
    authoredAt: '2024-01-15T09:00:00Z',
  },
]

function MyPage() {
  return <CommitGraph commits={commits} />
}
```

## With Click Handler

```tsx
function MyPage() {
  const handleCommitClick = (sha: string) => {
    console.log('Navigating to commit:', sha)
    // Navigate to commit detail page
    navigate(`/repo/commit/${sha}`)
  }

  return (
    <CommitGraph 
      commits={commits} 
      onCommitClick={handleCommitClick}
    />
  )
}
```

## Merge Commit Example

```tsx
const commitsWithMerge: CommitNode[] = [
  {
    sha: 'merge123',
    parents: ['main456', 'feature789'], // Two parents = merge commit
    branch: 'main',
    message: 'Merge pull request #42',
    authoredAt: '2024-01-15T15:00:00Z',
  },
  {
    sha: 'main456',
    parents: ['base000'],
    branch: null,
    message: 'Update documentation',
    authoredAt: '2024-01-15T14:00:00Z',
  },
  {
    sha: 'feature789',
    parents: ['base000'],
    branch: 'feature/new-ui',
    message: 'Implement new UI',
    authoredAt: '2024-01-15T13:00:00Z',
  },
  {
    sha: 'base000',
    parents: [],
    branch: null,
    message: 'Initial commit',
    authoredAt: '2024-01-15T10:00:00Z',
  },
]

// This will render a graph showing the merge commit with two parent edges
<CommitGraph commits={commitsWithMerge} />
```

## Features

- **Topological Sorting**: Commits should be provided in topological order (newest first)
- **Lane Assignment**: Each branch is automatically assigned a visual lane (x-coordinate)
- **Merge Commits**: Commits with multiple parents will have multiple edges drawn
- **Branch Labels**: Commits with a `branch` value will display a colored label
- **Deterministic Colors**: Branch colors are generated deterministically from the branch name
- **Interactive**: Hover over commits to see full message and SHA
- **Responsive**: SVG scales to fit the content

## Data Requirements

### CommitNode Interface

```typescript
interface CommitNode {
  sha: string           // Full commit SHA hash
  parents: string[]     // Array of parent commit SHAs (empty for root, 2+ for merge)
  branch: string | null // Branch name (null if not a branch head)
  message: string       // Commit message
  authoredAt: string    // ISO 8601 timestamp
}
```

### Important Notes

1. **Topological Order**: Commits must be sorted topologically (newest first) before passing to the component
2. **Parent References**: All parent SHAs in `parents` array must reference commits that exist in the `commits` array
3. **Branch Labels**: Only set `branch` for commits that are branch heads (tip of a branch)
4. **Empty State**: The component handles empty arrays gracefully with a "No commits to display" message

## Styling

The component uses Tailwind CSS classes and is designed for dark mode:
- Background: `bg-gray-800`
- Border: `border-gray-700`
- Text: Various gray shades
- Accent colors: Indigo, purple, pink, amber, emerald, blue, red, teal, orange, violet

## Performance

- Uses `useMemo` to cache layout calculations
- Efficient SVG rendering
- Handles large commit graphs (tested with 100+ commits)
