/**
 * ExplorePage — public explore page at route /explore
 *
 * Features:
 * - Search bar wired to GET /api/search?q=&type=repositories
 * - Debounced search input (300ms)
 * - URL search params sync (?q=...)
 * - Trending repos when query is empty / < 2 chars
 * - Loading skeleton, empty state
 * - TailwindCSS dark theme
 */

import { useState, useEffect, useCallback } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useApiClient } from '../api/client'

// ─── Types ────────────────────────────────────────────────────────────────────

interface RepoSearchResult {
  id: number
  name: string
  description: string | null
  ownerUsername: string
  createdAt: string
  updatedAt?: string | null
}

interface PagedResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

// ─── useDebounce hook ─────────────────────────────────────────────────────────

function useDebounce<T>(value: T, delayMs: number): T {
  const [debounced, setDebounced] = useState<T>(value)

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delayMs)
    return () => clearTimeout(timer)
  }, [value, delayMs])

  return debounced
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

function formatUpdatedAt(isoString: string | null | undefined): string {
  if (!isoString) return ''
  try {
    const now = Date.now()
    const then = new Date(isoString).getTime()
    const diffMs = now - then
    const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24))

    if (diffDays === 0) return 'Updated today'
    if (diffDays === 1) return 'Updated yesterday'
    if (diffDays < 30) return `Updated ${diffDays} days ago`

    return `Updated ${new Date(isoString).toLocaleDateString('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
    })}`
  } catch {
    return ''
  }
}

// ─── Skeleton ─────────────────────────────────────────────────────────────────

function RepoCardSkeleton() {
  return (
    <div
      className="bg-gray-800 border border-gray-700 rounded-lg p-5 animate-pulse"
      aria-hidden="true"
    >
      <div className="flex items-center gap-2 mb-3">
        <div className="w-4 h-4 rounded bg-gray-700" />
        <div className="h-4 bg-gray-700 rounded w-40" />
      </div>
      <div className="h-3 bg-gray-700 rounded w-full mb-2" />
      <div className="h-3 bg-gray-700 rounded w-3/4 mb-4" />
      <div className="flex items-center gap-3">
        <div className="h-3 bg-gray-700 rounded w-20" />
        <div className="h-3 bg-gray-700 rounded w-24 ml-auto" />
      </div>
    </div>
  )
}

function SearchResultsSkeleton() {
  return (
    <div aria-busy="true" aria-label="Loading repositories">
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {[1, 2, 3, 4, 5, 6].map((i) => (
          <RepoCardSkeleton key={i} />
        ))}
      </div>
    </div>
  )
}

// ─── RepoCard ─────────────────────────────────────────────────────────────────

interface RepoCardProps {
  repo: RepoSearchResult
}

function RepoCard({ repo }: RepoCardProps) {
  return (
    <article className="bg-gray-800 border border-gray-700 rounded-lg p-5 hover:border-gray-500 transition-colors flex flex-col gap-2">
      {/* Repo name */}
      <div className="flex items-center gap-2">
        <svg
          xmlns="http://www.w3.org/2000/svg"
          viewBox="0 0 16 16"
          fill="currentColor"
          className="w-4 h-4 text-gray-400 flex-shrink-0"
          aria-hidden="true"
        >
          <path d="M2 2.5A2.5 2.5 0 0 1 4.5 0h8.75a.75.75 0 0 1 .75.75v12.5a.75.75 0 0 1-.75.75h-2.5a.75.75 0 0 1 0-1.5h1.75v-2h-8a1 1 0 0 0-.714 1.7.75.75 0 1 1-1.072 1.05A2.495 2.495 0 0 1 2 11.5Zm10.5-1h-8a1 1 0 0 0-1 1v6.708A2.486 2.486 0 0 1 4.5 9h8V1.5Z" />
        </svg>
        <Link
          to={`/${repo.ownerUsername}/${repo.name}`}
          className="text-indigo-400 hover:text-indigo-300 font-medium text-sm truncate transition-colors"
        >
          {repo.ownerUsername}/{repo.name}
        </Link>
      </div>

      {/* Description */}
      {repo.description ? (
        <p className="text-gray-400 text-xs leading-relaxed line-clamp-2">
          {repo.description}
        </p>
      ) : (
        <p className="text-gray-600 text-xs italic">No description</p>
      )}

      {/* Footer: owner + updated */}
      <div className="flex items-center gap-3 mt-auto pt-1 flex-wrap">
        <Link
          to={`/${repo.ownerUsername}`}
          className="flex items-center gap-1.5 text-xs text-gray-500 hover:text-gray-300 transition-colors"
        >
          <svg
            xmlns="http://www.w3.org/2000/svg"
            viewBox="0 0 16 16"
            fill="currentColor"
            className="w-3.5 h-3.5"
            aria-hidden="true"
          >
            <path d="M8 8a3 3 0 1 0 0-6 3 3 0 0 0 0 6ZM12.735 14c.618 0 1.093-.561.872-1.139a6.002 6.002 0 0 0-11.215 0c-.22.578.254 1.139.872 1.139h9.47Z" />
          </svg>
          {repo.ownerUsername}
        </Link>

        {(repo.updatedAt ?? repo.createdAt) && (
          <span className="text-xs text-gray-500 ml-auto">
            {formatUpdatedAt(repo.updatedAt ?? repo.createdAt)}
          </span>
        )}
      </div>
    </article>
  )
}

// ─── EmptyState ───────────────────────────────────────────────────────────────

function EmptyState({ query }: { query: string }) {
  return (
    <div className="flex flex-col items-center justify-center py-20 text-center">
      <svg
        xmlns="http://www.w3.org/2000/svg"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.5"
        strokeLinecap="round"
        strokeLinejoin="round"
        className="w-12 h-12 text-gray-600 mb-4"
        aria-hidden="true"
      >
        <path d="M10.5 21a8.5 8.5 0 1 0 0-17 8.5 8.5 0 0 0 0 17Z" />
        <path d="m21 21-4.35-4.35" />
      </svg>
      <p className="text-gray-300 font-medium mb-1">No repositories found</p>
      {query && (
        <p className="text-gray-500 text-sm">
          No results for <span className="font-mono text-gray-400">&ldquo;{query}&rdquo;</span>
        </p>
      )}
    </div>
  )
}

// ─── ExplorePage ──────────────────────────────────────────────────────────────

export default function ExplorePage() {
  const api = useApiClient()
  const [searchParams, setSearchParams] = useSearchParams()

  // Initialise input from URL ?q= param
  const [inputValue, setInputValue] = useState<string>(
    () => searchParams.get('q') ?? '',
  )

  // Debounced query — 300ms delay
  const debouncedQuery = useDebounce(inputValue, 300)

  // Sync URL search params when debounced query changes
  useEffect(() => {
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev)
        if (debouncedQuery) {
          next.set('q', debouncedQuery)
        } else {
          next.delete('q')
        }
        return next
      },
      { replace: true },
    )
  }, [debouncedQuery, setSearchParams])

  // Determine the effective query to send to the API.
  // When < 2 chars, fall back to a trending query ("a" matches many repos).
  const isActiveSearch = debouncedQuery.length >= 2
  const apiQuery = isActiveSearch ? debouncedQuery : 'a'

  const {
    data,
    isLoading,
    isError,
  } = useQuery<PagedResponse<RepoSearchResult>>({
    queryKey: ['explore-repos', apiQuery],
    queryFn: () =>
      api.get<PagedResponse<RepoSearchResult>>(
        `/api/search?q=${encodeURIComponent(apiQuery)}&type=repositories`,
      ),
    staleTime: 30_000,
  })

  const repos = data?.content ?? []
  const totalElements = data?.totalElements ?? 0

  const handleInputChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      setInputValue(e.target.value)
    },
    [],
  )

  const handleClear = useCallback(() => {
    setInputValue('')
  }, [])

  return (
    <div className="min-h-screen bg-gray-950 text-gray-100">
      {/* ── Nav ─────────────────────────────────────────────────────────── */}
      <header className="border-b border-gray-800 bg-gray-900/80 backdrop-blur-sm sticky top-0 z-10">
        <div className="max-w-6xl mx-auto px-4 sm:px-6 py-3 flex items-center gap-4">
          <Link
            to="/"
            className="flex items-center gap-2 text-white font-bold text-lg hover:text-indigo-400 transition-colors flex-shrink-0"
            aria-label="DVCS home"
          >
            <svg
              xmlns="http://www.w3.org/2000/svg"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.5"
              strokeLinecap="round"
              strokeLinejoin="round"
              className="w-6 h-6 text-indigo-400"
              aria-hidden="true"
            >
              <path d="M15 22v-4a4.8 4.8 0 0 0-1-3.5c3 0 6-2 6-5.5.08-1.25-.27-2.48-1-3.5.28-1.15.28-2.35 0-3.5 0 0-1 0-3 1.5-2.64-.5-5.36-.5-8 0C6 2 5 2 5 2c-.3 1.15-.3 2.35 0 3.5A5.403 5.403 0 0 0 4 9c0 3.5 3 5.5 6 5.5-.39.49-.68 1.05-.85 1.65-.17.6-.22 1.23-.15 1.85v4" />
              <path d="M9 18c-4.51 2-5-2-7-2" />
            </svg>
            <span>DVCS</span>
          </Link>

          <nav className="flex items-center gap-2 sm:gap-4 ml-auto" aria-label="Main navigation">
            <Link
              to="/login"
              className="text-sm text-gray-400 hover:text-gray-100 transition-colors px-2 py-1"
            >
              Sign in
            </Link>
            <Link
              to="/register"
              className="text-sm font-medium bg-indigo-600 hover:bg-indigo-500 text-white rounded-md px-3 py-1.5 transition-colors focus:outline-none focus:ring-2 focus:ring-indigo-400 focus:ring-offset-2 focus:ring-offset-gray-950"
            >
              Get Started
            </Link>
          </nav>
        </div>
      </header>

      <main className="max-w-6xl mx-auto px-4 sm:px-6 py-10">
        {/* ── Page heading ─────────────────────────────────────────────── */}
        <div className="mb-8">
          <h1 className="text-2xl sm:text-3xl font-bold text-white mb-2">
            Explore Repositories
          </h1>
          <p className="text-gray-400 text-sm">
            Discover public repositories hosted on this platform.
          </p>
        </div>

        {/* ── Search bar ───────────────────────────────────────────────── */}
        <div className="relative mb-8">
          <label htmlFor="explore-search" className="sr-only">
            Search repositories
          </label>
          {/* Search icon */}
          <svg
            xmlns="http://www.w3.org/2000/svg"
            viewBox="0 0 20 20"
            fill="currentColor"
            className="absolute left-3.5 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-500 pointer-events-none"
            aria-hidden="true"
          >
            <path
              fillRule="evenodd"
              d="M9 3.5a5.5 5.5 0 1 0 0 11 5.5 5.5 0 0 0 0-11ZM2 9a7 7 0 1 1 12.452 4.391l3.328 3.329a.75.75 0 1 1-1.06 1.06l-3.329-3.328A7 7 0 0 1 2 9Z"
              clipRule="evenodd"
            />
          </svg>

          <input
            id="explore-search"
            type="search"
            value={inputValue}
            onChange={handleInputChange}
            placeholder="Search repositories…"
            autoComplete="off"
            spellCheck={false}
            className="w-full bg-gray-800 border border-gray-700 rounded-lg pl-10 pr-10 py-3 text-sm text-gray-100 placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent transition-colors"
            aria-label="Search repositories"
          />

          {/* Clear button */}
          {inputValue && (
            <button
              type="button"
              onClick={handleClear}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-500 hover:text-gray-300 transition-colors focus:outline-none focus:ring-2 focus:ring-indigo-500 rounded"
              aria-label="Clear search"
            >
              <svg
                xmlns="http://www.w3.org/2000/svg"
                viewBox="0 0 20 20"
                fill="currentColor"
                className="w-4 h-4"
                aria-hidden="true"
              >
                <path d="M6.28 5.22a.75.75 0 0 0-1.06 1.06L8.94 10l-3.72 3.72a.75.75 0 1 0 1.06 1.06L10 11.06l3.72 3.72a.75.75 0 1 0 1.06-1.06L11.06 10l3.72-3.72a.75.75 0 0 0-1.06-1.06L10 8.94 6.28 5.22Z" />
              </svg>
            </button>
          )}
        </div>

        {/* ── Results header ───────────────────────────────────────────── */}
        {!isLoading && !isError && (
          <div className="flex items-center justify-between mb-4">
            <p className="text-sm text-gray-400">
              {isActiveSearch ? (
                <>
                  {totalElements.toLocaleString()}{' '}
                  {totalElements === 1 ? 'repository' : 'repositories'} matching{' '}
                  <span className="font-mono text-gray-300">
                    &ldquo;{debouncedQuery}&rdquo;
                  </span>
                </>
              ) : (
                <span className="flex items-center gap-1.5">
                  <svg
                    xmlns="http://www.w3.org/2000/svg"
                    viewBox="0 0 16 16"
                    fill="currentColor"
                    className="w-3.5 h-3.5 text-indigo-400"
                    aria-hidden="true"
                  >
                    <path
                      fillRule="evenodd"
                      d="M8 1.75a.75.75 0 0 1 .692.462l1.41 3.393 3.664.293a.75.75 0 0 1 .428 1.317l-2.791 2.39.853 3.575a.75.75 0 0 1-1.12.814L8 11.232l-3.136 1.762a.75.75 0 0 1-1.12-.814l.853-3.574-2.79-2.39a.75.75 0 0 1 .427-1.318l3.663-.293 1.41-3.393A.75.75 0 0 1 8 1.75Z"
                      clipRule="evenodd"
                    />
                  </svg>
                  Trending repositories
                </span>
              )}
            </p>
          </div>
        )}

        {/* ── Loading state ────────────────────────────────────────────── */}
        {isLoading && <SearchResultsSkeleton />}

        {/* ── Error state ──────────────────────────────────────────────── */}
        {isError && !isLoading && (
          <div className="flex flex-col items-center justify-center py-20 text-center">
            <svg
              xmlns="http://www.w3.org/2000/svg"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.5"
              strokeLinecap="round"
              strokeLinejoin="round"
              className="w-12 h-12 text-red-500/60 mb-4"
              aria-hidden="true"
            >
              <path d="M12 9v4" />
              <path d="M12 17h.01" />
              <path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0Z" />
            </svg>
            <p className="text-gray-300 font-medium mb-1">Failed to load repositories</p>
            <p className="text-gray-500 text-sm">Please try again later.</p>
          </div>
        )}

        {/* ── Results grid ─────────────────────────────────────────────── */}
        {!isLoading && !isError && repos.length === 0 && (
          <EmptyState query={isActiveSearch ? debouncedQuery : ''} />
        )}

        {!isLoading && !isError && repos.length > 0 && (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {repos.map((repo) => (
              <RepoCard key={repo.id} repo={repo} />
            ))}
          </div>
        )}
      </main>
    </div>
  )
}
