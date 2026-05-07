import { Routes, Route } from 'react-router-dom'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import UserProfilePage from './pages/UserProfilePage'
import UserSettingsPage from './pages/UserSettingsPage'
import LandingPage from './pages/LandingPage'
import ExplorePage from './pages/ExplorePage'
import RepoHomePage from './pages/RepoHomePage'
import FileTreePage from './pages/FileTreePage'
import FileBlobPage from './pages/FileBlobPage'
import CommitListPage from './pages/CommitListPage'
import CommitDetailPage from './pages/CommitDetailPage'
import BranchListPage from './pages/BranchListPage'
import PullRequestListPage from './pages/PullRequestListPage'
import PullRequestDetailPage from './pages/PullRequestDetailPage'
import ProtectedRoute from './components/ProtectedRoute'

function App() {
  return (
    <Routes>
      <Route path="/" element={<LandingPage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      {/* Explore — must come before the /:owner catch-all */}
      <Route path="/explore" element={<ExplorePage />} />
      {/* Protected routes */}
      <Route
        path="/settings"
        element={
          <ProtectedRoute>
            <UserSettingsPage />
          </ProtectedRoute>
        }
      />
      {/* Repository home page — must come before /:owner to avoid conflicts */}
      <Route path="/:owner/:repo" element={<RepoHomePage />} />
      {/* File tree page */}
      <Route path="/:owner/:repo/tree/:ref/*" element={<FileTreePage />} />
      {/* File blob page */}
      <Route path="/:owner/:repo/blob/:ref/*" element={<FileBlobPage />} />
      {/* Commit list page */}
      <Route path="/:owner/:repo/commits/:ref" element={<CommitListPage />} />
      {/* Commit detail page */}
      <Route path="/:owner/:repo/commit/:sha" element={<CommitDetailPage />} />
      {/* Branch list page */}
      <Route path="/:owner/:repo/branches" element={<BranchListPage />} />
      {/* Pull request list page */}
      <Route path="/:owner/:repo/pulls" element={<PullRequestListPage />} />
      {/* Pull request detail page */}
      <Route path="/:owner/:repo/pulls/:id" element={<PullRequestDetailPage />} />
      {/* User profile — must come after all fixed-segment routes to avoid conflicts */}
      <Route path="/:owner" element={<UserProfilePage />} />
    </Routes>
  )
}

export default App
