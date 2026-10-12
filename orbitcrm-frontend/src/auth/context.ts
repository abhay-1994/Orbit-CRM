import { createContext, useContext } from 'react'
import type { AuthResponse, Me } from '../api/types'

export interface AuthState {
  /** The signed-in user, or null. */
  me: Me | null
  /** False until the initial session check (refresh cookie) has finished. */
  ready: boolean
  signIn(auth: AuthResponse): void
  signOut(): Promise<void>
  updateMe(me: Me): void
}

export const AuthContext = createContext<AuthState | null>(null)

export function useAuth(): AuthState {
  const auth = useContext(AuthContext)
  if (!auth) throw new Error('useAuth must be used inside <AuthProvider>')
  return auth
}

/** The signed-in user; only for components rendered behind <RequireAuth>. */
export function useMe(): Me {
  const { me } = useAuth()
  if (!me) throw new Error('No signed-in user')
  return me
}
