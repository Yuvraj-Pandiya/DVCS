/**
 * useNotifications — STOMP WebSocket hook for real-time notifications.
 *
 * Connects to `/ws/notifications` via SockJS, subscribes to
 * `/user/queue/notifications`, and maintains local notification state.
 */

import { useState, useEffect, useCallback, useRef } from 'react'
import { Client, IMessage } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { useAuth } from '../context/AuthContext'

// ─── Types ────────────────────────────────────────────────────────────────────

export interface Notification {
  id: number
  subjectType: string
  subjectId: number
  reason: string
  read: boolean
  createdAt: string
}

export interface UseNotificationsResult {
  notifications: Notification[]
  unreadCount: number
  markAsRead: (id: number) => Promise<void>
  isConnected: boolean
}

// ─── Hook ─────────────────────────────────────────────────────────────────────

export function useNotifications(): UseNotificationsResult {
  const { accessToken } = useAuth()

  const [notifications, setNotifications] = useState<Notification[]>([])
  const [unreadCount, setUnreadCount] = useState(0)
  const [isConnected, setIsConnected] = useState(false)

  // Keep a stable ref to the STOMP client so we can disconnect on unmount
  const clientRef = useRef<Client | null>(null)

  useEffect(() => {
    // Only connect when authenticated
    if (!accessToken) {
      return
    }

    const client = new Client({
      // SockJS factory — used as the WebSocket transport
      webSocketFactory: () => new SockJS('/ws/notifications'),

      // Pass JWT in the CONNECT frame headers
      connectHeaders: {
        Authorization: `Bearer ${accessToken}`,
      },

      // Reconnect automatically after 5 s on unexpected disconnect
      reconnectDelay: 5000,

      onConnect: () => {
        setIsConnected(true)

        // Subscribe to the user-specific notification queue
        client.subscribe('/user/queue/notifications', (message: IMessage) => {
          try {
            const notification = JSON.parse(message.body) as Notification
            setNotifications((prev) => [notification, ...prev])
            if (!notification.read) {
              setUnreadCount((prev) => prev + 1)
            }
          } catch {
            // Malformed message — ignore
          }
        })
      },

      onDisconnect: () => {
        setIsConnected(false)
      },

      onStompError: () => {
        setIsConnected(false)
      },
    })

    client.activate()
    clientRef.current = client

    return () => {
      client.deactivate()
      clientRef.current = null
      setIsConnected(false)
    }
    // Re-connect whenever the access token changes (e.g. after a silent refresh)
  }, [accessToken])

  /**
   * Mark a single notification as read via REST and update local state.
   */
  const markAsRead = useCallback(async (id: number): Promise<void> => {
    // Optimistically update local state first
    setNotifications((prev) =>
      prev.map((n) => (n.id === id ? { ...n, read: true } : n))
    )
    setUnreadCount((prev) => Math.max(0, prev - 1))

    try {
      await fetch(`/api/notifications/${id}/read`, {
        method: 'PATCH',
        headers: accessToken
          ? { Authorization: `Bearer ${accessToken}` }
          : undefined,
        credentials: 'include',
      })
    } catch {
      // If the request fails, revert the optimistic update
      setNotifications((prev) =>
        prev.map((n) => (n.id === id ? { ...n, read: false } : n))
      )
      setUnreadCount((prev) => prev + 1)
    }
  }, [accessToken])

  return { notifications, unreadCount, markAsRead, isConnected }
}
