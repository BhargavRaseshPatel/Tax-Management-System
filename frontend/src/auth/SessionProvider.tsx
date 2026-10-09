import { useState, type ReactNode } from 'react'
import type { User } from '../api/types'
import { SessionContext, type Session } from './session'

const STORAGE_KEY = 'civictax.demoUser'

function readStoredUser(): User | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? (JSON.parse(raw) as User) : null
  } catch {
    return null
  }
}

function store(user: User | null) {
  try {
    if (user) localStorage.setItem(STORAGE_KEY, JSON.stringify(user))
    else localStorage.removeItem(STORAGE_KEY)
  } catch {
    // private mode / blocked storage: session just won't survive a reload
  }
}

export function SessionProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(readStoredUser)
  const value: Session = {
    user,
    signIn: (next) => {
      store(next)
      setUser(next)
    },
    signOut: () => {
      store(null)
      setUser(null)
    },
  }
  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>
}
