import { createContext, useContext } from 'react'
import type { PresenceStatus } from '../api/types'

export interface ChatState {
  /** True while the live stream is open. */
  connected: boolean
  /** The signed-in user's effective presence. */
  myStatus: PresenceStatus
  chooseStatus(status: PresenceStatus): Promise<void>
  /** Live presence of a user; `fallback` (from the last fetch) until an update arrives. */
  presenceOf(userId: string, fallback?: PresenceStatus): PresenceStatus | undefined
  /** The user typing in a conversation right now, if any. */
  typingIn(conversationId: string): string | undefined
  /** The conversation on screen; its messages need no notification. */
  setActiveConversation(conversationId: string | null): void
}

export const ChatContext = createContext<ChatState | null>(null)

export function useChat(): ChatState {
  const chat = useContext(ChatContext)
  if (!chat) throw new Error('useChat must be used inside <ChatProvider>')
  return chat
}
