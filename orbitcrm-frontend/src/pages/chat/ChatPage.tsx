import { MessageOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { Empty, Spin } from 'antd'
import { useState } from 'react'
import { useParams } from 'react-router'
import { http } from '../../api/client'
import type { Conversation } from '../../api/types'
import { chatKeys } from '../../chat/cache'
import { ChatThread, type Outgoing } from './ChatThread'
import { ConversationList } from './ConversationList'

/**
 * Teams-style chat: conversations on the left, the open chat on the right (one pane at a time on phones).
 * Unsent messages are kept per conversation here, so switching chats never loses a failed message.
 */
export function ChatPage() {
  const { conversationId } = useParams()
  const [outbox, setOutbox] = useState<Record<string, Outgoing[]>>({})

  // Polled as a safety net for presence and previews; live events keep it fresh in between.
  const conversations = useQuery({
    queryKey: chatKeys.conversations,
    queryFn: () => http.get<Conversation[]>('/chat/conversations'),
    refetchInterval: 60_000,
  })
  const listed = conversations.data?.find((c) => c.id === conversationId)
  // Deep links to chats beyond the list's most recent 100.
  const single = useQuery({
    queryKey: [...chatKeys.all, 'conversation', conversationId],
    queryFn: () => http.get<Conversation>(`/chat/conversations/${conversationId}`),
    enabled: !!conversationId && conversations.isSuccess && !listed,
  })
  const conversation = listed ?? single.data

  return (
    <div className={`chat${conversationId ? ' chat-open' : ''}`}>
      <ConversationList conversations={conversations.data} loading={conversations.isLoading} activeId={conversationId} />
      <section className="chat-pane">
        {conversation ? (
          <ChatThread
            key={conversation.id}
            conversation={conversation}
            outbox={outbox[conversation.id] ?? []}
            onOutboxChange={(update) => setOutbox((all) => ({ ...all, [conversation.id]: update(all[conversation.id] ?? []) }))}
          />
        ) : conversationId && !single.isError ? (
          <Spin style={{ margin: 'auto' }} />
        ) : (
          <Empty
            style={{ margin: 'auto' }}
            image={<MessageOutlined className="chat-empty-icon" />}
            description={conversationId ? 'This conversation does not exist.' : 'Pick a chat, or search for a colleague to start one.'}
          />
        )}
      </section>
    </div>
  )
}
