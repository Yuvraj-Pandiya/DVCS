# BranchSelector Component

## Overview

The `BranchSelector` component provides a dropdown interface for selecting branches and tags in a repository. It features fuzzy search, keyboard navigation, and grouped display of branches and tags.

## Features

- **Automatic Data Fetching**: Fetches branches and tags from the API when the dropdown opens
- **Fuzzy Search**: Uses fuse.js for intelligent filtering as the user types
- **Grouped Display**: Separates branches and tags into distinct sections
- **Keyboard Navigation**: 
  - `↑` / `↓` - Navigate through options
  - `Enter` - Select highlighted option
  - `Escape` - Close dropdown
- **Visual Indicators**: Shows checkmark next to currently selected ref
- **Accessibility**: Proper ARIA labels and keyboard support

## Usage

```tsx
import BranchSelector from '../components/BranchSelector'

function MyComponent() {
  const [currentBranch, setCurrentBranch] = useState('main')

  return (
    <BranchSelector
      repoOwner="alice"
      repoName="myrepo"
      currentRef={currentBranch}
      onChange={(ref) => setCurrentBranch(ref)}
    />
  )
}
```

## Props

| Prop | Type | Description |
|------|------|-------------|
| `repoOwner` | `string` | Repository owner username |
| `repoName` | `string` | Repository name |
| `currentRef` | `string` | Currently selected branch or tag name |
| `onChange` | `(ref: string) => void` | Callback invoked when a new ref is selected |

## API Endpoints

The component expects the following API endpoints:

- `GET /api/repos/{owner}/{repo}/branches` - Returns array of branch objects
- `GET /api/repos/{owner}/{repo}/tags` - Returns array of tag objects (optional)

### Expected Response Format

**Branches:**
```json
[
  {
    "name": "main",
    "headSha": "abc123...",
    "protected": false
  }
]
```

**Tags:**
```json
[
  {
    "name": "v1.0.0",
    "commitSha": "def456..."
  }
]
```

## Styling

The component uses TailwindCSS classes and follows the dark theme pattern used throughout the application:
- Gray-800 background for dropdown
- Gray-700 for hover states
- Indigo-600 for selected items
- Proper focus rings for accessibility

## Implementation Notes

1. The component fetches data only when the dropdown is opened to minimize unnecessary API calls
2. If the tags endpoint fails (e.g., not implemented yet), the component gracefully handles the error and shows only branches
3. The fuzzy search threshold is set to 0.3 for balanced matching
4. The dropdown automatically closes when clicking outside or pressing Escape
5. The search input is automatically focused when the dropdown opens

## Testing

A demo page is available at `src/pages/BranchSelectorDemo.tsx` for manual testing. To use it:

1. Add a route in `App.tsx`:
   ```tsx
   <Route path="/demo/branch-selector" element={<BranchSelectorDemo />} />
   ```

2. Navigate to `/demo/branch-selector` in your browser

3. Update the `repoOwner` and `repoName` in the demo page to point to an actual repository

## Future Enhancements

Potential improvements for future iterations:

- Add loading skeleton while fetching
- Cache fetched branches/tags to avoid refetching
- Support for creating new branches from the selector
- Show additional metadata (last commit date, commit count)
- Support for filtering by branch type (feature/, bugfix/, etc.)
