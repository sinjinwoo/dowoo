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

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...(init?.headers ?? {}) },
  })

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

export { API_BASE }
