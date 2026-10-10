import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { Problem } from './types'

export class ApiError extends Error {
  readonly status: number
  readonly problem: Problem

  constructor(status: number, problem: Problem) {
    super(problem.detail ?? problem.title ?? `Request failed (HTTP ${status})`)
    this.status = status
    this.problem = problem
  }
}

let authToken: string | null = null
let unauthorizedHandler: (() => void) | null = null

export function setAuthToken(token: string | null) {
  authToken = token
}

/** Registers what to do when the API rejects the token; returns the unregister function. */
export function onUnauthorized(handler: () => void) {
  unauthorizedHandler = handler
  return () => { unauthorizedHandler = null }
}

/** The only place that talks HTTP: attaches the bearer token and turns every failure into an ApiError. */
async function send(path: string, options: { method?: string; body?: unknown; token?: string } = {}): Promise<Response> {
  let response: Response
  const headers: Record<string, string> = {}
  if (options.body !== undefined) headers['Content-Type'] = 'application/json'
  const token = options.token ?? authToken // an explicit token is used while signing in, before a session exists
  if (token) headers.Authorization = `Bearer ${token}`
  try {
    response = await fetch(path, {
      method: options.method ?? 'GET',
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
    })
  } catch {
    throw new ApiError(0, { detail: 'Cannot reach the API. Is the Spring Boot backend running on port 8080?' })
  }
  if (response.status === 401 && authToken) {
    unauthorizedHandler?.()
    throw new ApiError(401, { detail: 'Your session has expired. Please sign in again.' })
  }
  if (response.status === 403) throw new ApiError(403, { detail: 'You do not have access to this record.' })
  if (!response.ok) {
    const data = await response.json().catch(() => ({}))
    // The Vite proxy answers 500 with an empty body when the backend is down.
    const problem: Problem = data && typeof data === 'object' && 'status' in data ? data
      : { detail: `Request failed (HTTP ${response.status}). Is the backend running?` }
    throw new ApiError(response.status, problem)
  }
  return response
}

/** JSON request/response. */
export async function api<T>(path: string, options: { method?: string; body?: unknown; token?: string } = {}): Promise<T> {
  const response = await send(path, options)
  return response.status === 204 ? (undefined as T) : ((await response.json()) as T)
}

/**
 * Downloads a protected file. A plain link can't send the bearer token, so the file is fetched with it and then
 * handed to the browser's save dialog. The name comes from the server's Content-Disposition header.
 */
export async function downloadFile(path: string, fallbackName: string) {
  const response = await send(path)
  const name = /filename="?([^";]+)"?/.exec(response.headers.get('Content-Disposition') ?? '')?.[1] ?? fallbackName
  const url = URL.createObjectURL(await response.blob())
  const link = document.createElement('a')
  link.href = url
  link.download = name
  link.click()
  URL.revokeObjectURL(url)
}

/** Query options for a GET. The URL path is the cache key; null skips. allow404 resolves "not found" to null. */
export function apiQuery<T>(path: string | null, allow404 = false) {
  return {
    queryKey: [path],
    enabled: path !== null,
    queryFn: async (): Promise<T> => {
      try {
        return await api<T>(path!)
      } catch (e) {
        if (allow404 && e instanceof ApiError && e.status === 404) return null as T
        throw e
      }
    },
  }
}

/** Cached GET. With allow404, type T as `X | null`. */
export function useApi<T>(path: string | null, options: { allow404?: boolean } = {}) {
  return useQuery(apiQuery<T>(path, options.allow404))
}

/** POST/PUT/DELETE. On success every cached GET is refetched. */
export function useApiMutation<T = unknown>() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: { path: string; method?: 'POST' | 'PUT' | 'DELETE'; body?: unknown }) =>
      api<T>(request.path, { method: request.method ?? 'POST', body: request.body }),
    // ponytail: refetches everything; scope invalidation by key if the app grows past a demo
    onSuccess: () => queryClient.invalidateQueries(),
  })
}

/** Field-level validation message from a 400 ProblemDetail, for TextField helperText. */
export function fieldError(error: unknown, field: string): string | undefined {
  return error instanceof ApiError ? error.problem.errors?.[field] : undefined
}
