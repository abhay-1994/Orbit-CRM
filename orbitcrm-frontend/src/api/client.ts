import type { AuthResponse, FieldErrorDetail } from './types'

const BASE = '/api/v1'

/** Endpoints that must never trigger a token refresh and retry. */
const NO_RETRY = /^\/auth\/(login|refresh|register|logout|password\/(forgot|reset)|invitations)/

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly details: FieldErrorDetail[]

  constructor(status: number, code: string, message: string, details: FieldErrorDetail[] = []) {
    super(message)
    this.status = status
    this.code = code
    this.details = details
  }
}

// ---------------------------------------------------------------- session

// The access token lives only in memory; the refresh token is an HttpOnly cookie the browser sends
// to /api/v1/auth, so page scripts can never read it.
let accessToken: string | null = null
let refreshInFlight: Promise<AuthResponse | null> | null = null
const listeners = new Set<(auth: AuthResponse | null) => void>()

export function onSessionChange(listener: (auth: AuthResponse | null) => void): () => void {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

export function startSession(auth: AuthResponse): void {
  accessToken = auth.accessToken
  listeners.forEach((listener) => listener(auth))
}

export function endSession(): void {
  accessToken = null
  listeners.forEach((listener) => listener(null))
}

/** Exchanges the refresh cookie for a new access token. Concurrent callers share one request. */
export function refreshSession(): Promise<AuthResponse | null> {
  refreshInFlight ??= fetch(`${BASE}/auth/refresh`, {
    method: 'POST',
    headers: { 'X-Requested-With': 'fetch' },
  })
    .then(async (res) => (res.ok ? ((await res.json()).data as AuthResponse) : null))
    .catch(() => null)
    .then((auth) => {
      if (auth) startSession(auth)
      else endSession()
      return auth
    })
    .finally(() => {
      refreshInFlight = null
    })
  return refreshInFlight
}

// ---------------------------------------------------------------- requests

interface SendOptions {
  body?: unknown
  form?: FormData
  signal?: AbortSignal
  keepalive?: boolean
}

function send(method: string, path: string, { body, form, signal, keepalive }: SendOptions): Promise<Response> {
  const headers: Record<string, string> = { 'X-Requested-With': 'fetch' }
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`
  let payload: BodyInit | undefined = form
  if (!form && body !== undefined) {
    headers['Content-Type'] = 'application/json'
    payload = JSON.stringify(body)
  }
  return fetch(BASE + path, { method, headers, body: payload, signal, keepalive })
}

/**
 * Fire-and-forget request that still goes out while the page is closing (fetch keepalive).
 * Skipped once signed out: without a token it could only fail.
 */
export function sendOnExit(method: string, path: string): void {
  if (!accessToken) return
  send(method, path, { keepalive: true }).catch(() => {
    // nothing to do: the page is going away
  })
}

async function toError(res: Response): Promise<ApiError> {
  try {
    const { error } = await res.json()
    if (error) return new ApiError(res.status, error.code, error.message, error.details ?? [])
  } catch {
    // not a JSON error body
  }
  return new ApiError(res.status, `HTTP_${res.status}`, 'The request failed. Please try again.')
}

async function call(method: string, path: string, options: SendOptions = {}): Promise<Response> {
  let res = await send(method, path, options)
  if (res.status === 401 && accessToken && !NO_RETRY.test(path) && (await refreshSession())) {
    res = await send(method, path, options)
  }
  if (!res.ok) throw await toError(res)
  return res
}

async function json<T>(method: string, path: string, body?: unknown): Promise<T> {
  const res = await call(method, path, { body })
  return res.status === 204 ? (undefined as T) : ((await res.json()).data as T)
}

export const http = {
  get: <T>(path: string) => json<T>('GET', path),
  post: <T>(path: string, body: unknown = {}) => json<T>('POST', path, body),
  put: <T>(path: string, body: unknown) => json<T>('PUT', path, body),
  patch: <T>(path: string, body: unknown) => json<T>('PATCH', path, body),
  delete: <T>(path: string) => json<T>('DELETE', path),
}

/**
 * Opens a long-lived server-sent event stream. A plain EventSource cannot send the Authorization header,
 * so this uses fetch (with the usual refresh-and-retry on 401) and hands back the streaming response.
 */
export function openStream(path: string, signal: AbortSignal): Promise<Response> {
  return call('GET', path, { signal })
}

export async function upload<T>(path: string, file: File): Promise<T> {
  const form = new FormData()
  form.append('file', file)
  const res = await call('POST', path, { form })
  return (await res.json()).data as T
}

/** Downloads a file response (CSV export, attachment) and saves it under the server's file name. */
export async function download(path: string, body?: unknown): Promise<void> {
  const res = await call(body === undefined ? 'GET' : 'POST', path, { body })
  const disposition = res.headers.get('Content-Disposition') ?? ''
  const encoded = /filename\*=UTF-8''([^;]+)/i.exec(disposition)?.[1]
  const name = encoded ? decodeURIComponent(encoded) : /filename="?([^";]+)"?/i.exec(disposition)?.[1]
  const url = URL.createObjectURL(await res.blob())
  const link = document.createElement('a')
  link.href = url
  link.download = name ?? 'download'
  link.click()
  URL.revokeObjectURL(url)
}

/** Builds a query string, skipping empty values and repeating array values. */
export function qs(params: Record<string, string | number | boolean | string[] | null | undefined>): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value === null || value === undefined || value === '') continue
    if (Array.isArray(value)) value.forEach((item) => search.append(key, item))
    else search.append(key, String(value))
  }
  const text = search.toString()
  return text ? `?${text}` : ''
}
