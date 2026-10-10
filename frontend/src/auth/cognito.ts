import { challenge, randomString } from './pkce'

/**
 * Amazon Cognito hosted sign-in (OAuth 2.0 authorization code + PKCE; a browser app holds no client secret).
 * Active only when both values are set at build time; otherwise the app uses the backend's local dev sign-in.
 * Both values are public identifiers, not secrets.
 */
const domain = import.meta.env.VITE_COGNITO_DOMAIN as string | undefined // https://<prefix>.auth.<region>.amazoncognito.com
const clientId = import.meta.env.VITE_COGNITO_CLIENT_ID as string | undefined

export const cognitoEnabled = Boolean(domain && clientId)

const PENDING_KEY = 'civictax.pkce'
const redirectUri = () => `${location.origin}/login`

/** Sends the browser to Cognito's sign-in page. */
export async function startSignIn() {
  const verifier = randomString()
  const state = randomString()
  sessionStorage.setItem(PENDING_KEY, JSON.stringify({ verifier, state }))
  const query = new URLSearchParams({
    response_type: 'code',
    client_id: clientId!,
    redirect_uri: redirectUri(),
    scope: 'openid email',
    state,
    code_challenge: await challenge(verifier),
    code_challenge_method: 'S256',
  })
  location.assign(`${domain}/oauth2/authorize?${query}`)
}

/**
 * Back from Cognito with ?code&state: checks the state we sent (CSRF protection) and swaps the one-time code for
 * tokens. Returns the ID token, which carries the email the backend matches to a user.
 */
// ponytail: no refresh token use; the session lasts as long as the ID token (60 min), then sign in again.
export async function finishSignIn(code: string, state: string | null): Promise<string> {
  const pending = JSON.parse(sessionStorage.getItem(PENDING_KEY) ?? 'null') as { verifier: string; state: string } | null
  sessionStorage.removeItem(PENDING_KEY)
  if (!pending || pending.state !== state) throw new Error('Sign-in could not be verified. Please try again.')
  const response = await fetch(`${domain}/oauth2/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'authorization_code',
      client_id: clientId!,
      code,
      redirect_uri: redirectUri(),
      code_verifier: pending.verifier,
    }),
  })
  if (!response.ok) throw new Error('Sign-in failed. Please try again.')
  return ((await response.json()) as { id_token: string }).id_token
}

/** Ends the Cognito session too, so "sign out" really signs out. */
export const signOutUrl = () =>
  `${domain}/logout?${new URLSearchParams({ client_id: clientId!, logout_uri: redirectUri() })}`
