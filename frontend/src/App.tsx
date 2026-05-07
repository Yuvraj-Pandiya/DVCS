import { Routes, Route } from 'react-router-dom'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import UserProfilePage from './pages/UserProfilePage'
import UserSettingsPage from './pages/UserSettingsPage'
import LandingPage from './pages/LandingPage'
import ExplorePage from './pages/ExplorePage'
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
      {/* User profile — must come after all fixed-segment routes to avoid conflicts */}
      <Route path="/:owner" element={<UserProfilePage />} />
    </Routes>
  )
}

export default App
