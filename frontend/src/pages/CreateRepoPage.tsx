import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useApiClient, ApiError } from '../api/client'
import { useAuth } from '../context/AuthContext'

interface CreateRepoRequest {
  name: string
  description: string | null
  isPrivate: boolean
  autoInitialize?: boolean
}

interface RepoDto {
  id: number
  name: string
  ownerUsername: string
}

export default function CreateRepoPage() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const api = useApiClient()
  const queryClient = useQueryClient()

  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [isPrivate, setIsPrivate] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const mutation = useMutation<RepoDto, ApiError, CreateRepoRequest>({
    mutationFn: (data) => api.post<RepoDto>('/api/repos', data),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ['user-repos', user?.username] })
      navigate(`/${data.ownerUsername}/${data.name}`)
    },
    onError: (err) => {
      setError(err.message ?? 'Failed to create repository.')
    },
  })

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setError(null)
    if (!name.trim()) return

    mutation.mutate({
      name: name.trim(),
      description: description.trim() || null,
      isPrivate,
      autoInitialize: true, // Default to true for ease of use
    })
  }

  return (
    <div className="min-h-screen bg-gray-950 text-gray-100 flex flex-col items-center py-12 px-4">
      <div className="max-w-xl w-full">
        {/* Header */}
        <div className="mb-8">
          <h1 className="text-3xl font-bold text-white mb-2">Create a new repository</h1>
          <p className="text-gray-400">
            A repository contains all project files, including the revision history.
          </p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-6 bg-gray-900 border border-gray-800 rounded-xl p-8 shadow-xl">
          {/* Owner / Name */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <div className="sm:col-span-1">
              <label className="block text-sm font-medium text-gray-400 mb-1">Owner</label>
              <div className="px-3 py-2 bg-gray-800 border border-gray-700 rounded-md text-gray-300 text-sm font-medium">
                {user?.username}
              </div>
            </div>
            <div className="sm:col-span-2">
              <label htmlFor="repo-name" className="block text-sm font-medium text-gray-300 mb-1">
                Repository name <span className="text-red-500">*</span>
              </label>
              <input
                id="repo-name"
                type="text"
                required
                value={name}
                onChange={(e) => setName(e.target.value.toLowerCase().replace(/[^a-z0-9._-]/g, '-'))}
                placeholder="my-awesome-project"
                className="w-full px-3 py-2 bg-gray-800 border border-gray-700 rounded-md text-white placeholder-gray-500 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent transition-all"
              />
            </div>
          </div>
          <p className="text-xs text-gray-500">
            Great repository names are short and memorable. Need inspiration? How about{' '}
            <span className="text-indigo-400 font-medium italic">sturdy-octo-guide</span>?
          </p>

          {/* Description */}
          <div>
            <label htmlFor="repo-desc" className="block text-sm font-medium text-gray-300 mb-1">
              Description <span className="text-gray-500 font-normal">(optional)</span>
            </label>
            <input
              id="repo-desc"
              type="text"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="A brief description of your project"
              className="w-full px-3 py-2 bg-gray-800 border border-gray-700 rounded-md text-white placeholder-gray-500 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent transition-all"
            />
          </div>

          <hr className="border-gray-800" />

          {/* Visibility */}
          <div className="space-y-4">
            <label className="flex items-start gap-3 cursor-pointer group">
              <input
                type="radio"
                name="visibility"
                checked={!isPrivate}
                onChange={() => setIsPrivate(false)}
                className="mt-1 w-4 h-4 text-indigo-600 bg-gray-800 border-gray-700 focus:ring-indigo-500 focus:ring-offset-gray-900"
              />
              <div>
                <span className="block text-sm font-semibold text-gray-200 group-hover:text-white transition-colors">
                  Public
                </span>
                <span className="block text-xs text-gray-500 leading-relaxed">
                  Anyone on the internet can see this repository. You choose who can commit.
                </span>
              </div>
            </label>

            <label className="flex items-start gap-3 cursor-pointer group">
              <input
                type="radio"
                name="visibility"
                checked={isPrivate}
                onChange={() => setIsPrivate(true)}
                className="mt-1 w-4 h-4 text-indigo-600 bg-gray-800 border-gray-700 focus:ring-indigo-500 focus:ring-offset-gray-900"
              />
              <div>
                <span className="block text-sm font-semibold text-gray-200 group-hover:text-white transition-colors">
                  Private
                </span>
                <span className="block text-xs text-gray-500 leading-relaxed">
                  You choose who can see and commit to this repository.
                </span>
              </div>
            </label>
          </div>

          {/* Error Message */}
          {error && (
            <div className="p-3 bg-red-900/30 border border-red-800 rounded-md text-red-400 text-sm">
              {error}
            </div>
          )}

          <hr className="border-gray-800" />

          {/* Footer actions */}
          <div className="flex items-center justify-end gap-4 pt-2">
            <Link
              to="/"
              className="text-sm font-medium text-gray-400 hover:text-gray-200 transition-colors"
            >
              Cancel
            </Link>
            <button
              type="submit"
              disabled={mutation.isPending || !name.trim()}
              className="px-6 py-2.5 bg-indigo-600 hover:bg-indigo-500 disabled:bg-gray-700 disabled:cursor-not-allowed text-white text-sm font-bold rounded-lg shadow-lg shadow-indigo-600/20 transition-all focus:outline-none focus:ring-2 focus:ring-indigo-400 focus:ring-offset-2 focus:ring-offset-gray-950"
            >
              {mutation.isPending ? 'Creating...' : 'Create repository'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
