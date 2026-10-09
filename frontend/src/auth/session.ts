import { createContext, useContext } from 'react'
import type { Role, SessionUser } from '../api/types'

/**
 * Signed-in user + bearer token. Locally the token comes from the backend's dev issuer (DEV_AUTH=true); when
 * deployed, Amazon Cognito's hosted login issues it instead. Either way the backend verifies every request.
 */
export interface Session {
  user: SessionUser | null
  signIn: (email: string) => Promise<SessionUser>
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
