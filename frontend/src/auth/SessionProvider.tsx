import { useEffect, useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { api, onUnauthorized, setAuthToken } from '../api/client'
import type { SessionUser } from '../api/types'
import { SessionContext, type Session } from './session'

/** sessionStorage: survives a reload, gone when the tab closes, never shared across tabs (unlike localStorage). */
const STORAGE_KEY = 'civictax.session'

interface Stored {
  token: string
  user: SessionUser
}

function readStored(): Stored | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    return raw ? (JSON.parse(raw) as Stored) : null
  } catch {
    return null
  }
}

function store(value: Stored | null) {
  try {
    if (value) sessionStorage.setItem(STORAGE_KEY, JSON.stringify(value))
    else sessionStorage.removeItem(STORAGE_KEY)
  } catch {
    // blocked storage: the session just won't survive a reload
  }
}

export function SessionProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [user, setUser] = useState<SessionUser | null>(() => {
    const stored = readStored()
    setAuthToken(stored?.token ?? null) // before the first query runs
    return stored?.user ?? null
  })

  const signOut = () => {
    store(null)
    setAuthToken(null)
    queryClient.clear() // never show one user's cached data to the next
    setUser(null)
  }

  // An expired or revoked token (401 from any call) ends the session.
  useEffect(() => onUnauthorized(signOut))

  const value: Session = {
    user,
    signOut,
    signIn: async (email) => {
      // Local dev issuer. With Cognito this becomes the hosted-login redirect + token exchange.
      const response = await api<{ accessToken: string; user: SessionUser }>('/api/dev/token', { method: 'POST', body: { email } })
      const next = { token: response.accessToken, user: response.user }
      store(next)
      setAuthToken(next.token)
      queryClient.clear()
      setUser(next.user)
      return next.user
    },
  }
  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>
}
