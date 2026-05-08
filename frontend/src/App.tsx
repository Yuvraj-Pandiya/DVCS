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
import IssueListPage from './pages/IssueListPage'
import IssueDetailPage from './pages/IssueDetailPage'
import RepoSettingsPage from './pages/RepoSettingsPage'
import PipelineListPage from './pages/PipelineListPage'
import CreateRepoPage from './pages/CreateRepoPage'
import CreateIssuePage from './pages/CreateIssuePage'
import CreatePullRequestPage from './pages/CreatePullRequestPage'
import ProtectedRoute from './components/ProtectedRoute'
import AppShell from './components/layout/AppShell'

function App() {
  return (
    <Routes>
      {/* Public standalone pages */}
      <Route path="/" element={<LandingPage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />

      {/* Main app shell for functional pages */}
      <Route element={<AppShell />}>
        <Route path="/explore" element={<ExplorePage />} />
        <Route
          path="/new"
          element={
            <ProtectedRoute>
              <CreateRepoPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/settings"
          element={
            <ProtectedRoute>
              <UserSettingsPage />
            </ProtectedRoute>
          }
        />

        {/* Repository routes */}
        <Route path="/:owner/:repo" element={<RepoHomePage />} />
        <Route path="/:owner/:repo/tree/:ref/*" element={<FileTreePage />} />
        <Route path="/:owner/:repo/blob/:ref/*" element={<FileBlobPage />} />
        <Route path="/:owner/:repo/commits/:ref" element={<CommitListPage />} />
        <Route path="/:owner/:repo/commit/:sha" element={<CommitDetailPage />} />
        <Route path="/:owner/:repo/branches" element={<BranchListPage />} />
        <Route path="/:owner/:repo/pulls" element={<PullRequestListPage />} />
        <Route
          path="/:owner/:repo/pulls/new"
          element={
            <ProtectedRoute>
              <CreatePullRequestPage />
            </ProtectedRoute>
          }
        />
        <Route path="/:owner/:repo/pulls/:id" element={<PullRequestDetailPage />} />
        <Route path="/:owner/:repo/issues" element={<IssueListPage />} />
        <Route
          path="/:owner/:repo/issues/new"
          element={
            <ProtectedRoute>
              <CreateIssuePage />
            </ProtectedRoute>
          }
        />
        <Route path="/:owner/:repo/issues/:id" element={<IssueDetailPage />} />
        <Route
          path="/:owner/:repo/settings"
          element={
            <ProtectedRoute>
              <RepoSettingsPage />
            </ProtectedRoute>
          }
        />
        <Route path="/:owner/:repo/pipelines" element={<PipelineListPage />} />

        {/* User profile */}
        <Route path="/:owner" element={<UserProfilePage />} />
      </Route>
    </Routes>
  )
}

export default App
