/**
 * RepoSettingsPage — OWNER-only settings page at route /:owner/:repo/settings
 *
 * Four tabs:
 *  1. General     — rename repo, change visibility, update description
 *  2. Collaborators — list + add by username + change role + remove
 *  3. Webhooks    — list + create + edit + delete + test
 *  4. Danger Zone — delete repository with confirmation dialog
 */

import { useState, useEffect } from 'react'
import { Link, useParams, useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../context/AuthContext'
import { useApiClient, ApiError } from '../api/client'

// ─── Types ────────────────────────────────────────────────────────────────────

interface RepoInfo {
  id: number
  name: string
  description: string | null
  isPrivate: boolean
  defaultBranch: string
  owner: {
    id: number
    username: string
  }
}

interface Collaborator {
  userId: number
  username: string
  avatarUrl: string | null
  role: 'OWNER' | 'WRITE' | 'READ'
}

interface Webhook {
  id: number
  url: string
  secret: string | null
  events: string[]
  active: boolean
  createdAt: string
}

type TabId = 'general' | 'collaborators' | 'webhooks' | 'danger'

const WEBHOOK_EVENTS = [
  { value: 'push', label: 'Push' },
  { value: 'pull_request', label: 'Pull Request' },
  { value: 'issues', label: 'Issues' },
  { value: 'issue_comment', label: 'Issue Comment' },
]

const ROLES: { value: 'OWNER' | 'WRITE' | 'READ'; label: string }[] = [
  { value: 'OWNER', label: 'Owner' },
  { value: 'WRITE', label: 'Write' },
  { value: 'READ', label: 'Read' },
]

// ─── Helpers ──────────────────────────────────────────────────────────────────

function roleBadgeClass(role: string): string {
  switch (role) {
    case 'OWNER': return 'bg-indigo-900 text-indigo-300'
    case 'WRITE': return 'bg-green-900 text-green-300'
    default: return 'bg-gray-700 text-gray-300'
  }
}

// ─── Tab: General ─────────────────────────────────────────────────────────────

interface GeneralTabProps {
  owner: string
  repo: string
  repoInfo: RepoInfo
}

function GeneralTab({ owner, repo, repoInfo }: GeneralTabProps) {
  const api = useApiClient()
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const [name, setName] = useState(repoInfo.name)
  const [description, setDescription] = useState(repoInfo.description ?? '')
  const [isPrivate, setIsPrivate] = useState(repoInfo.isPrivate)
  const [successMsg, setSuccessMsg] = useState<string | null>(null)
  const [errorMsg, setErrorMsg] = useState<string | null>(null)

  useEffect(() => {
    setName(repoInfo.name)
    setDescription(repoInfo.description ?? '')
    setIsPrivate(repoInfo.isPrivate)
  }, [repoInfo])

  const mutation = useMutation<RepoInfo, ApiError, { name?: string; description?: string; isPrivate?: boolean }>({
    mutationFn: (data) => api.patch<RepoInfo>(`/api/repos/${owner}/${repo}`, data),
    onSuccess: (updated) => {
      setSuccessMsg('Repository settings saved.')
      setErrorMsg(null)
      queryClient.invalidateQueries({ queryKey: ['repo', owner, repo] })
      if (updated.name !== repo) {
        navigate(`/${owner}/${updated.name}/settings`)
      }
    },
    onError: (err) => {
      setErrorMsg(err.message ?? 'Failed to save settings.')
      setSuccessMsg(null)
    },
  })

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setSuccessMsg(null)
    setErrorMsg(null)
    mutation.mutate({ name, description, isPrivate })
  }

  return (
    <section aria-labelledby="general-heading">
      <h2 id="general-heading" className="text-lg font-semibold text-gray-100 mb-6">General Settings</h2>
      <form onSubmit={handleSubmit} className="space-y-5 max-w-lg">
        <div>
          <label htmlFor="repo-name" className="block text-sm font-medium text-gray-300 mb-1">Repository name</label>
          <input
            id="repo-name"
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            required
            pattern="[a-zA-Z0-9_.\-]+"
            title="Only letters, numbers, hyphens, underscores, and dots"
            className="w-full rounded-md bg-gray-800 border border-gray-600 text-gray-100 placeholder-gray-500 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
          />
        </div>
        <div>
          <label htmlFor="repo-description" className="block text-sm font-medium text-gray-300 mb-1">
            Description <span className="text-gray-500 font-normal">(optional)</span>
          </label>
          <textarea
            id="repo-description"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            rows={3}
            maxLength={500}
            placeholder="A short description of this repository…"
            className="w-full rounded-md bg-gray-800 border border-gray-600 text-gray-100 placeholder-gray-500 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent resize-none"
          />
        </div>
        <div>
          <p className="block text-sm font-medium text-gray-300 mb-2">Visibility</p>
          <div className="flex flex-col gap-2">
            <label className="flex items-start gap-3 cursor-pointer">
              <input
                type="radio"
                name="visibility"
                checked={!isPrivate}
                onChange={() => setIsPrivate(false)}
                className="mt-0.5 text-indigo-500 border-gray-600 bg-gray-800 focus:ring-indigo-500"
              />
              <div>
                <span className="text-sm font-medium text-gray-200">Public</span>
                <p className="text-xs text-gray-500">Anyone can see this repository.</p>
              </div>
            </label>
            <label className="flex items-start gap-3 cursor-pointer">
              <input
                type="radio"
                name="visibility"
                checked={isPrivate}
                onChange={() => setIsPrivate(true)}
                className="mt-0.5 text-indigo-500 border-gray-600 bg-gray-800 focus:ring-indigo-500"
              />
              <div>
                <span className="text-sm font-medium text-gray-200">Private</span>
                <p className="text-xs text-gray-500">Only you and collaborators can see this repository.</p>
              </div>
            </label>
          </div>
        </div>
        {successMsg && <p role="status" className="text-sm text-green-400">{successMsg}</p>}
        {errorMsg && <p role="alert" className="text-sm text-red-400">{errorMsg}</p>}
        <button
          type="submit"
          disabled={mutation.isPending}
          className="inline-flex items-center gap-2 rounded-md bg-indigo-600 px-4 py-2 text-sm font-semibold text-white hover:bg-indigo-500 disabled:opacity-50 disabled:cursor-not-allowed transition-colors focus:outline-none focus:ring-2 focus:ring-indigo-400"
        >
          {mutation.isPending ? 'Saving…' : 'Save changes'}
        </button>
      </form>
    </section>
  )
}

// ─── Tab: Collaborators ───────────────────────────────────────────────────────

interface CollaboratorsTabProps {
  owner: string
  repo: string
}

function CollaboratorsTab({ owner, repo }: CollaboratorsTabProps) {
  const api = useApiClient()
  const queryClient = useQueryClient()

  const [addUsername, setAddUsername] = useState('')
  const [addRole, setAddRole] = useState<'OWNER' | 'WRITE' | 'READ'>('READ')
  const [addError, setAddError] = useState<string | null>(null)
  const [addSuccess, setAddSuccess] = useState<string | null>(null)

  const { data: collaborators = [], isLoading, error } = useQuery<Collaborator[], ApiError>({
    queryKey: ['collaborators', owner, repo],
    queryFn: () => api.get<Collaborator[]>(`/api/repos/${owner}/${repo}/collaborators`),
  })

  const addMutation = useMutation<Collaborator, ApiError, { username: string; role: string }>({
    mutationFn: (data) => api.post<Collaborator>(`/api/repos/${owner}/${repo}/collaborators`, data),
    onSuccess: () => {
      setAddUsername('')
      setAddRole('READ')
      setAddError(null)
      setAddSuccess('Collaborator added successfully.')
      queryClient.invalidateQueries({ queryKey: ['collaborators', owner, repo] })
    },
    onError: (err) => {
      setAddError(err.message ?? 'Failed to add collaborator.')
      setAddSuccess(null)
    },
  })

  const changeRoleMutation = useMutation<unknown, ApiError, { userId: number; role: string }>({
    mutationFn: ({ userId, role }) =>
      api.patch(`/api/repos/${owner}/${repo}/collaborators/${userId}`, { role }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['collaborators', owner, repo] })
    },
  })

  const removeMutation = useMutation<unknown, ApiError, number>({
    mutationFn: (userId) => api.delete(`/api/repos/${owner}/${repo}/collaborators/${userId}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['collaborators', owner, repo] })
    },
  })

  function handleAdd(e: React.FormEvent) {
    e.preventDefault()
    setAddError(null)
    setAddSuccess(null)
    addMutation.mutate({ username: addUsername, role: addRole })
  }

  return (
    <section aria-labelledby="collaborators-heading">
      <h2 id="collaborators-heading" className="text-lg font-semibold text-gray-100 mb-6">Collaborators</h2>

      <div className="mb-8">
        {isLoading ? (
          <div className="space-y-3 animate-pulse">
            {[1, 2, 3].map((i) => (
              <div key={i} className="h-14 bg-gray-800 rounded-lg border border-gray-700" />
            ))}
          </div>
        ) : error ? (
          <p className="text-sm text-red-400">Failed to load collaborators.</p>
        ) : collaborators.length === 0 ? (
          <div className="bg-gray-800 border border-gray-700 rounded-lg p-6 text-center">
            <p className="text-sm text-gray-500">No collaborators yet.</p>
          </div>
        ) : (
          <ul className="space-y-2" aria-label="Collaborators">
            {collaborators.map((collab) => (
              <li
                key={collab.userId}
                className="flex items-center justify-between gap-4 bg-gray-800 border border-gray-700 rounded-lg px-4 py-3"
              >
                <div className="flex items-center gap-3 min-w-0">
                  {collab.avatarUrl ? (
                    <img
                      src={collab.avatarUrl}
                      alt=""
                      className="w-8 h-8 rounded-full object-cover border border-gray-600 flex-shrink-0"
                    />
                  ) : (
                    <div className="w-8 h-8 rounded-full bg-gray-700 flex items-center justify-center flex-shrink-0">
                      <span className="text-xs text-gray-400 font-medium">
                        {collab.username.charAt(0).toUpperCase()}
                      </span>
                    </div>
                  )}
                  <span className="text-sm font-medium text-gray-200 truncate">{collab.username}</span>
                  <span className={`text-xs font-semibold px-2 py-0.5 rounded-full ${roleBadgeClass(collab.role)}`}>
                    {collab.role}
                  </span>
                </div>
                <div className="flex items-center gap-2 flex-shrink-0">
                  <select
                    value={collab.role}
                    onChange={(e) => changeRoleMutation.mutate({ userId: collab.userId, role: e.target.value })}
                    aria-label={`Change role for ${collab.username}`}
                    className="rounded-md bg-gray-700 border border-gray-600 text-gray-200 text-xs px-2 py-1 focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  >
                    {ROLES.map(({ value, label }) => (
                      <option key={value} value={value}>{label}</option>
                    ))}
                  </select>
                  <button
                    onClick={() => removeMutation.mutate(collab.userId)}
                    disabled={removeMutation.isPending}
                    aria-label={`Remove ${collab.username}`}
                    className="text-xs text-red-400 hover:text-red-300 disabled:opacity-50 transition-colors focus:outline-none focus:ring-2 focus:ring-red-400 rounded"
                  >
                    Remove
                  </button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>

      <div className="border-t border-gray-700 pt-6">
        <h3 className="text-base font-medium text-gray-200 mb-4">Add collaborator</h3>
        <form onSubmit={handleAdd} className="flex flex-wrap gap-3 items-end max-w-lg">
          <div className="flex-1 min-w-40">
            <label htmlFor="collab-username" className="block text-sm font-medium text-gray-300 mb-1">Username</label>
            <input
              id="collab-username"
              type="text"
              value={addUsername}
              onChange={(e) => setAddUsername(e.target.value)}
              required
              placeholder="e.g. octocat"
              className="w-full rounded-md bg-gray-800 border border-gray-600 text-gray-100 placeholder-gray-500 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
            />
          </div>
          <div>
            <label htmlFor="collab-role" className="block text-sm font-medium text-gray-300 mb-1">Role</label>
            <select
              id="collab-role"
              value={addRole}
              onChange={(e) => setAddRole(e.target.value as 'OWNER' | 'WRITE' | 'READ')}
              className="rounded-md bg-gray-800 border border-gray-600 text-gray-100 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
            >
              {ROLES.map(({ value, label }) => (
                <option key={value} value={value}>{label}</option>
              ))}
            </select>
          </div>
          <button
            type="submit"
            disabled={addMutation.isPending}
            className="inline-flex items-center gap-2 rounded-md bg-indigo-600 px-4 py-2 text-sm font-semibold text-white hover:bg-indigo-500 disabled:opacity-50 disabled:cursor-not-allowed transition-colors focus:outline-none focus:ring-2 focus:ring-indigo-400"
          >
            {addMutation.isPending ? 'Adding…' : 'Add'}
          </button>
        </form>
        {addSuccess && <p role="status" className="text-sm text-green-400 mt-3">{addSuccess}</p>}
        {addError && <p role="alert" className="text-sm text-red-400 mt-3">{addError}</p>}
      </div>
    </section>
  )
}

// ─── Tab: Webhooks ────────────────────────────────────────────────────────────

interface WebhooksTabProps {
  owner: string
  repo: string
}

interface WebhookFormState {
  url: string
  secret: string
  events: string[]
  active: boolean
}

function WebhooksTab({ owner, repo }: WebhooksTabProps) {
  const api = useApiClient()
  const queryClient = useQueryClient()

  const emptyForm: WebhookFormState = { url: '', secret: '', events: [], active: true }
  const [showAddForm, setShowAddForm] = useState(false)
  const [addForm, setAddForm] = useState<WebhookFormState>(emptyForm)
  const [addError, setAddError] = useState<string | null>(null)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [editForm, setEditForm] = useState<WebhookFormState>(emptyForm)
  const [editError, setEditError] = useState<string | null>(null)
  const [testResults, setTestResults] = useState<Record<number, { ok: boolean; msg: string }>>({})

  const { data: webhooks = [], isLoading, error } = useQuery<Webhook[], ApiError>({
    queryKey: ['webhooks', owner, repo],
    queryFn: () => api.get<Webhook[]>(`/api/repos/${owner}/${repo}/webhooks`),
  })

  const createMutation = useMutation<Webhook, ApiError, WebhookFormState>({
    mutationFn: (data) => api.post<Webhook>(`/api/repos/${owner}/${repo}/webhooks`, data),
    onSuccess: () => {
      setAddForm(emptyForm)
      setShowAddForm(false)
      setAddError(null)
      queryClient.invalidateQueries({ queryKey: ['webhooks', owner, repo] })
    },
    onError: (err) => { setAddError(err.message ?? 'Failed to create webhook.') },
  })

  const updateMutation = useMutation<Webhook, ApiError, { id: number } & WebhookFormState>({
    mutationFn: ({ id, ...data }) => api.patch<Webhook>(`/api/repos/${owner}/${repo}/webhooks/${id}`, data),
    onSuccess: () => {
      setEditingId(null)
      setEditError(null)
      queryClient.invalidateQueries({ queryKey: ['webhooks', owner, repo] })
    },
    onError: (err) => { setEditError(err.message ?? 'Failed to update webhook.') },
  })

  const deleteMutation = useMutation<unknown, ApiError, number>({
    mutationFn: (id) => api.delete(`/api/repos/${owner}/${repo}/webhooks/${id}`),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['webhooks', owner, repo] }) },
  })

  const testMutation = useMutation<unknown, ApiError, number>({
    mutationFn: (id) => api.post(`/api/repos/${owner}/${repo}/webhooks/${id}/test`),
    onSuccess: (_, id) => {
      setTestResults((prev) => ({ ...prev, [id]: { ok: true, msg: 'Test delivery sent successfully.' } }))
    },
    onError: (err, id) => {
      setTestResults((prev) => ({ ...prev, [id]: { ok: false, msg: err.message ?? 'Test delivery failed.' } }))
    },
  })

  function toggleEvent(form: WebhookFormState, event: string): WebhookFormState {
    const events = form.events.includes(event)
      ? form.events.filter((e) => e !== event)
      : [...form.events, event]
    return { ...form, events }
  }

  function WebhookFormFields({ form, onChange }: { form: WebhookFormState; onChange: (f: WebhookFormState) => void }) {
    return (
      <div className="space-y-4">
        <div>
          <label className="block text-sm font-medium text-gray-300 mb-1">Payload URL</label>
          <input
            type="url"
            value={form.url}
            onChange={(e) => onChange({ ...form, url: e.target.value })}
            required
            placeholder="https://example.com/webhook"
            className="w-full rounded-md bg-gray-800 border border-gray-600 text-gray-100 placeholder-gray-500 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-300 mb-1">
            Secret <span className="text-gray-500 font-normal">(optional)</span>
          </label>
          <input
            type="text"
            value={form.secret}
            onChange={(e) => onChange({ ...form, secret: e.target.value })}
            placeholder="Webhook secret token"
            className="w-full rounded-md bg-gray-800 border border-gray-600 text-gray-100 placeholder-gray-500 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
          />
        </div>
        <fieldset>
          <legend className="block text-sm font-medium text-gray-300 mb-2">Events</legend>
          <div className="grid grid-cols-2 gap-2">
            {WEBHOOK_EVENTS.map(({ value, label }) => (
              <label key={value} className="flex items-center gap-2 cursor-pointer select-none">
                <input
                  type="checkbox"
                  checked={form.events.includes(value)}
                  onChange={() => onChange(toggleEvent(form, value))}
                  className="rounded border-gray-600 bg-gray-800 text-indigo-500 focus:ring-indigo-500"
                />
                <span className="text-sm text-gray-300">{label}</span>
              </label>
            ))}
          </div>
        </fieldset>
        <label className="flex items-center gap-2 cursor-pointer select-none">
          <input
            type="checkbox"
            checked={form.active}
            onChange={(e) => onChange({ ...form, active: e.target.checked })}
            className="rounded border-gray-600 bg-gray-800 text-indigo-500 focus:ring-indigo-500"
          />
          <span className="text-sm text-gray-300">Active</span>
        </label>
      </div>
    )
  }

  return (
    <section aria-labelledby="webhooks-heading">
      <div className="flex items-center justify-between mb-6">
        <h2 id="webhooks-heading" className="text-lg font-semibold text-gray-100">Webhooks</h2>
        {!showAddForm && (
          <button
            onClick={() => setShowAddForm(true)}
            className="inline-flex items-center gap-1.5 rounded-md bg-indigo-600 px-3 py-1.5 text-sm font-semibold text-white hover:bg-indigo-500 transition-colors focus:outline-none focus:ring-2 focus:ring-indigo-400"
          >
            Add webhook
          </button>
        )}
      </div>

      {showAddForm && (
        <div className="mb-6 bg-gray-800 border border-gray-700 rounded-lg p-4">
          <h3 className="text-base font-medium text-gray-200 mb-4">New webhook</h3>
          <form onSubmit={(e) => { e.preventDefault(); setAddError(null); createMutation.mutate(addForm) }} className="max-w-lg space-y-4">
            <WebhookFormFields form={addForm} onChange={setAddForm} />
            {addError && <p role="alert" className="text-sm text-red-400">{addError}</p>}
            <div className="flex gap-2">
              <button
                type="submit"
                disabled={createMutation.isPending}
                className="inline-flex items-center gap-2 rounded-md bg-indigo-600 px-4 py-2 text-sm font-semibold text-white hover:bg-indigo-500 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
              >
                {createMutation.isPending ? 'Creating…' : 'Create webhook'}
              </button>
              <button
                type="button"
                onClick={() => { setShowAddForm(false); setAddForm(emptyForm); setAddError(null) }}
                className="rounded-md px-4 py-2 text-sm font-medium text-gray-400 hover:text-gray-200 hover:bg-gray-700 transition-colors"
              >
                Cancel
              </button>
            </div>
          </form>
        </div>
      )}

      {isLoading ? (
        <div className="space-y-3 animate-pulse">
          {[1, 2].map((i) => <div key={i} className="h-20 bg-gray-800 rounded-lg border border-gray-700" />)}
        </div>
      ) : error ? (
        <p className="text-sm text-red-400">Failed to load webhooks.</p>
      ) : webhooks.length === 0 ? (
        <div className="bg-gray-800 border border-gray-700 rounded-lg p-6 text-center">
          <p className="text-sm text-gray-500">No webhooks configured yet.</p>
        </div>
      ) : (
        <ul className="space-y-3" aria-label="Webhooks">
          {webhooks.map((webhook) => (
            <li key={webhook.id} className="bg-gray-800 border border-gray-700 rounded-lg">
              {editingId === webhook.id ? (
                <div className="p-4">
                  <h3 className="text-sm font-medium text-gray-200 mb-4">Edit webhook</h3>
                  <form onSubmit={(e) => { e.preventDefault(); setEditError(null); updateMutation.mutate({ id: webhook.id, ...editForm }) }} className="max-w-lg space-y-4">
                    <WebhookFormFields form={editForm} onChange={setEditForm} />
                    {editError && <p role="alert" className="text-sm text-red-400">{editError}</p>}
                    <div className="flex gap-2">
                      <button
                        type="submit"
                        disabled={updateMutation.isPending}
                        className="inline-flex items-center gap-2 rounded-md bg-indigo-600 px-4 py-2 text-sm font-semibold text-white hover:bg-indigo-500 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                      >
                        {updateMutation.isPending ? 'Saving…' : 'Save'}
                      </button>
                      <button
                        type="button"
                        onClick={() => { setEditingId(null); setEditError(null) }}
                        className="rounded-md px-4 py-2 text-sm font-medium text-gray-400 hover:text-gray-200 hover:bg-gray-700 transition-colors"
                      >
                        Cancel
                      </button>
                    </div>
                  </form>
                </div>
              ) : (
                <div className="px-4 py-3">
                  <div className="flex items-start justify-between gap-4">
                    <div className="min-w-0">
                      <p className="text-sm font-mono text-gray-200 truncate">{webhook.url}</p>
                      <div className="flex flex-wrap gap-1 mt-1">
                        {webhook.events.map((ev) => (
                          <span key={ev} className="text-xs bg-gray-700 text-gray-300 rounded px-1.5 py-0.5">{ev}</span>
                        ))}
                      </div>
                      <span className={`text-xs font-medium mt-1 inline-block ${webhook.active ? 'text-green-400' : 'text-gray-500'}`}>
                        {webhook.active ? 'Active' : 'Inactive'}
                      </span>
                    </div>
                    <div className="flex items-center gap-2 flex-shrink-0">
                      <button
                        onClick={() => testMutation.mutate(webhook.id)}
                        disabled={testMutation.isPending}
                        aria-label={`Test webhook ${webhook.url}`}
                        className="text-xs text-indigo-400 hover:text-indigo-300 disabled:opacity-50 transition-colors"
                      >
                        Test
                      </button>
                      <button
                        onClick={() => { setEditingId(webhook.id); setEditForm({ url: webhook.url, secret: webhook.secret ?? '', events: webhook.events, active: webhook.active }) }}
                        aria-label={`Edit webhook ${webhook.url}`}
                        className="text-xs text-gray-400 hover:text-gray-200 transition-colors"
                      >
                        Edit
                      </button>
                      <button
                        onClick={() => deleteMutation.mutate(webhook.id)}
                        disabled={deleteMutation.isPending}
                        aria-label={`Delete webhook ${webhook.url}`}
                        className="text-xs text-red-400 hover:text-red-300 disabled:opacity-50 transition-colors"
                      >
                        Delete
                      </button>
                    </div>
                  </div>
                  {testResults[webhook.id] && (
                    <p role="status" className={`text-xs mt-2 ${testResults[webhook.id].ok ? 'text-green-400' : 'text-red-400'}`}>
                      {testResults[webhook.id].msg}
                    </p>
                  )}
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

// ─── Tab: Danger Zone ─────────────────────────────────────────────────────────

interface DangerZoneTabProps {
  owner: string
  repo: string
  repoName: string
}

function DangerZoneTab({ owner, repo, repoName }: DangerZoneTabProps) {
  const api = useApiClient()
  const navigate = useNavigate()

  const [showConfirm, setShowConfirm] = useState(false)
  const [confirmInput, setConfirmInput] = useState('')
  const [deleteError, setDeleteError] = useState<string | null>(null)

  const deleteMutation = useMutation<unknown, ApiError, void>({
    mutationFn: () => api.delete(`/api/repos/${owner}/${repo}`),
    onSuccess: () => { navigate(`/${owner}`) },
    onError: (err) => { setDeleteError(err.message ?? 'Failed to delete repository.') },
  })

  function handleDelete(e: React.FormEvent) {
    e.preventDefault()
    if (confirmInput !== repoName) {
      setDeleteError(`Please type "${repoName}" to confirm.`)
      return
    }
    setDeleteError(null)
    deleteMutation.mutate()
  }

  return (
    <section aria-labelledby="danger-heading">
      <h2 id="danger-heading" className="text-lg font-semibold text-red-400 mb-6">Danger Zone</h2>
      <div className="border border-red-900 bg-red-950 rounded-lg p-6 max-w-2xl">
        <h3 className="text-base font-semibold text-red-300 mb-2">Delete this repository</h3>
        <p className="text-sm text-red-200 mb-4">
          Once you delete a repository, there is no going back. This action is permanent and cannot be undone.
        </p>
        {!showConfirm ? (
          <button
            onClick={() => setShowConfirm(true)}
            className="inline-flex items-center gap-2 rounded-md bg-red-700 px-4 py-2 text-sm font-semibold text-white hover:bg-red-600 transition-colors focus:outline-none focus:ring-2 focus:ring-red-500"
          >
            Delete this repository
          </button>
        ) : (
          <form onSubmit={handleDelete} className="space-y-4">
            <div>
              <label htmlFor="confirm-delete" className="block text-sm font-medium text-red-200 mb-2">
                Type <span className="font-mono font-bold">{repoName}</span> to confirm:
              </label>
              <input
                id="confirm-delete"
                type="text"
                value={confirmInput}
                onChange={(e) => setConfirmInput(e.target.value)}
                required
                autoFocus
                className="w-full rounded-md bg-red-900 border border-red-700 text-red-100 placeholder-red-400 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-red-500 focus:border-transparent"
              />
            </div>
            {deleteError && <p role="alert" className="text-sm text-red-300">{deleteError}</p>}
            <div className="flex gap-2">
              <button
                type="submit"
                disabled={deleteMutation.isPending || confirmInput !== repoName}
                className="inline-flex items-center gap-2 rounded-md bg-red-700 px-4 py-2 text-sm font-semibold text-white hover:bg-red-600 disabled:opacity-50 disabled:cursor-not-allowed transition-colors focus:outline-none focus:ring-2 focus:ring-red-500"
              >
                {deleteMutation.isPending ? 'Deleting…' : 'I understand, delete this repository'}
              </button>
              <button
                type="button"
                onClick={() => { setShowConfirm(false); setConfirmInput(''); setDeleteError(null) }}
                className="rounded-md px-4 py-2 text-sm font-medium text-red-300 hover:text-red-100 hover:bg-red-900 transition-colors focus:outline-none focus:ring-2 focus:ring-red-500"
              >
                Cancel
              </button>
            </div>
          </form>
        )}
      </div>
    </section>
  )
}

// ─── RepoSettingsPage ─────────────────────────────────────────────────────────

const TABS: { id: TabId; label: string }[] = [
  { id: 'general', label: 'General' },
  { id: 'collaborators', label: 'Collaborators' },
  { id: 'webhooks', label: 'Webhooks' },
  { id: 'danger', label: 'Danger Zone' },
]

export default function RepoSettingsPage() {
  const { owner, repo } = useParams<{ owner: string; repo: string }>()
  const { user } = useAuth()
  const api = useApiClient()
  const navigate = useNavigate()

  const [activeTab, setActiveTab] = useState<TabId>('general')

  const { data: repoInfo, isLoading, error } = useQuery<RepoInfo, ApiError>({
    queryKey: ['repo', owner, repo],
    queryFn: () => api.get<RepoInfo>(`/api/repos/${owner}/${repo}`),
    enabled: !!owner && !!repo,
  })

  // Redirect non-owners
  useEffect(() => {
    if (repoInfo && user && repoInfo.owner.id !== user.id) {
      navigate(`/${owner}/${repo}`)
    }
  }, [repoInfo, user, owner, repo, navigate])

  if (!owner || !repo) {
    return (
      <div className="min-h-screen bg-gray-950 text-gray-100 flex items-center justify-center">
        <p className="text-sm text-gray-500">Invalid repository URL.</p>
      </div>
    )
  }

  if (isLoading) {
    return (
      <div className="min-h-screen bg-gray-950 text-gray-100 flex items-center justify-center">
        <p className="text-sm text-gray-500">Loading…</p>
      </div>
    )
  }

  if (error || !repoInfo) {
    return (
      <div className="min-h-screen bg-gray-950 text-gray-100 flex items-center justify-center">
        <p className="text-sm text-red-400">Failed to load repository settings.</p>
      </div>
    )
  }

  if (user && repoInfo.owner.id !== user.id) {
    return null
  }

  return (
    <div className="min-h-screen bg-gray-950 text-gray-100">
      <header className="border-b border-gray-800 bg-gray-900">
        <div className="max-w-5xl mx-auto px-4 py-3 flex items-center gap-3">
          <Link
            to="/"
            className="flex items-center gap-2 text-white font-bold text-lg hover:text-indigo-400 transition-colors"
            aria-label="DVCS home"
          >
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" className="w-6 h-6 text-indigo-400" aria-hidden="true">
              <path fillRule="evenodd" d="M12 2C6.477 2 2 6.477 2 12s4.477 10 10 10 10-4.477 10-10S17.523 2 12 2zm-1 14.5v-5l-3 3-1.5-1.5 4.5-4.5 4.5 4.5-1.5 1.5-3-3v5h-1z" clipRule="evenodd" />
            </svg>
            <span>DVCS</span>
          </Link>
          <Link to={`/${owner}/${repo}`} className="ml-auto text-sm text-gray-400 hover:text-gray-200 transition-colors">
            {owner}/{repo}
          </Link>
        </div>
      </header>

      <main className="max-w-5xl mx-auto px-4 py-8">
        <h1 className="text-2xl font-bold text-gray-100 mb-6">Repository Settings</h1>

        <div className="flex flex-col md:flex-row gap-8">
          {/* Sidebar tab nav */}
          <nav aria-label="Settings sections" className="md:w-48 flex-shrink-0">
            <ul className="space-y-1">
              {TABS.map(({ id, label }) => (
                <li key={id}>
                  <button
                    onClick={() => setActiveTab(id)}
                    aria-current={activeTab === id ? 'page' : undefined}
                    className={[
                      'w-full text-left rounded-md px-3 py-2 text-sm font-medium transition-colors focus:outline-none focus:ring-2 focus:ring-indigo-500',
                      activeTab === id
                        ? id === 'danger'
                          ? 'bg-red-900 text-red-200'
                          : 'bg-indigo-600 text-white'
                        : 'text-gray-400 hover:bg-gray-800 hover:text-gray-200',
                    ].join(' ')}
                  >
                    {label}
                  </button>
                </li>
              ))}
            </ul>
          </nav>

          {/* Tab content */}
          <div className="flex-1 min-w-0 bg-gray-900 border border-gray-700 rounded-lg p-6">
            {activeTab === 'general' && <GeneralTab owner={owner} repo={repo} repoInfo={repoInfo} />}
            {activeTab === 'collaborators' && <CollaboratorsTab owner={owner} repo={repo} />}
            {activeTab === 'webhooks' && <WebhooksTab owner={owner} repo={repo} />}
            {activeTab === 'danger' && <DangerZoneTab owner={owner} repo={repo} repoName={repoInfo.name} />}
          </div>
        </div>
      </main>
    </div>
  )
}
