import assert from 'node:assert/strict'
import { test } from 'node:test'
import { challenge, randomString } from './pkce.ts'

test('challenge matches the RFC 7636 Appendix B example', async () => {
  assert.equal(await challenge('dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk'), 'E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM')
})

test('random strings are URL-safe, long enough, and different each time', () => {
  const a = randomString()
  assert.match(a, /^[A-Za-z0-9_-]{43}$/)
  assert.notEqual(a, randomString())
})
