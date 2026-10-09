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

/** The only place that talks HTTP. Stage 8 adds the Cognito bearer token here. */
export async function api<T>(path: string, options: { method?: string; body?: unknown } = {}): Promise<T> {
  let response: Response
  try {
    response = await fetch(path, {
      method: options.method ?? 'GET',
      headers: options.body === undefined ? undefined : { 'Content-Type': 'application/json' },
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
    })
  } catch {
    throw new ApiError(0, { detail: 'Cannot reach the API. Is the Spring Boot backend running on port 8080?' })
  }
  if (response.status === 204) return undefined as T
  const data = await response.json().catch(() => ({}))
  if (!response.ok) {
    // The Vite proxy answers 500 with an empty body when the backend is down.
    const problem: Problem = data && typeof data === 'object' && 'status' in data ? data
      : { detail: `Request failed (HTTP ${response.status}). Is the backend running?` }
    throw new ApiError(response.status, problem)
  }
  return data as T
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
