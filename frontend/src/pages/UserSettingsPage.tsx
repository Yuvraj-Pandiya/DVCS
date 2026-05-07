/**
 * UserSettingsPage — protected settings page at route /settings
 *
 * Three tabs:
 *  1. Profile   — edit avatarUrl + bio via PATCH /api/users/me
 *  2. SSH Keys  — list / add / delete via /api/users/me/ssh-keys
 *  3. Tokens    — list / create / revoke via /api/users/me/tokens
 *
 * All mutations use React Query and invalidate the relevant query on success.
 */

import { useState, useRef } from 'react'
import { Link } from 'react-router-dom'
import {
  useQuery,
  useMutation,
  useQueryClient,
} from '@tanstack/react-query'
import { useAuth } from '../context/AuthContext'
import { useApiClient, ApiError } from '../api/client'

// ─── Types ────────────────────────────────────────────────────────────────────

interface SshKey {
  id: number
  title: string
  fingerprint: string
  createdAt: string
}

interface PersonalToken {
  id: number
  name: string
  scopes: string[]
  expiresAt: string | null
  createdAt: string
}

interface CreatedTokenResponse {
  id: number
  name: string
  scopes: string[]
  expiresAt: string | null
  createdAt: string
  /** Raw token value — only returned once at creation time */
  token: string
}

type TabId = 'profile' | 'ssh-keys' | 'tokens'

const SCOPES = [
  { value: 'repo:read', label: 'repo:read' },
  { value: 'repo:write', label: 'repo:write' },
  { value: 'issues:read', label: 'issues:read' },
  { value: 'issues:write', label: 'issues:write' },
]

// ─── Helpers ──────────────────────────────────────────────────────────────────

function formatDate(iso: string | null | undefined): string {
  if (!iso) return 'Never'
  try {
    return new Date(iso).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    })
  } catch {
    return iso
  }
}

// ─── Tab: Profile ─────────────────────────────────────────────────────────────

function ProfileTab() {
  const { user } = useAuth()
  const api = useApiClient()
  const queryClient = useQueryClient()

  const [avatarUrl, setAvatarUrl] = useState(user?.avatarUrl ?? '')
  const [bio, setBio] = useState('')
  const [successMsg, setSuccessMsg] = useState<string | null>(null)
  const [errorMsg, setErrorMsg] = useState<string | null>(null)

  const mutation = useMutation<unknown, ApiError, { avatarUrl: string; bio: string }>({
    mutationFn: (data) => api.patch('/api/users/me', data),
    onSuccess: () => {
      setSuccessMsg('Profile updated successfully.')
      setErrorMsg(null)
      queryClient.invalidateQueries({ queryKey: ['me'] })
    },
    onError: (err) => {
      setErrorMsg(err.message ?? 'Failed to update profile.')
      setSuccessMsg(null)
    },
  })

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setSuccessMsg(null)
    setErrorMsg(null)
    mutation.mutate({ avatarUrl, bio })
  }

  return (
    <section aria-labelledby="profile-heading">
      <h2
        id="profile-heading"
        className="text-lg font-semibold text-gray-100 mb-6"
      >
        Public Profile
      </h2>

      <form onSubmit={handleSubmit} className="space-y-5 max-w-lg">
        {/* Avatar URL */}
        <div>
          <label
            htmlFor="avatarUrl"
            className="block text-sm font-medium text-gray-300 mb-1"
          >
            Avatar URL
          </label>
          <input
            id="avatarUrl"
            type="url"
            value={avatarUrl}
            onChange={(e) => setAvatarUrl(e.target.value)}
            placeholder="https://example.com/avatar.png"
            className="w-full rounded-md bg-gray-800 border border-gray-600 text-gray-100 placeholder-gray-500 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
          />
          {/* Preview */}
          {avatarUrl && (
            <div className="mt-2 flex items-center gap-3">
              <img
                src={avatarUrl}
                alt="Avatar preview"
                className="w-12 h-12 rounded-full object-cover border border-gray-600"
                onError={(e) => {
                  ;(e.target as HTMLImageElement).style.display = 'none'
                }}
              />
              <span className="text-xs text-gray-500">Preview</span>
            </div>
          )}
        </div>

        {/* Bio */}
        <div>
          <label
            htmlFor="bio"
            className="block text-sm font-medium text-gray-300 mb-1"
          >
            Bio
          </label>
          <textarea
            id="bio"
            value={bio}
            onChange={(e) => setBio(e.target.value)}
            rows={4}
            maxLength={500}
            placeholder="Tell us a little about yourself…"
            className="w-full rounded-md bg-gray-800 border border-gray-600 text-gray-100 placeholder-gray-500 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent resize-none"
          />
          <p className="text-xs text-gray-500 mt-1 text-right">
            {bio.length}/500
          </p>
        </div>

        {/* Feedback */}
        {successMsg && (
          <p role="status" className="text-sm text-green-400">
            {successMsg}
          </p>
        )}
        {errorMsg && (
          <p role="alert" className="text-sm text-red-400">
            {errorMsg}
          </p>
        )}

        <button
          type="submit"
          disabled={mutation.isPending}
          className="inline-flex items-center gap-2 rounded-md bg-indigo-600 px-4 py-2 text-sm font-semibold text-white hover:bg-indigo-500 disabled:opacity-50 disabled:cursor-not-allowed transition-colors focus:outline-none focus:ring-2 focus:ring-indigo-400 focus:ring-offset-2 focus:ring-offset-gray-950"
        >
          {mutation.isPending ? 'Saving…' : 'Save profile'}
        </button>
      </form>
    </section>
  )
}

// ─── Tab: SSH Keys ────────────────────────────────────────────────────────────

function SshKeysTab() {
  const api = useApiClient()
  const queryClient = useQueryClient()

  const [title, setTitle] = useState('')
  const [publicKey, setPublicKey] = useState('')
  const [addError, setAddError] = useState<string | null>(null)
  const [addSuccess, setAddSuccess] = useState<string | null>(null)

  const { data: keys = [], isLoading, error } = useQuery<SshKey[], ApiError>({
    queryKey: ['ssh-keys'],
    queryFn: () => api.get<SshKey[]>('/api/users/me/ssh-keys'),
  })

  const addMutation = useMutation<SshKey, ApiError, { title: string; publicKey: string }>({
    mutationFn: (data) => api.post<SshKey>('/api/users/me/ssh-keys', data),
    onSuccess: () => {
      setTitle('')
      setPublicKey('')
      setAddError(null)
      setAddSuccess('SSH key added successfully.')
      queryClient.invalidateQueries({ queryKey: ['ssh-keys'] })
    },
    onError: (err) => {
      setAddError(err.message ?? 'Failed to add SSH key.')
      setAddSuccess(null)
    },
  })

  const deleteMutation = useMutation<unknown, ApiError, number>({
    mutationFn: (id) => api.delete(`/api/users/me/ssh-keys/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['ssh-keys'] })
    },
  })

  function handleAdd(e: React.FormEvent) {
    e.preventDefault()
    setAddError(null)
    setAddSuccess(null)
    addMutation.mutate({ title, publicKey })
  }

  return (
    <section aria-labelledby="ssh-heading">
      <h2
        id="ssh-heading"
        className="text-lg font-semibold text-gray-100 mb-6"
      >
        SSH Keys
      </h2>

      {/* Key list */}
      <div className="mb-8">
        {isLoading ? (
          <div className="space-y-3 animate-pulse">
            {[1, 2].map((i) => (
              <div key={i} className="h-16 bg-gray-800 rounded-lg border border-gray-700" />
            ))}
          </div>
        ) : error ? (
          <p className="text-sm text-red-400">Failed to load SSH keys.</p>
        ) : keys.length === 0 ? (
          <div className="bg-gray-800 border border-gray-700 rounded-lg p-6 text-center">
            <p className="text-sm text-gray-500">No SSH keys added yet.</p>
          </div>
        ) : (
          <ul className="space-y-3" aria-label="SSH keys">
            {keys.map((key) => (
              <li
                key={key.id}
                className="flex items-start justify-between gap-4 bg-gray-800 border border-gray-700 rounded-lg px-4 py-3"
              >
                <div className="min-w-0">
                  <p className="text-sm font-medium text-gray-200 truncate">
                    {key.title}
                  </p>
                  <p className="text-xs text-gray-500 font-mono mt-0.5 truncate">
                    {key.fingerprint}
                  </p>
                  <p className="text-xs text-gray-600 mt-0.5">
                    Added {formatDate(key.createdAt)}
                  </p>
                </div>
                <button
                  onClick={() => deleteMutation.mutate(key.id)}
                  disabled={deleteMutation.isPending}
                  aria-label={`Delete SSH key ${key.title}`}
                  className="flex-shrink-0 text-xs text-red-400 hover:text-red-300 disabled:opacity-50 transition-colors focus:outline-none focus:ring-2 focus:ring-red-400 rounded"
                >
                  Delete
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>

      {/* Add key form */}
      <div className="border-t border-gray-700 pt-6">
        <h3 className="text-base font-medium text-gray-200 mb-4">Add new SSH key</h3>
        <form onSubmit={handleAdd} className="space-y-4 max-w-lg">
          <div>
            <label
              htmlFor="ssh-title"
              className="block text-sm font-medium text-gray-300 mb-1"
            >
              Title
            </label>
            <input
              id="ssh-title"
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              required
              placeholder="e.g. My laptop"
              className="w-full rounded-md bg-gray-800 border border-gray-600 text-gray-100 placeholder-gray-500 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
            />
          </div>
          <div>
            <label
              htmlFor="ssh-key"
              className="block text-sm font-medium text-gray-300 mb-1"
            >
              Public key
            </label>
            <textarea
              id="ssh-key"
              value={publicKey}
              onChange={(e) => setPublicKey(e.target.value)}
              required
              rows={4}
              placeholder="ssh-ed25519 AAAA…"
              className="w-full rounded-md bg-gray-800 border border-gray-600 text-gray-100 placeholder-gray-500 px-3 py-2 text-sm font-mono focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent resize-none"
            />
          </div>

          {addSuccess && (
            <p role="status" className="text-sm text-green-400">
              {addSuccess}
            </p>
          )}
          {addError && (
            <p role="alert" className="text-sm text-red-400">
              {addError}
            </p>
          )}

          <button
            type="submit"
            disabled={addMutation.isPending}
            className="inline-flex items-center gap-2 rounded-md bg-indigo-600 px-4 py-2 text-sm font-semibold text-white hover:bg-indigo-500 disabled:opacity-50 disabled:cursor-not-allowed transition-colors focus:outline-none focus:ring-2 focus:ring-indigo-400 focus:ring-offset-2 focus:ring-offset-gray-950"
          >
            {addMutation.isPending ? 'Adding…' : 'Add SSH key'}
          </button>
        </form>
      </div>
    </section>
  )
}

// ─── Tab: Personal Access Tokens ──────────────────────────────────────────────

function TokensTab() {
  const api = useApiClient()
  const queryClient = useQueryClient()

  const [name, setName] = useState('')
  const [selectedScopes, setSelectedScopes] = useState<string[]>([])
  const [expiresAt, setExpiresAt] = useState('')
  const [createError, setCreateError] = useState<string | null>(null)
  const [newToken, setNewToken] = useState<string | null>(null)
  const copyRef = useRef<HTMLInputElement>(null)

  const { data: tokens = [], isLoading, error } = useQuery<PersonalToken[], ApiError>({
    queryKey: ['tokens'],
    queryFn: () => api.get<PersonalToken[]>('/api/users/me/tokens'),
  })

  const createMutation = useMutation<
    CreatedTokenResponse,
    ApiError,
    { name: string; scopes: string[]; expiresAt: string | null }
  >({
    mutationFn: (data) => api.post<CreatedTokenResponse>('/api/users/me/tokens', data),
    onSuccess: (data) => {
      setNewToken(data.token)
      setName('')
      setSelectedScopes([])
      setExpiresAt('')
      setCreateError(null)
      queryClient.invalidateQueries({ queryKey: ['tokens'] })
    },
    onError: (err) => {
      setCreateError(err.message ?? 'Failed to create token.')
    },
  })

  const revokeMutation = useMutation<unknown, ApiError, number>({
    mutationFn: (id) => api.delete(`/api/users/me/tokens/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tokens'] })
    },
  })

  function toggleScope(scope: string) {
    setSelectedScopes((prev) =>
      prev.includes(scope) ? prev.filter((s) => s !== scope) : [...prev, scope]
    )
  }

  function handleCreate(e: React.FormEvent) {
    e.preventDefault()
    setCreateError(null)
    setNewToken(null)
    createMutation.mutate({
      name,
      scopes: selectedScopes,
      expiresAt: expiresAt || null,
    })
  }

  function handleCopy() {
    if (newToken) {
      navigator.clipboard.writeText(newToken).catch(() => {
        // Fallback: select the input text
        copyRef.current?.select()
        document.execCommand('copy')
      })
    }
  }

  return (
    <section aria-labelledby="tokens-heading">
      <h2
        id="tokens-heading"
        className="text-lg font-semibold text-gray-100 mb-6"
      >
        Personal Access Tokens
      </h2>

      {/* New token banner — shown once after creation */}
      {newToken && (
        <div
          role="alert"
          className="mb-6 rounded-lg border border-yellow-600 bg-yellow-950 p-4"
        >
          <p className="text-sm font-semibold text-yellow-300 mb-2">
            Make sure to copy your token now — you won't be able to see it again.
          </p>
          <div className="flex items-center gap-2">
            <input
              ref={copyRef}
              type="text"
              readOnly
              value={newToken}
              aria-label="New personal access token"
              className="flex-1 rounded-md bg-gray-900 border border-yellow-700 text-yellow-200 font-mono text-xs px-3 py-2 focus:outline-none"
            />
            <button
              onClick={handleCopy}
              className="flex-shrink-0 rounded-md bg-yellow-700 hover:bg-yellow-600 text-white text-xs font-semibold px-3 py-2 transition-colors focus:outline-none focus:ring-2 focus:ring-yellow-400"
            >
              Copy
            </button>
          </div>
          <button
            onClick={() => setNewToken(null)}
            className="mt-2 text-xs text-yellow-500 hover:text-yellow-400 underline"
          >
            Dismiss
          </button>
        </div>
      )}

      {/* Token list */}
      <div className="mb-8">
        {isLoading ? (
          <div className="space-y-3 animate-pulse">
            {[1, 2].map((i) => (
              <div key={i} className="h-16 bg-gray-800 rounded-lg border border-gray-700" />
            ))}
          </div>
        ) : error ? (
          <p className="text-sm text-red-400">Failed to load tokens.</p>
        ) : tokens.length === 0 ? (
          <div className="bg-gray-800 border border-gray-700 rounded-lg p-6 text-center">
            <p className="text-sm text-gray-500">No personal access tokens yet.</p>
          </div>
        ) : (
          <ul className="space-y-3" aria-label="Personal access tokens">
            {tokens.map((token) => (
              <li
                key={token.id}
                className="flex items-start justify-between gap-4 bg-gray-800 border border-gray-700 rounded-lg px-4 py-3"
              >
                <div className="min-w-0">
                  <p className="text-sm font-medium text-gray-200 truncate">
                    {token.name}
                  </p>
                  <div className="flex flex-wrap gap-1 mt-1">
                    {token.scopes.map((scope) => (
                      <span
                        key={scope}
                        className="text-xs bg-gray-700 text-gray-300 rounded px-1.5 py-0.5 font-mono"
                      >
                        {scope}
                      </span>
                    ))}
                  </div>
                  <p className="text-xs text-gray-600 mt-1">
                    Expires: {formatDate(token.expiresAt)}
                  </p>
                </div>
                <button
                  onClick={() => revokeMutation.mutate(token.id)}
                  disabled={revokeMutation.isPending}
                  aria-label={`Revoke token ${token.name}`}
                  className="flex-shrink-0 text-xs text-red-400 hover:text-red-300 disabled:opacity-50 transition-colors focus:outline-none focus:ring-2 focus:ring-red-400 rounded"
                >
                  Revoke
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>

      {/* Create token form */}
      <div className="border-t border-gray-700 pt-6">
        <h3 className="text-base font-medium text-gray-200 mb-4">
          Create new token
        </h3>
        <form onSubmit={handleCreate} className="space-y-4 max-w-lg">
          {/* Name */}
          <div>
            <label
              htmlFor="token-name"
              className="block text-sm font-medium text-gray-300 mb-1"
            >
              Token name
            </label>
            <input
              id="token-name"
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
              placeholder="e.g. CI deploy token"
              className="w-full rounded-md bg-gray-800 border border-gray-600 text-gray-100 placeholder-gray-500 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
            />
          </div>

          {/* Scopes */}
          <fieldset>
            <legend className="block text-sm font-medium text-gray-300 mb-2">
              Scopes
            </legend>
            <div className="grid grid-cols-2 gap-2">
              {SCOPES.map(({ value, label }) => (
                <label
                  key={value}
                  className="flex items-center gap-2 cursor-pointer select-none"
                >
                  <input
                    type="checkbox"
                    checked={selectedScopes.includes(value)}
                    onChange={() => toggleScope(value)}
                    className="rounded border-gray-600 bg-gray-800 text-indigo-500 focus:ring-indigo-500 focus:ring-offset-gray-950"
                  />
                  <span className="text-sm text-gray-300 font-mono">{label}</span>
                </label>
              ))}
            </div>
          </fieldset>

          {/* Expiry */}
          <div>
            <label
              htmlFor="token-expires"
              className="block text-sm font-medium text-gray-300 mb-1"
            >
              Expiration date{' '}
              <span className="text-gray-500 font-normal">(optional)</span>
            </label>
            <input
              id="token-expires"
              type="date"
              value={expiresAt}
              onChange={(e) => setExpiresAt(e.target.value)}
              min={new Date().toISOString().split('T')[0]}
              className="rounded-md bg-gray-800 border border-gray-600 text-gray-100 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
            />
          </div>

          {createError && (
            <p role="alert" className="text-sm text-red-400">
              {createError}
            </p>
          )}

          <button
            type="submit"
            disabled={createMutation.isPending || selectedScopes.length === 0}
            className="inline-flex items-center gap-2 rounded-md bg-indigo-600 px-4 py-2 text-sm font-semibold text-white hover:bg-indigo-500 disabled:opacity-50 disabled:cursor-not-allowed transition-colors focus:outline-none focus:ring-2 focus:ring-indigo-400 focus:ring-offset-2 focus:ring-offset-gray-950"
          >
            {createMutation.isPending ? 'Creating…' : 'Create token'}
          </button>
        </form>
      </div>
    </section>
  )
}

// ─── UserSettingsPage ─────────────────────────────────────────────────────────

const TABS: { id: TabId; label: string }[] = [
  { id: 'profile', label: 'Profile' },
  { id: 'ssh-keys', label: 'SSH Keys' },
  { id: 'tokens', label: 'Personal Access Tokens' },
]

export default function UserSettingsPage() {
  const { user } = useAuth()
  const [activeTab, setActiveTab] = useState<TabId>('profile')

  return (
    <div className="min-h-screen bg-gray-950 text-gray-100">
      {/* Top nav */}
      <header className="border-b border-gray-800 bg-gray-900">
        <div className="max-w-5xl mx-auto px-4 py-3 flex items-center gap-3">
          <Link
            to="/"
            className="flex items-center gap-2 text-white font-bold text-lg hover:text-indigo-400 transition-colors"
            aria-label="DVCS home"
          >
            <svg
              xmlns="http://www.w3.org/2000/svg"
              viewBox="0 0 24 24"
              fill="currentColor"
              className="w-6 h-6 text-indigo-400"
              aria-hidden="true"
            >
              <path d="M12 2C6.477 2 2 6.477 2 12s4.477 10 10 10 10-4.477 10-10S17.523 2 12 2zm-1 14.5v-5l-3 3-1.5-1.5 4.5-4.5 4.5 4.5-1.5 1.5-3-3v5h-1z" />
            </svg>
            <span>DVCS</span>
          </Link>
          {user && (
            <Link
              to={`/${user.username}`}
              className="ml-auto text-sm text-gray-400 hover:text-gray-200 transition-colors"
            >
              {user.username}
            </Link>
          )}
        </div>
      </header>

      <main className="max-w-5xl mx-auto px-4 py-8">
        <h1 className="text-2xl font-bold text-gray-100 mb-6">Settings</h1>

        <div className="flex flex-col md:flex-row gap-8">
          {/* ── Sidebar tab nav ─────────────────────────────────────────── */}
          <nav
            aria-label="Settings sections"
            className="md:w-48 flex-shrink-0"
          >
            <ul className="space-y-1">
              {TABS.map(({ id, label }) => (
                <li key={id}>
                  <button
                    onClick={() => setActiveTab(id)}
                    aria-current={activeTab === id ? 'page' : undefined}
                    className={[
                      'w-full text-left rounded-md px-3 py-2 text-sm font-medium transition-colors focus:outline-none focus:ring-2 focus:ring-indigo-500',
                      activeTab === id
                        ? 'bg-indigo-600 text-white'
                        : 'text-gray-400 hover:bg-gray-800 hover:text-gray-200',
                    ].join(' ')}
                  >
                    {label}
                  </button>
                </li>
              ))}
            </ul>
          </nav>

          {/* ── Tab content ─────────────────────────────────────────────── */}
          <div className="flex-1 min-w-0 bg-gray-900 border border-gray-700 rounded-lg p-6">
            {activeTab === 'profile' && <ProfileTab />}
            {activeTab === 'ssh-keys' && <SshKeysTab />}
            {activeTab === 'tokens' && <TokensTab />}
          </div>
        </div>
      </main>
    </div>
  )
}
