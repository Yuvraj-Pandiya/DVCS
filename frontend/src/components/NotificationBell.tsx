/**
 * NotificationBell — real-time notification bell with dropdown.
 *
 * Uses the `useNotifications` hook to connect to the STOMP WebSocket
 * and display a badge + dropdown list of recent notifications.
 */

import { useState, useRef, useEffect } from 'react'
import { useNotifications, Notification } from '../hooks/useNotifications'

// ─── Helpers ──────────────────────────────────────────────────────────────────

/**
 * Returns a human-readable relative time string (e.g. "2 minutes ago").
 */
function relativeTime(isoString: string): string {
  const now = Date.now()
  const then = new Date(isoString).getTime()
  const diffMs = now - then

  if (diffMs < 0) return 'just now'

  const seconds = Math.floor(diffMs / 1000)
  if (seconds < 60) return `${seconds}s ago`

  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `${minutes}m ago`

  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}h ago`

  const days = Math.floor(hours / 24)
  return `${days}d ago`
}

/**
 * Maps a subjectType to a short emoji label for display.
 */
function subjectLabel(subjectType: string): string {
  switch (subjectType.toLowerCase()) {
    case 'pull_request':
    case 'pullrequest':
      return 'PR'
    case 'issue':
      return 'Issue'
    case 'commit':
      return 'Commit'
    case 'repository':
      return 'Repo'
    default:
      return subjectType
  }
}

// ─── NotificationItem ─────────────────────────────────────────────────────────

interface NotificationItemProps {
  notification: Notification
  onMarkAsRead: (id: number) => void
}

function NotificationItem({ notification, onMarkAsRead }: NotificationItemProps) {
  const { id, subjectType, reason, read, createdAt } = notification

  return (
    <button
      type="button"
      onClick={() => {
        if (!read) {
          onMarkAsRead(id)
        }
      }}
      className={[
        'w-full text-left px-4 py-3 flex items-start gap-3 hover:bg-gray-700 transition-colors',
        !read ? 'bg-gray-750' : '',
      ].join(' ')}
      aria-label={`Notification: ${reason}. ${read ? 'Read' : 'Unread'}`}
    >
      {/* Unread indicator dot */}
      <span
        className={[
          'mt-1.5 flex-shrink-0 w-2 h-2 rounded-full',
          !read ? 'bg-indigo-400' : 'bg-transparent',
        ].join(' ')}
        aria-hidden="true"
      />

      <div className="flex-1 min-w-0">
        {/* Subject type badge + reason */}
        <div className="flex items-center gap-2 flex-wrap">
          <span className="text-xs font-medium px-1.5 py-0.5 rounded bg-gray-600 text-gray-300">
            {subjectLabel(subjectType)}
          </span>
          <span className="text-sm text-gray-200 truncate">{reason}</span>
        </div>

        {/* Relative timestamp */}
        <p className="text-xs text-gray-500 mt-0.5">{relativeTime(createdAt)}</p>
      </div>
    </button>
  )
}

// ─── NotificationBell ─────────────────────────────────────────────────────────

/**
 * Bell icon button with unread badge.
 * Clicking toggles a dropdown showing the 10 most recent notifications.
 * Clicking outside closes the dropdown.
 */
export default function NotificationBell() {
  const { notifications, unreadCount, markAsRead } = useNotifications()
  const [open, setOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)

  // Close dropdown on outside click
  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (
        containerRef.current &&
        !containerRef.current.contains(e.target as Node)
      ) {
        setOpen(false)
      }
    }

    if (open) {
      document.addEventListener('mousedown', handleClickOutside)
    }

    return () => {
      document.removeEventListener('mousedown', handleClickOutside)
    }
  }, [open])

  // Show only the 10 most recent notifications
  const recentNotifications = notifications.slice(0, 10)

  return (
    <div className="relative" ref={containerRef}>
      {/* Bell button */}
      <button
        type="button"
        onClick={() => setOpen((prev) => !prev)}
        aria-label={
          unreadCount > 0
            ? `Notifications — ${unreadCount} unread`
            : 'Notifications'
        }
        aria-haspopup="true"
        aria-expanded={open}
        className="relative p-2 rounded-md text-gray-400 hover:text-gray-200 hover:bg-gray-700 focus:outline-none focus:ring-2 focus:ring-indigo-500 transition-colors"
      >
        {/* Bell SVG icon */}
        <svg
          xmlns="http://www.w3.org/2000/svg"
          viewBox="0 0 24 24"
          fill="currentColor"
          className="w-5 h-5"
          aria-hidden="true"
        >
          <path d="M12 22a2 2 0 0 0 2-2h-4a2 2 0 0 0 2 2zm6-6V11a6 6 0 0 0-5-5.91V4a1 1 0 0 0-2 0v1.09A6 6 0 0 0 6 11v5l-2 2v1h16v-1l-2-2z" />
        </svg>

        {/* Unread badge — hidden when count is 0 */}
        {unreadCount > 0 && (
          <span
            aria-hidden="true"
            className="absolute top-1 right-1 flex items-center justify-center min-w-[1rem] h-4 px-0.5 rounded-full bg-indigo-500 text-white text-[10px] font-bold leading-none"
          >
            {unreadCount > 99 ? '99+' : unreadCount}
          </span>
        )}
      </button>

      {/* Dropdown */}
      {open && (
        <div
          role="dialog"
          aria-label="Notifications"
          className="absolute right-0 mt-2 w-80 rounded-md shadow-xl bg-gray-800 border border-gray-700 z-50 overflow-hidden"
        >
          {/* Header */}
          <div className="px-4 py-2.5 border-b border-gray-700 flex items-center justify-between">
            <h2 className="text-sm font-semibold text-gray-200">
              Notifications
            </h2>
            {unreadCount > 0 && (
              <span className="text-xs text-indigo-400">
                {unreadCount} unread
              </span>
            )}
          </div>

          {/* Notification list */}
          <div className="max-h-96 overflow-y-auto divide-y divide-gray-700">
            {recentNotifications.length === 0 ? (
              <p className="px-4 py-6 text-sm text-gray-500 text-center">
                No notifications yet
              </p>
            ) : (
              recentNotifications.map((n) => (
                <NotificationItem
                  key={n.id}
                  notification={n}
                  onMarkAsRead={markAsRead}
                />
              ))
            )}
          </div>
        </div>
      )}
    </div>
  )
}
