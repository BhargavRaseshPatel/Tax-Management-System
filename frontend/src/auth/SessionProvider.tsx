import { useEffect, useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { api, ApiError, onUnauthorized, setAuthToken } from '../api/client'
import type { SessionUser } from '../api/types'
import { cognitoEnabled, signOutUrl } from './cognito'
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
    if (cognitoEnabled) location.assign(signOutUrl())
  }

  const begin = (token: string, next: SessionUser) => {
    store({ token, user: next })
    setAuthToken(token)
    queryClient.clear()
    setUser(next)
    return next
  }

  // An expired or revoked token (401 from any call) ends the session.
  useEffect(() => onUnauthorized(signOut))

  const value: Session = {
    user,
    signOut,
    signIn: async (email) => {
      const response = await api<{ accessToken: string; user: SessionUser }>('/api/dev/token', { method: 'POST', body: { email } })
      return begin(response.accessToken, response.user)
    },
    signInWithToken: async (token) => {
      try {
        return begin(token, await api<SessionUser>('/api/me', { token }))
      } catch (e) {
        // Cognito knows this person, but the application does not (or the account is deactivated).
        if (e instanceof ApiError && e.status === 401) {
          throw new Error('This account is not registered in CivicTax, or has been deactivated. Ask an administrator to add your email.')
        }
        throw e
      }
    },
  }
  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>
}
