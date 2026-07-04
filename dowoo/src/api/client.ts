const API_BASE = import.meta.env.VITE_API_BASE_URL ?? ''

interface ApiEnvelope<T> {
  status: number
  data: T | null
  message: string
  error: { code: string; details: string | null } | null
}

export class ApiRequestError extends Error {
  code: string
  constructor(code: string, message: string) {
    super(message)
    this.name = 'ApiRequestError'
    this.code = code
  }
}

let accessToken: string | null = null
let onUnauthorized: (() => void) | null = null

// refresh/logout는 쿠키만으로 인증되는 요청이라 서버가 이 헤더를 CSRF 방어로 요구한다.
// 단순 폼 기반 CSRF는 커스텀 헤더를 붙일 수 없고, 다른 오리진의 fetch/XHR은 이 헤더 때문에
// CORS preflight를 거치게 되어 허용되지 않은 오리진이면 브라우저가 요청을 막는다.
const CSRF_HEADER_NAME = 'X-Requested-With'
const CSRF_HEADER_VALUE = 'XMLHttpRequest'

export function setAccessToken(token: string | null) {
  accessToken = token
}

export function getAccessToken(): string | null {
  return accessToken
}

export function setUnauthorizedHandler(handler: (() => void) | null) {
  onUnauthorized = handler
}

export function notifyUnauthorized() {
  onUnauthorized?.()
}

export async function refreshAccessToken(): Promise<string | null> {
  try {
    const response = await fetch(`${API_BASE}/api/v1/auth/refresh`, {
      method: 'POST',
      credentials: 'include',
      headers: { [CSRF_HEADER_NAME]: CSRF_HEADER_VALUE },
    })
    const body = (await response.json().catch(() => null)) as ApiEnvelope<{ accessToken: string }> | null
    if (!response.ok || !body?.data) return null
    accessToken = body.data.accessToken
    return accessToken
  } catch {
    return null
  }
}

async function rawRequest(path: string, init?: RequestInit): Promise<Response> {
  return fetch(`${API_BASE}${path}`, {
    ...init,
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      [CSRF_HEADER_NAME]: CSRF_HEADER_VALUE,
      ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
      ...(init?.headers ?? {}),
    },
  })
}

const AUTH_PATH_PREFIX = '/api/v1/auth/'

async function request<T>(path: string, init?: RequestInit, isRetry = false): Promise<T> {
  const response = await rawRequest(path, init)

  if (response.status === 401 && !isRetry && !path.startsWith(AUTH_PATH_PREFIX)) {
    const newToken = await refreshAccessToken()
    if (newToken) {
      return request<T>(path, init, true)
    }
    onUnauthorized?.()
    throw new ApiRequestError('UNAUTHORIZED', '로그인이 필요합니다.')
  }

  const body = (await response.json().catch(() => null)) as ApiEnvelope<T> | null

  if (!response.ok || !body || body.error) {
    throw new ApiRequestError(
      body?.error?.code ?? 'UNKNOWN',
      body?.message ?? `요청에 실패했습니다. (${response.status})`
    )
  }

  return body.data as T
}

export const apiGet = <T,>(path: string) => request<T>(path)

export const apiPost = <T,>(path: string, body?: unknown) =>
  request<T>(path, { method: 'POST', body: body !== undefined ? JSON.stringify(body) : undefined })

export const apiPatch = <T,>(path: string, body?: unknown) =>
  request<T>(path, { method: 'PATCH', body: body !== undefined ? JSON.stringify(body) : undefined })

export const apiPut = <T,>(path: string, body: unknown) =>
  request<T>(path, { method: 'PUT', body: JSON.stringify(body) })

export const apiDelete = (path: string) => request<void>(path, { method: 'DELETE' })

function parseContentDispositionFilename(header: string | null): string | null {
  if (!header) return null
  const match = /filename\*=UTF-8''([^;]+)/i.exec(header)
  if (!match) return null
  try {
    return decodeURIComponent(match[1])
  } catch {
    return null
  }
}

// 다운로드는 JSON 응답이 아니라 파일(Blob)이라 request()의 JSON 파싱 경로를 탈 수 없다 -
// 401 재시도 등 인증 로직은 동일하게 유지하면서 응답만 Blob으로 받는 별도 경로가 필요하다.
export async function apiDownload(path: string, isRetry = false): Promise<{ blob: Blob; filename: string | null }> {
  const response = await rawRequest(path)

  if (response.status === 401 && !isRetry) {
    const newToken = await refreshAccessToken()
    if (newToken) {
      return apiDownload(path, true)
    }
    onUnauthorized?.()
    throw new ApiRequestError('UNAUTHORIZED', '로그인이 필요합니다.')
  }

  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as ApiEnvelope<never> | null
    throw new ApiRequestError(
      body?.error?.code ?? 'UNKNOWN',
      body?.message ?? `요청에 실패했습니다. (${response.status})`
    )
  }

  return {
    blob: await response.blob(),
    filename: parseContentDispositionFilename(response.headers.get('Content-Disposition')),
  }
}

export { API_BASE }
