/** PKCE (RFC 7636) helpers, using the browser's built-in Web Crypto. */

const base64Url = (bytes: Uint8Array) =>
  btoa(String.fromCharCode(...bytes)).replaceAll('+', '-').replaceAll('/', '_').replaceAll('=', '')

/** 43 unguessable URL-safe characters: used for the code verifier and the state value. */
export const randomString = () => base64Url(crypto.getRandomValues(new Uint8Array(32)))

/** code_challenge = BASE64URL(SHA-256(code_verifier)) */
export async function challenge(verifier: string) {
  return base64Url(new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier))))
}
