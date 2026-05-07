/**
 * BranchSelector — dropdown with fuzzy search for branches and tags.
 *
 * Fetches branches and tags from the API, provides fuzzy filtering via fuse.js,
 * groups results into Branches / Tags sections, and supports keyboard navigation.
 *
 * Props:
 *   - repoOwner: string — repository owner username
 *   - repoName: string — repository name
 *   - currentRef: string — currently selected branch or tag name
 *   - onChange: (ref: string) => void — callback when a new ref is selected
 */

import { useState, useRef, useEffect, useMemo } from 'react'
import Fuse from 'fuse.js'
import { useApiClient } from '../api/client'

// ─── Types ────────────────────────────────────────────────────────────────────

interface Branch {
  name: string
  headSha: string
  protected: boolean
}

interface Tag {
  name: string
  commitSha: string
}

interface RefItem {
  name: string
  type: 'branch' | 'tag'
}

interface BranchSelectorProps {
  repoOwner: string
  repoName: string
  currentRef: string
  onChange: (ref: string) => void
}

// ─── BranchSelector ───────────────────────────────────────────────────────────

export default function BranchSelector({
  repoOwner,
  repoName,
  currentRef,
  onChange,
}: BranchSelectorProps) {
  const api = useApiClient()

  // State
  const [open, setOpen] = useState(false)
  const [searchQuery, setSearchQuery] = useState('')
  const [branches, setBranches] = useState<Branch[]>([])
  const [tags, setTags] = useState<Tag[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [selectedIndex, setSelectedIndex] = useState(0)

  // Refs
  const containerRef = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLInputElement>(null)
  const dropdownRef = useRef<HTMLDivElement>(null)

  // Fetch branches and tags when dropdown opens
  useEffect(() => {
    if (!open) return

    const fetchRefs = async () => {
      setLoading(true)
      setError(null)

      try {
        // Fetch branches
        const branchesData = await api.get<Branch[]>(
          `/api/repos/${repoOwner}/${repoName}/branches`
        )
        setBranches(branchesData)

        // Fetch tags (assuming similar endpoint structure)
        try {
          const tagsData = await api.get<Tag[]>(
            `/api/repos/${repoOwner}/${repoName}/tags`
          )
          setTags(tagsData)
        } catch {
          // Tags endpoint might not exist yet, silently ignore
          setTags([])
        }
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Failed to fetch refs')
        setBranches([])
        setTags([])
      } finally {
        setLoading(false)
      }
    }

    fetchRefs()
  }, [open, repoOwner, repoName, api])

  // Build combined ref list
  const allRefs = useMemo<RefItem[]>(() => {
    const branchRefs: RefItem[] = branches.map((b) => ({
      name: b.name,
      type: 'branch' as const,
    }))
    const tagRefs: RefItem[] = tags.map((t) => ({
      name: t.name,
      type: 'tag' as const,
    }))
    return [...branchRefs, ...tagRefs]
  }, [branches, tags])

  // Fuzzy search with fuse.js
  const fuse = useMemo(
    () =>
      new Fuse(allRefs, {
        keys: ['name'],
        threshold: 0.3,
        includeScore: true,
      }),
    [allRefs]
  )

  const filteredRefs = useMemo<RefItem[]>(() => {
    if (!searchQuery.trim()) {
      return allRefs
    }
    return fuse.search(searchQuery).map((result) => result.item)
  }, [searchQuery, allRefs, fuse])

  // Group filtered refs by type
  const groupedRefs = useMemo(() => {
    const branchGroup = filteredRefs.filter((r) => r.type === 'branch')
    const tagGroup = filteredRefs.filter((r) => r.type === 'tag')
    return { branches: branchGroup, tags: tagGroup }
  }, [filteredRefs])

  // Flatten for keyboard navigation
  const flatList = useMemo<RefItem[]>(() => {
    return [...groupedRefs.branches, ...groupedRefs.tags]
  }, [groupedRefs])

  // Reset selected index when filtered list changes
  useEffect(() => {
    setSelectedIndex(0)
  }, [flatList])

  // Focus input when dropdown opens
  useEffect(() => {
    if (open && inputRef.current) {
      inputRef.current.focus()
    }
  }, [open])

  // Close dropdown on outside click
  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (
        containerRef.current &&
        !containerRef.current.contains(e.target as Node)
      ) {
        setOpen(false)
        setSearchQuery('')
      }
    }

    if (open) {
      document.addEventListener('mousedown', handleClickOutside)
    }

    return () => {
      document.removeEventListener('mousedown', handleClickOutside)
    }
  }, [open])

  // Keyboard navigation
  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (!open) {
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault()
        setOpen(true)
      }
      return
    }

    switch (e.key) {
      case 'Escape':
        e.preventDefault()
        setOpen(false)
        setSearchQuery('')
        break

      case 'ArrowDown':
        e.preventDefault()
        setSelectedIndex((prev) => Math.min(prev + 1, flatList.length - 1))
        scrollToSelected()
        break

      case 'ArrowUp':
        e.preventDefault()
        setSelectedIndex((prev) => Math.max(prev - 1, 0))
        scrollToSelected()
        break

      case 'Enter':
        e.preventDefault()
        if (flatList.length > 0 && selectedIndex >= 0) {
          const selected = flatList[selectedIndex]
          handleSelect(selected.name)
        }
        break

      default:
        break
    }
  }

  // Scroll selected item into view
  const scrollToSelected = () => {
    setTimeout(() => {
      const selectedElement = dropdownRef.current?.querySelector(
        `[data-index="${selectedIndex}"]`
      )
      if (selectedElement) {
        selectedElement.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
      }
    }, 0)
  }

  // Handle ref selection
  const handleSelect = (refName: string) => {
    onChange(refName)
    setOpen(false)
    setSearchQuery('')
    setSelectedIndex(0)
  }

  return (
    <div className="relative" ref={containerRef}>
      {/* Trigger button */}
      <button
        type="button"
        onClick={() => setOpen((prev) => !prev)}
        onKeyDown={handleKeyDown}
        aria-label={`Current branch: ${currentRef}. Click to switch.`}
        aria-haspopup="true"
        aria-expanded={open}
        className="flex items-center gap-2 px-3 py-1.5 rounded-md bg-gray-700 hover:bg-gray-600 text-gray-200 text-sm font-medium transition-colors focus:outline-none focus:ring-2 focus:ring-indigo-500"
      >
        {/* Branch icon */}
        <svg
          xmlns="http://www.w3.org/2000/svg"
          viewBox="0 0 16 16"
          fill="currentColor"
          className="w-4 h-4"
          aria-hidden="true"
        >
          <path
            fillRule="evenodd"
            d="M11.75 2.5a.75.75 0 1 0 0 1.5.75.75 0 0 0 0-1.5zm-2.25.75a2.25 2.25 0 1 1 3 2.122V6A2.5 2.5 0 0 1 10 8.5H6a1 1 0 0 0-1 1v1.128a2.251 2.251 0 1 1-1.5 0V5.372a2.25 2.25 0 1 1 1.5 0v1.836A2.492 2.492 0 0 1 6 7h4a1 1 0 0 0 1-1v-.628A2.25 2.25 0 0 1 9.5 3.25zM4.25 12a.75.75 0 1 0 0 1.5.75.75 0 0 0 0-1.5zM3.5 3.25a.75.75 0 1 1 1.5 0 .75.75 0 0 1-1.5 0z"
            clipRule="evenodd"
          />
        </svg>

        <span className="truncate max-w-[150px]">{currentRef}</span>

        {/* Chevron icon */}
        <svg
          xmlns="http://www.w3.org/2000/svg"
          viewBox="0 0 20 20"
          fill="currentColor"
          className={[
            'w-4 h-4 transition-transform',
            open ? 'rotate-180' : '',
          ].join(' ')}
          aria-hidden="true"
        >
          <path
            fillRule="evenodd"
            d="M5.23 7.21a.75.75 0 011.06.02L10 11.168l3.71-3.938a.75.75 0 111.08 1.04l-4.25 4.5a.75.75 0 01-1.08 0l-4.25-4.5a.75.75 0 01.02-1.06z"
            clipRule="evenodd"
          />
        </svg>
      </button>

      {/* Dropdown */}
      {open && (
        <div
          role="dialog"
          aria-label="Select branch or tag"
          className="absolute left-0 mt-2 w-80 rounded-md shadow-xl bg-gray-800 border border-gray-700 z-50 overflow-hidden"
        >
          {/* Search input */}
          <div className="p-3 border-b border-gray-700">
            <input
              ref={inputRef}
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              onKeyDown={handleKeyDown}
              placeholder="Search branches and tags..."
              className="w-full px-3 py-2 rounded-md bg-gray-700 text-gray-200 text-sm placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-indigo-500"
              aria-label="Search branches and tags"
            />
          </div>

          {/* Content */}
          <div
            ref={dropdownRef}
            className="max-h-96 overflow-y-auto"
            onKeyDown={handleKeyDown}
          >
            {loading ? (
              <div className="px-4 py-6 text-sm text-gray-500 text-center">
                Loading...
              </div>
            ) : error ? (
              <div className="px-4 py-6 text-sm text-red-400 text-center">
                {error}
              </div>
            ) : flatList.length === 0 ? (
              <div className="px-4 py-6 text-sm text-gray-500 text-center">
                No branches or tags found
              </div>
            ) : (
              <>
                {/* Branches section */}
                {groupedRefs.branches.length > 0 && (
                  <div>
                    <div className="px-4 py-2 text-xs font-semibold text-gray-400 uppercase tracking-wide bg-gray-750">
                      Branches
                    </div>
                    <div>
                      {groupedRefs.branches.map((ref, idx) => {
                        const globalIndex = idx
                        const isSelected = globalIndex === selectedIndex
                        const isCurrent = ref.name === currentRef

                        return (
                          <button
                            key={`branch-${ref.name}`}
                            type="button"
                            data-index={globalIndex}
                            onClick={() => handleSelect(ref.name)}
                            className={[
                              'w-full text-left px-4 py-2.5 flex items-center gap-2 transition-colors',
                              isSelected
                                ? 'bg-indigo-600 text-white'
                                : 'hover:bg-gray-700 text-gray-200',
                            ].join(' ')}
                            aria-label={`Branch: ${ref.name}${isCurrent ? ' (current)' : ''}`}
                          >
                            {/* Branch icon */}
                            <svg
                              xmlns="http://www.w3.org/2000/svg"
                              viewBox="0 0 16 16"
                              fill="currentColor"
                              className="w-4 h-4 flex-shrink-0"
                              aria-hidden="true"
                            >
                              <path
                                fillRule="evenodd"
                                d="M11.75 2.5a.75.75 0 1 0 0 1.5.75.75 0 0 0 0-1.5zm-2.25.75a2.25 2.25 0 1 1 3 2.122V6A2.5 2.5 0 0 1 10 8.5H6a1 1 0 0 0-1 1v1.128a2.251 2.251 0 1 1-1.5 0V5.372a2.25 2.25 0 1 1 1.5 0v1.836A2.492 2.492 0 0 1 6 7h4a1 1 0 0 0 1-1v-.628A2.25 2.25 0 0 1 9.5 3.25zM4.25 12a.75.75 0 1 0 0 1.5.75.75 0 0 0 0-1.5zM3.5 3.25a.75.75 0 1 1 1.5 0 .75.75 0 0 1-1.5 0z"
                                clipRule="evenodd"
                              />
                            </svg>

                            <span className="flex-1 truncate text-sm">
                              {ref.name}
                            </span>

                            {/* Current indicator */}
                            {isCurrent && (
                              <svg
                                xmlns="http://www.w3.org/2000/svg"
                                viewBox="0 0 20 20"
                                fill="currentColor"
                                className="w-4 h-4 flex-shrink-0"
                                aria-hidden="true"
                              >
                                <path
                                  fillRule="evenodd"
                                  d="M16.704 4.153a.75.75 0 01.143 1.052l-8 10.5a.75.75 0 01-1.127.075l-4.5-4.5a.75.75 0 011.06-1.06l3.894 3.893 7.48-9.817a.75.75 0 011.05-.143z"
                                  clipRule="evenodd"
                                />
                              </svg>
                            )}
                          </button>
                        )
                      })}
                    </div>
                  </div>
                )}

                {/* Tags section */}
                {groupedRefs.tags.length > 0 && (
                  <div>
                    <div className="px-4 py-2 text-xs font-semibold text-gray-400 uppercase tracking-wide bg-gray-750">
                      Tags
                    </div>
                    <div>
                      {groupedRefs.tags.map((ref, idx) => {
                        const globalIndex = groupedRefs.branches.length + idx
                        const isSelected = globalIndex === selectedIndex
                        const isCurrent = ref.name === currentRef

                        return (
                          <button
                            key={`tag-${ref.name}`}
                            type="button"
                            data-index={globalIndex}
                            onClick={() => handleSelect(ref.name)}
                            className={[
                              'w-full text-left px-4 py-2.5 flex items-center gap-2 transition-colors',
                              isSelected
                                ? 'bg-indigo-600 text-white'
                                : 'hover:bg-gray-700 text-gray-200',
                            ].join(' ')}
                            aria-label={`Tag: ${ref.name}${isCurrent ? ' (current)' : ''}`}
                          >
                            {/* Tag icon */}
                            <svg
                              xmlns="http://www.w3.org/2000/svg"
                              viewBox="0 0 16 16"
                              fill="currentColor"
                              className="w-4 h-4 flex-shrink-0"
                              aria-hidden="true"
                            >
                              <path
                                fillRule="evenodd"
                                d="M2.5 1.75a.25.25 0 01.25-.25h8.5a.25.25 0 01.177.073l3.25 3.25a.25.25 0 010 .354l-8.5 8.5a.25.25 0 01-.354 0l-3.25-3.25a.25.25 0 010-.354l8.5-8.5zM11 2.5L2.5 11l2.5 2.5L13.5 5 11 2.5z"
                                clipRule="evenodd"
                              />
                              <circle cx="9.5" cy="4.5" r="1" />
                            </svg>

                            <span className="flex-1 truncate text-sm">
                              {ref.name}
                            </span>

                            {/* Current indicator */}
                            {isCurrent && (
                              <svg
                                xmlns="http://www.w3.org/2000/svg"
                                viewBox="0 0 20 20"
                                fill="currentColor"
                                className="w-4 h-4 flex-shrink-0"
                                aria-hidden="true"
                              >
                                <path
                                  fillRule="evenodd"
                                  d="M16.704 4.153a.75.75 0 01.143 1.052l-8 10.5a.75.75 0 01-1.127.075l-4.5-4.5a.75.75 0 011.06-1.06l3.894 3.893 7.48-9.817a.75.75 0 011.05-.143z"
                                  clipRule="evenodd"
                                />
                              </svg>
                            )}
                          </button>
                        )
                      })}
                    </div>
                  </div>
                )}
              </>
            )}
          </div>
        </div>
      )}
    </div>
  )
}
