import { useEffect, useState } from 'react'
import { Navigate, useNavigate, useSearchParams } from 'react-router'
import { Alert, Avatar, Box, Button, Chip, CircularProgress, Divider, List, ListItemAvatar, ListItemButton, ListItemText, Paper, Stack, Typography } from '@mui/material'
import LockOutlinedIcon from '@mui/icons-material/LockOutlined'
import { useApi } from '../api/client'
import type { SessionUser } from '../api/types'
import { ROLES } from '../api/types'
import { cognitoEnabled, finishSignIn, startSignIn } from '../auth/cognito'
import { HOME_BY_ROLE, ROLE_LABEL, useSession } from '../auth/session'
import { Logo } from '../components/AppShell'
import { initials } from '../components/format'
import { ErrorAlert, QueryState, StatusChip } from '../components/ui'
import { tokens } from '../theme'

/** A sign-in code can be exchanged only once; React's dev-mode double effect must not send it twice. */
let exchange: { code: string; token: Promise<string> } | null = null
function exchangeOnce(code: string, state: string | null) {
  if (exchange?.code !== code) exchange = { code, token: finishSignIn(code, state) }
  return exchange.token
}

/**
 * Deployed (Cognito settings present at build time): a button to Amazon Cognito's hosted sign-in, and the handler
 * for the ?code it returns with. Local: choose an active user and the backend's dev issuer returns a token.
 */
export function LoginPage() {
  const { user, signIn, signInWithToken } = useSession()
  const navigate = useNavigate()
  const users = useApi<SessionUser[]>(cognitoEnabled ? null : '/api/dev/users')
  const [error, setError] = useState<unknown>(null)
  const [params] = useSearchParams()
  const code = cognitoEnabled ? params.get('code') : null
  const state = params.get('state')

  useEffect(() => {
    if (!code) return
    exchangeOnce(code, state)
      .then(signInWithToken)
      .then((signedIn) => navigate(HOME_BY_ROLE[signedIn.role], { replace: true }))
      .catch((e: unknown) => {
        setError(e)
        navigate('/login', { replace: true }) // drop the used code from the address bar
      })
    // signInWithToken and navigate are stable for this purpose; re-running is keyed on the code alone
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [code])

  if (user) return <Navigate to={HOME_BY_ROLE[user.role]} replace />

  if (cognitoEnabled) {
    return (
      <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center', p: 2, background: `linear-gradient(160deg, ${tokens.canvas} 0%, #e3ebf7 100%)` }}>
        <Paper sx={{ width: '100%', maxWidth: 440, p: { xs: 3, sm: 4 } }}>
          <Logo />
          <Typography variant="h1" sx={{ mt: 3, fontSize: '1.6rem' }}>Sign in</Typography>
          <Typography sx={{ color: 'text.secondary', mt: 0.5, mb: 3 }}>Use your CivicTax account. Accounts are created by an administrator.</Typography>
          {code ? (
            <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}><CircularProgress size={22} /><Typography>Signing you in…</Typography></Stack>
          ) : (
            <Button fullWidth variant="contained" size="large" startIcon={<LockOutlinedIcon />} onClick={() => { setError(null); startSignIn().catch(setError) }}>
              Sign in
            </Button>
          )}
          <ErrorAlert error={error} />
          <Typography variant="caption" sx={{ display: 'block', color: 'text.secondary', mt: 3 }}>
            All names, properties, and tax rates are fictional. Not affiliated with any municipality.
          </Typography>
        </Paper>
      </Box>
    )
  }

  const active = users.data ?? []
  const choose = (u: SessionUser) => {
    setError(null)
    signIn(u.email).then((signedIn) => navigate(HOME_BY_ROLE[signedIn.role]), setError)
  }

  return (
    <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center', p: 2, background: `linear-gradient(160deg, ${tokens.canvas} 0%, #e3ebf7 100%)` }}>
      <Paper sx={{ width: '100%', maxWidth: 520, p: { xs: 3, sm: 4 } }}>
        <Logo />
        <Typography variant="h1" sx={{ mt: 3, fontSize: '1.6rem' }}>Sign in</Typography>
        <Typography sx={{ color: 'text.secondary', mt: 0.5 }}>Choose a demo account to explore the portal as that role.</Typography>

        <Alert severity="info" icon={<LockOutlinedIcon fontSize="inherit" />} sx={{ mt: 2 }}>
          Local sign-in issues a real signed token; the server enforces roles and data ownership on every request.
          Deployed environments use Amazon Cognito instead.
        </Alert>

        <Box sx={{ mt: 2 }}>
          <QueryState query={users}>
            {active.length === 0 ? (
              <Alert severity="warning">
                No users yet. Load demo data with <code>scripts/seed-demo-data.sh</code> (see README).
              </Alert>
            ) : (
              ROLES.map((role) => {
                const ofRole = active.filter((u) => u.role === role)
                if (ofRole.length === 0) return null
                return (
                  <Box key={role} sx={{ mt: 1.5 }}>
                    <Divider textAlign="left"><Chip label={ROLE_LABEL[role]} size="small" /></Divider>
                    <List dense>
                      {ofRole.slice(0, 4).map((u) => (
                        <ListItemButton key={u.id} sx={{ borderRadius: 2 }} onClick={() => choose(u)}>
                          <ListItemAvatar><Avatar sx={{ bgcolor: tokens.navy, width: 34, height: 34, fontSize: '0.8rem' }}>{initials(u.fullName)}</Avatar></ListItemAvatar>
                          <ListItemText primary={u.fullName} secondary={u.email} slotProps={{ secondary: { sx: { fontFamily: tokens.mono, fontSize: '0.75rem' } } }} />
                          <StatusChip value={u.role} />
                        </ListItemButton>
                      ))}
                    </List>
                  </Box>
                )
              })
            )}
          </QueryState>
          <ErrorAlert error={error} />
        </Box>
        <Stack sx={{ mt: 2 }}>
          <Typography variant="caption" sx={{ color: 'text.secondary' }}>
            All names, properties, and tax rates are fictional. Not affiliated with any municipality.
          </Typography>
        </Stack>
      </Paper>
    </Box>
  )
}
