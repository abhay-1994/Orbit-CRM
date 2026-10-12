import { useQueryClient } from '@tanstack/react-query'
import { App } from 'antd'
import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { useNavigate } from 'react-router'
import { ApiError, http, openStream, sendOnExit } from '../api/client'
import type { ChatMessage, Conversation, PresenceStatus, PresenceView } from '../api/types'
import { useMe } from '../auth/context'
import { showError } from '../lib/feedback'
import { chatKeys, upsertMessage } from './cache'
import { ChatContext, type ChatState } from './context'
import { PresenceAvatar } from './presence'
import { readEvents, sleep } from './stream'

/** A tab without keyboard or pointer input for this long reports itself idle (all tabs idle = Away). */
const IDLE_AFTER_MS = 5 * 60_000
/** How long "is typing" stays visible after the last typing event. */
const TYPING_SHOWN_MS = 6_000
const INPUT_EVENTS = ['pointerdown', 'pointermove', 'keydown', 'wheel', 'touchstart'] as const

interface Typing {
  userId: string
  until: number
}

/**
 * Owns the live chat connection for the signed-in user: keeps one server-sent event stream open
 * (reconnecting with jittered back-off and catching up by refetching), applies live events to the
 * query cache, tracks presence and typing, reports idle tabs, and notifies about new messages -
 * as a toast while the app is visible, as a desktop notification while it is hidden, never in
 * "Do not disturb".
 */
export function ChatProvider({ children }: { children: ReactNode }) {
  const me = useMe()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const { notification } = App.useApp()
  const [connected, setConnected] = useState(false)
  const [myStatus, setMyStatus] = useState<PresenceStatus>('AVAILABLE')
  const [presence, setPresence] = useState<Record<string, PresenceStatus>>({})
  const [typing, setTyping] = useState<Record<string, Typing>>({})
  const connectionId = useRef<string | null>(null)
  const idle = useRef(false)
  const activeConversation = useRef<string | null>(null)
  const myStatusNow = useRef(myStatus)

  const reportActivity = useCallback((nextIdle: boolean) => {
    idle.current = nextIdle
    if (connectionId.current) {
      http.post('/chat/presence/activity', { connectionId: connectionId.current, idle: nextIdle }).catch(() => {
        // the stream reconnects and reports again
      })
    }
  }, [])

  const notify = async (message: ChatMessage) => {
    if (message.senderId === me.id || myStatusNow.current === 'DO_NOT_DISTURB') return
    const visible = document.visibilityState === 'visible'
    if (visible && activeConversation.current === message.conversationId) return
    const known = queryClient.getQueryData<Conversation[]>(chatKeys.conversations)?.find((c) => c.id === message.conversationId)
    const sender = known?.with ?? (await http.get<Conversation>(`/chat/conversations/${message.conversationId}`).catch(() => null))?.with
    const title = sender?.name ?? 'New message'
    const body = message.body ?? ''
    const open = () => navigate(`/chat/${message.conversationId}`)
    if (visible) {
      notification.open({
        key: message.conversationId,
        title,
        description: body.length > 140 ? `${body.slice(0, 140)}…` : body,
        icon: <PresenceAvatar name={title} size={32} />,
        placement: 'bottomRight',
        onClick: () => {
          open()
          notification.destroy(message.conversationId)
        },
      })
    } else if ('Notification' in window && Notification.permission === 'granted') {
      const desktop = new Notification(title, { body, tag: message.conversationId, icon: '/favicon.png' })
      desktop.onclick = () => {
        window.focus()
        open()
        desktop.close()
      }
    }
  }

  const handle = (name: string, data: any) => {
    switch (name) {
      case 'ready':
        connectionId.current = data.connectionId
        setMyStatus(data.presence.status)
        setConnected(true)
        // Catch up on anything missed while disconnected; live presence restarts from fresh data.
        setPresence({})
        void queryClient.invalidateQueries({ queryKey: chatKeys.all })
        if (idle.current) reportActivity(true)
        break
      case 'resync':
        setPresence({})
        void queryClient.invalidateQueries({ queryKey: chatKeys.all })
        break
      case 'message.created':
      case 'message.updated': {
        const message = data as ChatMessage
        upsertMessage(queryClient, message, name === 'message.created')
        void queryClient.invalidateQueries({ queryKey: chatKeys.conversations })
        void queryClient.invalidateQueries({ queryKey: chatKeys.unread })
        if (name === 'message.created') {
          setTyping((t) => Object.fromEntries(Object.entries(t).filter(([id]) => id !== message.conversationId)))
          void notify(message)
        }
        break
      }
      case 'typing':
        setTyping((t) => ({ ...t, [data.conversationId]: { userId: data.userId, until: Date.now() + TYPING_SHOWN_MS } }))
        break
      case 'read':
        void queryClient.invalidateQueries({ queryKey: chatKeys.conversations })
        if (data.userId === me.id) void queryClient.invalidateQueries({ queryKey: chatKeys.unread })
        break
      case 'presence': {
        const view = data as PresenceView
        if (view.userId === me.id) setMyStatus(view.status)
        setPresence((p) => ({ ...p, [view.userId]: view.status }))
        break
      }
    }
  }
  const handler = useRef(handle)
  useEffect(() => {
    handler.current = handle
    myStatusNow.current = myStatus
  })

  // The live stream: reconnects after errors and after the server recycles it (every few minutes).
  // A closing tab (or signing out) says goodbye, so colleagues see Offline right away.
  useEffect(() => {
    const controller = new AbortController()
    const goodbye = () => {
      if (connectionId.current) sendOnExit('DELETE', `/chat/stream/${connectionId.current}`)
    }
    window.addEventListener('pagehide', goodbye)
    void (async () => {
      let attempt = 0
      while (!controller.signal.aborted) {
        try {
          const res = await openStream('/chat/stream', controller.signal)
          await readEvents(res, (name, data) => {
            if (name === 'ready') attempt = 0
            handler.current(name, JSON.parse(data))
          })
        } catch (error) {
          if (controller.signal.aborted || (error instanceof ApiError && error.status === 401)) return
        }
        setConnected(false)
        connectionId.current = null
        const backoff = Math.min(30_000, 1000 * 2 ** attempt++)
        await sleep(backoff * (0.5 + Math.random() / 2), controller.signal)
      }
    })()
    return () => {
      window.removeEventListener('pagehide', goodbye)
      goodbye()
      controller.abort()
    }
  }, [])

  // Idle detection for Away.
  useEffect(() => {
    let lastInput = Date.now()
    const onInput = () => {
      lastInput = Date.now()
      if (idle.current) reportActivity(false)
    }
    INPUT_EVENTS.forEach((event) => window.addEventListener(event, onInput, { passive: true }))
    const timer = setInterval(() => {
      if (!idle.current && Date.now() - lastInput > IDLE_AFTER_MS) reportActivity(true)
    }, 15_000)
    return () => {
      INPUT_EVENTS.forEach((event) => window.removeEventListener(event, onInput))
      clearInterval(timer)
    }
  }, [reportActivity])

  // "Is typing" expires on its own.
  useEffect(() => {
    const entries = Object.values(typing)
    if (!entries.length) return
    const wait = Math.min(...entries.map((t) => t.until)) - Date.now()
    const timer = setTimeout(() => {
      setTyping((t) => Object.fromEntries(Object.entries(t).filter(([, entry]) => entry.until > Date.now())))
    }, Math.max(wait, 0) + 50)
    return () => clearTimeout(timer)
  }, [typing])

  const chooseStatus = useCallback(async (status: PresenceStatus) => {
    try {
      setMyStatus((await http.put<PresenceView>('/chat/presence', { status })).status)
    } catch (error) {
      showError(error)
    }
  }, [])

  const setActiveConversation = useCallback((conversationId: string | null) => {
    activeConversation.current = conversationId
  }, [])

  const value = useMemo<ChatState>(
    () => ({
      connected,
      myStatus,
      chooseStatus,
      presenceOf: (userId, fallback) => presence[userId] ?? fallback,
      typingIn: (conversationId) => typing[conversationId]?.userId,
      setActiveConversation,
    }),
    [connected, myStatus, chooseStatus, presence, typing, setActiveConversation],
  )

  return <ChatContext.Provider value={value}>{children}</ChatContext.Provider>
}
