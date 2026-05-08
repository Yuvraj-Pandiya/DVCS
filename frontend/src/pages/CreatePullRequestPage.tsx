import { useState } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useApiClient, ApiError } from '../api/client'

interface Branch {
  name: string
  headSha: string
}

export default function CreatePullRequestPage() {
  const { owner, repo } = useParams<{ owner: string; repo: string }>()
  const navigate = useNavigate()
  const api = useApiClient()
  const queryClient = useQueryClient()

  const [title, setTitle] = useState('')
  const [body, setBody] = useState('')
  const [headBranch, setHeadBranch] = useState('')
  const [baseBranch, setBaseBranch] = useState('')
  const [error, setError] = useState<string | null>(null)

  // Fetch branches for selection
  const { data: branches = [], isLoading: branchesLoading } = useQuery<Branch[]>({
    queryKey: ['repo-branches', owner, repo],
    queryFn: () => api.get<Branch[]>(`/api/repos/${owner}/${repo}/branches`),
    enabled: !!owner && !!repo,
  })

  // Set default branches when loaded
  useState(() => {
    if (branches.length >= 2) {
      setBaseBranch(branches[0].name)
      setHeadBranch(branches[1].name)
    } else if (branches.length === 1) {
      setBaseBranch(branches[0].name)
    }
  })

  const createPrMutation = useMutation({
    mutationFn: (data: { title: string; body: string; headBranch: string; baseBranch: string }) =>
      api.post(`/api/repos/${owner}/${repo}/pulls`, data),
    onSuccess: (newPr: any) => {
      queryClient.invalidateQueries({ queryKey: ['repo-pulls', owner, repo] })
      navigate(`/${owner}/${repo}/pulls/${newPr.number}`)
    },
    onError: (err: unknown) => {
      setError(err instanceof ApiError ? err.message : 'Failed to create pull request')
    },
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)

    if (!title.trim()) {
      setError('Title is required')
      return
    }
    if (!headBranch || !baseBranch) {
      setError('Both head and base branches are required')
      return
    }
    if (headBranch === baseBranch) {
      setError('Head and base branches must be different')
      return
    }

    createPrMutation.mutate({
      title: title.trim(),
      body: body.trim(),
      headBranch,
      baseBranch,
    })
  }

  return (
    <div className="min-h-screen bg-gray-950 text-gray-100">
      <div className="max-w-4xl mx-auto px-4 py-8">
        <nav className="flex items-center gap-2 text-sm text-gray-400 mb-6">
          <Link to={`/${owner}/${repo}`} className="hover:text-indigo-400">
            {owner}/{repo}
          </Link>
          <span>/</span>
          <Link to={`/${owner}/${repo}/pulls`} className="hover:text-indigo-400">
            pulls
          </Link>
          <span>/</span>
          <span className="text-gray-200">new</span>
        </nav>

        <h1 className="text-2xl font-bold mb-6">Open a pull request</h1>

        <form onSubmit={handleSubmit} className="bg-gray-800 border border-gray-700 rounded-lg p-6 space-y-6">
          <div className="flex flex-col sm:flex-row gap-4">
            <div className="flex-1">
              <label htmlFor="base-branch" className="block text-sm font-medium text-gray-400 mb-1">
                Base branch
              </label>
              <select
                id="base-branch"
                value={baseBranch}
                onChange={(e) => setBaseBranch(e.target.value)}
                className="w-full bg-gray-900 border border-gray-700 rounded-md px-3 py-2 text-gray-100 focus:outline-none focus:ring-2 focus:ring-indigo-500"
              >
                <option value="">Select base branch</option>
                {branches.map((b) => (
                  <option key={b.name} value={b.name}>
                    {b.name}
                  </option>
                ))}
              </select>
            </div>
            <div className="flex items-center justify-center pt-6">
              <svg className="w-5 h-5 text-gray-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 19l-7-7m0 0l7-7m-7 7h18" />
              </svg>
            </div>
            <div className="flex-1">
              <label htmlFor="head-branch" className="block text-sm font-medium text-gray-400 mb-1">
                Head branch
              </label>
              <select
                id="head-branch"
                value={headBranch}
                onChange={(e) => setHeadBranch(e.target.value)}
                className="w-full bg-gray-900 border border-gray-700 rounded-md px-3 py-2 text-gray-100 focus:outline-none focus:ring-2 focus:ring-indigo-500"
              >
                <option value="">Select head branch</option>
                {branches.map((b) => (
                  <option key={b.name} value={b.name}>
                    {b.name}
                  </option>
                ))}
              </select>
            </div>
          </div>

          <div>
            <label htmlFor="title" className="block text-sm font-medium text-gray-300 mb-1">
              Title
            </label>
            <input
              id="title"
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="Title"
              className="w-full bg-gray-900 border border-gray-700 rounded-md px-3 py-2 text-gray-100 focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
          </div>

          <div>
            <label htmlFor="body" className="block text-sm font-medium text-gray-300 mb-1">
              Description
            </label>
            <textarea
              id="body"
              value={body}
              onChange={(e) => setBody(e.target.value)}
              placeholder="Leave a comment"
              rows={8}
              className="w-full bg-gray-900 border border-gray-700 rounded-md px-3 py-2 text-gray-100 focus:outline-none focus:ring-2 focus:ring-indigo-500 resize-none"
            />
          </div>

          {error && (
            <div className="text-sm text-red-400 bg-red-900/20 border border-red-800 rounded px-3 py-2">
              {error}
            </div>
          )}

          <div className="flex justify-end gap-3">
            <button
              type="button"
              onClick={() => navigate(-1)}
              className="px-4 py-2 text-sm font-medium text-gray-300 hover:text-white bg-gray-700 hover:bg-gray-600 rounded-md transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={createPrMutation.isPending || !title.trim() || !headBranch || !baseBranch}
              className="px-4 py-2 text-sm font-semibold text-white bg-green-600 hover:bg-green-500 rounded-md transition-colors disabled:opacity-50"
            >
              {createPrMutation.isPending ? 'Creating...' : 'Create pull request'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
