import { useState } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useApiClient, ApiError } from '../api/client'

export default function CreateIssuePage() {
  const { owner, repo } = useParams<{ owner: string; repo: string }>()
  const navigate = useNavigate()
  const api = useApiClient()
  const queryClient = useQueryClient()

  const [title, setTitle] = useState('')
  const [body, setBody] = useState('')
  const [error, setError] = useState<string | null>(null)

  const createIssueMutation = useMutation({
    mutationFn: (data: { title: string; body: string }) =>
      api.post(`/api/repos/${owner}/${repo}/issues`, data),
    onSuccess: (newIssue: any) => {
      queryClient.invalidateQueries({ queryKey: ['repo-issues', owner, repo] })
      navigate(`/${owner}/${repo}/issues/${newIssue.number}`)
    },
    onError: (err: unknown) => {
      setError(err instanceof ApiError ? err.message : 'Failed to create issue')
    },
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)

    if (!title.trim()) {
      setError('Title is required')
      return
    }

    createIssueMutation.mutate({
      title: title.trim(),
      body: body.trim(),
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
          <Link to={`/${owner}/${repo}/issues`} className="hover:text-indigo-400">
            issues
          </Link>
          <span>/</span>
          <span className="text-gray-200">new</span>
        </nav>

        <h1 className="text-2xl font-bold mb-6">Create new issue</h1>

        <form onSubmit={handleSubmit} className="bg-gray-800 border border-gray-700 rounded-lg p-6 space-y-4">
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
              rows={10}
              className="w-full bg-gray-900 border border-gray-700 rounded-md px-3 py-2 text-gray-100 focus:outline-none focus:ring-2 focus:ring-indigo-500 resize-none"
            />
          </div>

          {error && (
            <div className="text-sm text-red-400 bg-red-900/20 border border-red-800 rounded px-3 py-2">
              {error}
            </div>
          )}

          <div className="flex justify-end gap-3 pt-2">
            <button
              type="button"
              onClick={() => navigate(-1)}
              className="px-4 py-2 text-sm font-medium text-gray-300 hover:text-white bg-gray-700 hover:bg-gray-600 rounded-md transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={createIssueMutation.isPending || !title.trim()}
              className="px-4 py-2 text-sm font-semibold text-white bg-indigo-600 hover:bg-indigo-500 rounded-md transition-colors disabled:opacity-50"
            >
              {createIssueMutation.isPending ? 'Creating...' : 'Submit new issue'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
