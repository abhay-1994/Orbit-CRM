import { CloseOutlined, FormOutlined, SearchOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, Badge, Button, Empty, Input, Skeleton, Tooltip, Typography, type InputRef } from 'antd'
import { useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { http, qs } from '../../api/client'
import type { ChatPerson, Conversation } from '../../api/types'
import { useMe } from '../../auth/context'
import { chatKeys } from '../../chat/cache'
import { useChat } from '../../chat/context'
import { PresenceAvatar } from '../../chat/presence'
import { compactTime } from '../../lib/format'
import { useDebounced } from '../../lib/hooks'

interface Props {
  conversations?: Conversation[]
  loading: boolean
  activeId?: string
}

/** The left pane: recent chats, or colleagues matching the search to start a new one. */
export function ConversationList({ conversations, loading, activeId }: Props) {
  const me = useMe()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { connected, presenceOf, typingIn } = useChat()
  const [query, setQuery] = useState('')
  const [picking, setPicking] = useState(false)
  const [permission, setPermission] = useState(() => ('Notification' in window ? Notification.permission : 'denied'))
  const search = useRef<InputRef>(null)
  const term = useDebounced(query.trim())
  const findingPeople = picking || term !== ''

  const people = useQuery({
    queryKey: [...chatKeys.all, 'people', term],
    queryFn: () => http.get<ChatPerson[]>(`/chat/people${qs({ q: term, limit: 20 })}`),
    enabled: findingPeople,
  })

  const start = useMutation({
    mutationFn: (userId: string) => http.post<Conversation>('/chat/conversations', { userId }),
    onSuccess: (conversation) => {
      void queryClient.invalidateQueries({ queryKey: chatKeys.conversations })
      closeSearch()
      navigate(`/chat/${conversation.id}`)
    },
  })

  const closeSearch = () => {
    setQuery('')
    setPicking(false)
  }

  const preview = (c: Conversation) => {
    if (typingIn(c.id) === c.with.id) return <em className="chat-typing-text">typing…</em>
    const last = c.lastMessage
    if (!last) return 'No messages yet'
    const text = last.deleted ? 'Message deleted' : last.body
    return last.senderId === me.id ? `You: ${text}` : text
  }

  return (
    <aside className="chat-list">
      <div className="chat-list-head">
        <h1>Chat</h1>
        <Tooltip title="New chat">
          <Button
            type="text"
            shape="circle"
            icon={<FormOutlined />}
            aria-label="New chat"
            onClick={() => {
              setPicking(true)
              search.current?.focus()
            }}
          />
        </Tooltip>
      </div>
      <div className="chat-search">
        <Input
          ref={search}
          prefix={<SearchOutlined className="muted" />}
          suffix={findingPeople && <CloseOutlined className="muted" onClick={closeSearch} aria-label="Close search" />}
          placeholder="Search people"
          aria-label="Search people"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={(e) => e.key === 'Escape' && closeSearch()}
        />
      </div>
      {!connected && <Alert type="warning" showIcon banner title="Reconnecting… messages will catch up." />}
      {permission === 'default' && !findingPeople && (
        <button type="button" className="chat-hint" onClick={() => void Notification.requestPermission().then(setPermission)}>
          Turn on desktop notifications for new messages
        </button>
      )}

      <div className="chat-list-body">
        {findingPeople ? (
          people.isLoading ? (
            <Skeleton active avatar paragraph={{ rows: 1 }} style={{ padding: 12 }} />
          ) : !people.data?.length ? (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No colleagues found" />
          ) : (
            people.data.map((person) => (
              <button
                key={person.id}
                type="button"
                className="chat-row"
                disabled={start.isPending}
                onClick={() => start.mutate(person.id)}
              >
                <PresenceAvatar name={person.name} status={presenceOf(person.id, person.presence)} />
                <span className="chat-row-main">
                  <strong>{person.name}</strong>
                  <Typography.Text type="secondary" ellipsis>
                    {person.email}
                  </Typography.Text>
                </span>
              </button>
            ))
          )
        ) : loading ? (
          <Skeleton active avatar paragraph={{ rows: 1 }} style={{ padding: 12 }} />
        ) : !conversations?.length ? (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No chats yet. Search for a colleague to start one." />
        ) : (
          conversations.map((c) => (
            <button
              key={c.id}
              type="button"
              className={`chat-row${c.id === activeId ? ' active' : ''}${c.unreadCount ? ' unread' : ''}`}
              onClick={() => navigate(`/chat/${c.id}`)}
            >
              <PresenceAvatar name={c.with.name} status={presenceOf(c.with.id, c.with.presence)} />
              <span className="chat-row-main">
                <span className="chat-row-line">
                  <strong>{c.with.name}</strong>
                  <time>{compactTime(c.lastMessage?.createdAt ?? c.createdAt)}</time>
                </span>
                <span className="chat-row-line">
                  <span className="chat-preview">{preview(c)}</span>
                  <Badge count={c.unreadCount} size="small" />
                </span>
              </span>
            </button>
          ))
        )}
      </div>
    </aside>
  )
}
