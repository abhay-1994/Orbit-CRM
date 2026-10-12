import {
  ArrowDownOutlined,
  ArrowLeftOutlined,
  ClockCircleOutlined,
  DeleteOutlined,
  EditOutlined,
  ExclamationCircleOutlined,
  MoreOutlined,
  SendOutlined,
} from '@ant-design/icons'
import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { App, Button, Dropdown, Input, Spin } from 'antd'
import dayjs from 'dayjs'
import { Fragment, useEffect, useLayoutEffect, useRef, useState, type KeyboardEvent } from 'react'
import { useNavigate } from 'react-router'
import { http, qs } from '../../api/client'
import type { ChatMessage, Conversation, MessagePage } from '../../api/types'
import { useMe } from '../../auth/context'
import { chatKeys, upsertMessage } from '../../chat/cache'
import { useChat } from '../../chat/context'
import { PresenceAvatar, PresenceDot } from '../../chat/presence'
import { PRESENCE } from '../../chat/statuses'
import { errorText } from '../../lib/feedback'
import { dayLabel, formatDateTime, formatTime } from '../../lib/format'

const MAX_LENGTH = 4000
const TYPING_EVERY_MS = 3000
const GROUP_WITHIN_MINUTES = 5

/** A message the user sent that the server has not confirmed yet; `failed` holds the reason. */
export interface Outgoing {
  clientId: string
  body: string
  createdAt: string
  failed?: string
}

interface Item {
  key: string
  senderId: string
  createdAt: string
  message?: ChatMessage
  outgoing?: Outgoing
}

/** Unsent drafts survive switching chats for the rest of the session. */
const drafts = new Map<string, string>()

interface Props {
  conversation: Conversation
  outbox: Outgoing[]
  onOutboxChange(update: (outbox: Outgoing[]) => Outgoing[]): void
}

/** One open chat: history, live updates, read receipts, typing indicator and the composer. */
export function ChatThread({ conversation, outbox, onOutboxChange }: Props) {
  const me = useMe()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { modal } = App.useApp()
  const { presenceOf, typingIn, setActiveConversation } = useChat()
  const other = conversation.with
  const firstName = other.name.split(' ')[0]
  const status = presenceOf(other.id, other.presence) ?? 'OFFLINE'
  const [draft, setDraft] = useState(() => drafts.get(conversation.id) ?? '')
  const [editing, setEditing] = useState<{ id: string; body: string } | null>(null)
  const [newBelow, setNewBelow] = useState(false)
  const scroller = useRef<HTMLDivElement>(null)
  const atBottom = useRef(true)
  const keepOffset = useRef<number | null>(null)
  const lastTyping = useRef(0)

  const history = useInfiniteQuery({
    queryKey: chatKeys.messages(conversation.id),
    queryFn: ({ pageParam }) =>
      http.get<MessagePage>(`/chat/conversations/${conversation.id}/messages${qs({ before: pageParam, limit: 50 })}`),
    initialPageParam: '',
    getNextPageParam: (page) => (page.hasMore ? page.messages[0]?.id : undefined),
    // Live events keep it current; reconnects invalidate it.
    staleTime: Infinity,
  })

  const messages = history.data ? [...history.data.pages].reverse().flatMap((page) => page.messages) : []
  const confirmed = new Set(messages.filter((m) => m.senderId === me.id).map((m) => m.clientId))
  const items: Item[] = [
    ...messages.map((m) => ({ key: `${m.senderId}:${m.clientId}`, senderId: m.senderId, createdAt: m.createdAt, message: m })),
    ...outbox
      .filter((o) => !confirmed.has(o.clientId))
      .map((o) => ({ key: `${me.id}:${o.clientId}`, senderId: me.id, createdAt: o.createdAt, outgoing: o })),
  ]
  const lastMine = messages.findLast((m) => m.senderId === me.id && !m.deleted)
  const seen = !!lastMine && !!conversation.theirLastReadAt && Date.parse(conversation.theirLastReadAt) >= Date.parse(lastMine.createdAt)
  const newestFromThem = messages.findLast((m) => m.senderId !== me.id)
  const unreadId =
    newestFromThem && (!conversation.lastReadAt || Date.parse(newestFromThem.createdAt) > Date.parse(conversation.lastReadAt))
      ? newestFromThem.id
      : undefined
  const typingNow = typingIn(conversation.id) === other.id
  const lastKey = items.at(-1)?.key
  const pageCount = history.data?.pages.length ?? 0

  useEffect(() => {
    setActiveConversation(conversation.id)
    return () => setActiveConversation(null)
  }, [conversation.id, setActiveConversation])

  // Read receipts: mark read while the chat is actually on screen.
  const { mutate: markRead } = useMutation({
    mutationFn: (messageId: string) => http.post(`/chat/conversations/${conversation.id}/read`, { messageId }),
    meta: { silent: true },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: chatKeys.conversations })
      void queryClient.invalidateQueries({ queryKey: chatKeys.unread })
    },
  })
  useEffect(() => {
    if (!unreadId) return
    const mark = () => document.visibilityState === 'visible' && markRead(unreadId)
    mark()
    document.addEventListener('visibilitychange', mark)
    return () => document.removeEventListener('visibilitychange', mark)
  }, [unreadId, markRead])

  // Scrolling: stay pinned to the newest message unless the user scrolled up to read older ones.
  useLayoutEffect(() => {
    const el = scroller.current
    if (el && keepOffset.current !== null) {
      el.scrollTop = el.scrollHeight - keepOffset.current
      keepOffset.current = null
    }
  }, [pageCount])
  useLayoutEffect(() => {
    const el = scroller.current
    if (!el || !lastKey) return
    if (atBottom.current) el.scrollTop = el.scrollHeight
    else setNewBelow(true)
  }, [lastKey])
  useLayoutEffect(() => {
    const el = scroller.current
    if (el && typingNow && atBottom.current) el.scrollTop = el.scrollHeight
  }, [typingNow])

  const onScroll = () => {
    const el = scroller.current
    if (!el) return
    atBottom.current = el.scrollHeight - el.scrollTop - el.clientHeight < 80
    if (atBottom.current) setNewBelow(false)
    if (el.scrollTop < 120 && history.hasNextPage && !history.isFetchingNextPage) {
      keepOffset.current = el.scrollHeight - el.scrollTop
      void history.fetchNextPage()
    }
  }

  const jumpToNewest = () => {
    scroller.current?.scrollTo({ top: scroller.current.scrollHeight, behavior: 'smooth' })
    setNewBelow(false)
  }

  // ---------------------------------------------------------------- sending

  const send = useMutation({
    mutationFn: (outgoing: Outgoing) =>
      http.post<ChatMessage>(`/chat/conversations/${conversation.id}/messages`, { clientId: outgoing.clientId, body: outgoing.body }),
    meta: { silent: true },
    onSuccess: (message) => {
      upsertMessage(queryClient, message, true)
      onOutboxChange((list) => list.filter((o) => o.clientId !== message.clientId))
      void queryClient.invalidateQueries({ queryKey: chatKeys.conversations })
    },
    onError: (error, outgoing) =>
      onOutboxChange((list) => list.map((o) => (o.clientId === outgoing.clientId ? { ...o, failed: errorText(error) } : o))),
  })

  const submit = () => {
    const body = draft.trim()
    if (!body) return
    const outgoing: Outgoing = { clientId: crypto.randomUUID(), body, createdAt: new Date().toISOString() }
    onOutboxChange((list) => [...list, outgoing])
    send.mutate(outgoing)
    setDraft('')
    drafts.delete(conversation.id)
    atBottom.current = true
  }

  // Retrying with the same clientId can never create a duplicate.
  const retry = (outgoing: Outgoing) => {
    onOutboxChange((list) => list.map((o) => (o.clientId === outgoing.clientId ? { ...o, failed: undefined } : o)))
    send.mutate(outgoing)
  }

  const discard = (outgoing: Outgoing) => onOutboxChange((list) => list.filter((o) => o.clientId !== outgoing.clientId))

  const onDraftChange = (value: string) => {
    setDraft(value)
    drafts.set(conversation.id, value)
    if (value.trim() && Date.now() - lastTyping.current > TYPING_EVERY_MS) {
      lastTyping.current = Date.now()
      http.post(`/chat/conversations/${conversation.id}/typing`).catch(() => {
        // typing indicators are best effort
      })
    }
  }

  const onComposerKey = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault()
      submit()
    }
  }

  // ---------------------------------------------------------------- editing

  const edit = useMutation({
    mutationFn: ({ id, body }: { id: string; body: string }) => http.patch<ChatMessage>(`/chat/messages/${id}`, { body }),
    onSuccess: (message) => {
      upsertMessage(queryClient, message, false)
      setEditing(null)
      void queryClient.invalidateQueries({ queryKey: chatKeys.conversations })
    },
  })

  const remove = useMutation({
    mutationFn: (message: ChatMessage) => http.delete(`/chat/messages/${message.id}`),
    onSuccess: (_, message) => {
      upsertMessage(queryClient, { ...message, body: null, deleted: true }, false)
      void queryClient.invalidateQueries({ queryKey: chatKeys.conversations })
    },
  })

  const saveEdit = () => {
    if (editing && editing.body.trim()) edit.mutate({ id: editing.id, body: editing.body.trim() })
  }

  const confirmDelete = (message: ChatMessage) =>
    modal.confirm({
      title: 'Delete this message?',
      content: `It will be removed for you and ${firstName}.`,
      okText: 'Delete',
      okButtonProps: { danger: true },
      onOk: () => remove.mutateAsync(message),
    })

  // ---------------------------------------------------------------- rendering

  const bubble = (item: Item, mine: boolean) => {
    const { message, outgoing } = item
    if (message && editing?.id === message.id) {
      return (
        <div className="chat-edit">
          <Input.TextArea
            autoFocus
            value={editing.body}
            maxLength={MAX_LENGTH}
            autoSize={{ minRows: 1, maxRows: 8 }}
            aria-label="Edit message"
            onChange={(e) => setEditing({ id: message.id, body: e.target.value })}
            onKeyDown={(e) => {
              if (e.key === 'Escape') setEditing(null)
              if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
                e.preventDefault()
                saveEdit()
              }
            }}
          />
          <div className="chat-edit-actions">
            <Button size="small" onClick={() => setEditing(null)}>
              Cancel
            </Button>
            <Button size="small" type="primary" loading={edit.isPending} disabled={!editing.body.trim()} onClick={saveEdit}>
              Save
            </Button>
          </div>
        </div>
      )
    }
    if (message?.deleted) return <div className="chat-bubble deleted">This message was deleted</div>
    return (
      <div className="chat-bubble-row">
        {mine && message && (
          <Dropdown
            trigger={['click']}
            menu={{
              items: [
                { key: 'edit', icon: <EditOutlined />, label: 'Edit' },
                { key: 'delete', icon: <DeleteOutlined />, label: 'Delete', danger: true },
              ],
              onClick: ({ key }) => (key === 'edit' ? setEditing({ id: message.id, body: message.body ?? '' }) : confirmDelete(message)),
            }}
          >
            <Button type="text" size="small" className="chat-msg-actions" icon={<MoreOutlined />} aria-label="Message options" />
          </Dropdown>
        )}
        <div className={`chat-bubble${outgoing ? ' pending' : ''}`} title={formatDateTime(item.createdAt)}>
          {message?.body ?? outgoing?.body}
          {message?.editedAt && <span className="chat-edited">Edited</span>}
        </div>
      </div>
    )
  }

  const deliveryState = (item: Item) => {
    if (item.outgoing?.failed) {
      return (
        <div className="chat-status failed" role="alert">
          <ExclamationCircleOutlined /> Not sent: {item.outgoing.failed}
          <Button type="link" size="small" onClick={() => retry(item.outgoing!)}>
            Retry
          </Button>
          <Button type="link" size="small" danger onClick={() => discard(item.outgoing!)}>
            Discard
          </Button>
        </div>
      )
    }
    if (item.outgoing) {
      return (
        <div className="chat-status">
          <ClockCircleOutlined /> Sending…
        </div>
      )
    }
    if (item.message && item.message.id === lastMine?.id) return <div className="chat-status">{seen ? 'Seen' : 'Sent'}</div>
    return null
  }

  return (
    <div className="chat-thread">
      <header className="chat-head">
        <Button type="text" className="chat-back" icon={<ArrowLeftOutlined />} aria-label="Back to chats" onClick={() => navigate('/chat')} />
        <PresenceAvatar name={other.name} status={status} size={40} />
        <div className="chat-head-text">
          <strong>{other.name}</strong>
          <span className="muted">
            <PresenceDot status={status} size={10} /> {PRESENCE[status].label}
            {other.email && <span className="chat-head-email"> · {other.email}</span>}
          </span>
        </div>
      </header>

      <div className="chat-messages" ref={scroller} onScroll={onScroll} role="log" aria-live="polite" aria-label={`Messages with ${other.name}`}>
        {history.isFetchingNextPage && <Spin size="small" className="chat-older" />}
        {history.isLoading ? (
          <Spin style={{ margin: 'auto' }} />
        ) : items.length === 0 ? (
          <div className="chat-start">
            <PresenceAvatar name={other.name} status={status} size={64} />
            <p>
              This is the start of your chat with <strong>{other.name}</strong>.
            </p>
          </div>
        ) : (
          items.map((item, index) => {
            const previous = items[index - 1]
            const newDay = !previous || !dayjs(previous.createdAt).isSame(item.createdAt, 'day')
            const grouped =
              !!previous &&
              !newDay &&
              previous.senderId === item.senderId &&
              dayjs(item.createdAt).diff(previous.createdAt, 'minute') < GROUP_WITHIN_MINUTES
            const mine = item.senderId === me.id
            return (
              <Fragment key={item.key}>
                {newDay && (
                  <div className="chat-day">
                    <span>{dayLabel(item.createdAt)}</span>
                  </div>
                )}
                <div className={`chat-msg${mine ? ' mine' : ''}${grouped ? ' grouped' : ''}`}>
                  {!grouped && <div className="chat-meta">{formatTime(item.createdAt)}</div>}
                  {bubble(item, mine)}
                  {mine && deliveryState(item)}
                </div>
              </Fragment>
            )
          })
        )}
        {typingNow && (
          <div className="chat-typing">
            <span className="chat-dots" aria-hidden>
              <i />
              <i />
              <i />
            </span>
            {firstName} is typing…
          </div>
        )}
      </div>

      {newBelow && (
        <Button className="chat-jump" shape="round" size="small" icon={<ArrowDownOutlined />} onClick={jumpToNewest}>
          New messages
        </Button>
      )}

      {other.active ? (
        <div className="chat-composer">
          <Input.TextArea
            value={draft}
            onChange={(e) => onDraftChange(e.target.value)}
            onKeyDown={onComposerKey}
            placeholder={`Message ${firstName}`}
            aria-label={`Message ${other.name}`}
            autoSize={{ minRows: 1, maxRows: 6 }}
            maxLength={MAX_LENGTH}
            variant="borderless"
          />
          {draft.length > MAX_LENGTH - 200 && <span className="chat-count">{MAX_LENGTH - draft.length}</span>}
          <Button type="primary" shape="circle" icon={<SendOutlined />} aria-label="Send" disabled={!draft.trim()} onClick={submit} />
        </div>
      ) : (
        <div className="chat-composer disabled">{other.name} can no longer receive messages.</div>
      )}
    </div>
  )
}
