import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { endSession, http, onSessionChange, refreshSession, startSession } from '../api/client'
import type { Me } from '../api/types'
import { AuthContext, type AuthState } from './context'

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [me, setMe] = useState<Me | null>(null)
  const [ready, setReady] = useState(false)

  useEffect(() => {
    const unsubscribe = onSessionChange((auth) => {
      setMe(auth?.user ?? null)
      if (!auth) queryClient.clear()
    })
    // Restores the session from the refresh cookie after a page reload.
    void refreshSession().finally(() => setReady(true))
    return unsubscribe
  }, [queryClient])

  const value = useMemo<AuthState>(
    () => ({
      me,
      ready,
      signIn: startSession,
      signOut: async () => {
        await http.post('/auth/logout').catch(() => undefined)
        endSession()
      },
      updateMe: setMe,
    }),
    [me, ready],
  )

  return <AuthContext value={value}>{children}</AuthContext>
}
