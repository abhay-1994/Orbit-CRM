import type { InfiniteData, QueryClient } from '@tanstack/react-query'
import type { ChatMessage, MessagePage } from '../api/types'

export const chatKeys = {
  all: ['chat'] as const,
  conversations: ['chat', 'conversations'] as const,
  unread: ['chat', 'unread'] as const,
  messages: (conversationId: string) => ['chat', 'messages', conversationId] as const,
}

// Timestamps carry a varying number of fraction digits, so compare them as dates; ids are time-ordered
// (UUIDv7) and break ties below a millisecond.
const byTime = (a: ChatMessage, b: ChatMessage) => Date.parse(a.createdAt) - Date.parse(b.createdAt) || a.id.localeCompare(b.id)

/**
 * Puts a message into the loaded history of its conversation: replaces it when present (edit, delete,
 * a second delivery) and, for `created`, adds it to the newest page. Unloaded conversations are left alone;
 * they fetch fresh data when opened.
 */
export function upsertMessage(queryClient: QueryClient, message: ChatMessage, created: boolean): void {
  queryClient.setQueryData<InfiniteData<MessagePage>>(chatKeys.messages(message.conversationId), (data) => {
    if (!data) return data
    let found = false
    const pages = data.pages.map((page) => ({
      ...page,
      messages: page.messages.map((m) => {
        if (m.id !== message.id) return m
        found = true
        return message
      }),
    }))
    if (!found && created) {
      // pages[0] holds the newest messages (older pages are appended as the user scrolls up).
      pages[0] = { ...pages[0], messages: [...pages[0].messages, message].sort(byTime) }
    }
    return { ...data, pages }
  })
}
