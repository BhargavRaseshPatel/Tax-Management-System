import { createContext, useContext } from 'react'
import type { Role, User } from '../api/types'

/**
 * DEMO-ONLY session: you pick an existing user on the login page. There is no password and the backend does not
 * check it. Stage 8 replaces this with Amazon Cognito + JWT, enforced by Spring Security.
 */
export interface Session {
  user: User | null
  signIn: (user: User) => void
  signOut: () => void
}

export const SessionContext = createContext<Session | null>(null)

export function useSession(): Session {
  const session = useContext(SessionContext)
  if (!session) throw new Error('useSession must be used inside SessionProvider')
  return session
}

export const HOME_BY_ROLE: Record<Role, string> = {
  RESIDENT: '/resident',
  TAX_OFFICER: '/officer',
  ADMIN: '/admin',
}

export const ROLE_LABEL: Record<Role, string> = {
  RESIDENT: 'Resident',
  TAX_OFFICER: 'Tax Officer',
  ADMIN: 'Admin',
}
