import { API_BASE } from './client'

export class TranslationAbortedError extends Error {
  constructor() {
    super('번역이 취소되었습니다.')
    this.name = 'TranslationAbortedError'
  }
}

export interface TranslateStreamArgs {
  novelId: string
  chapterId: string
  signal: AbortSignal
  onLine: (line: string) => void
  onProgress?: (percent: number) => void
}

interface SseErrorData {
  code?: string
  message?: string
}

// Core API의 §7.1 SSE 스트림(start/line/progress/done/error)을 소비한다.
// fetch의 body ReadableStream을 직접 파싱한다 - EventSource는 GET만 지원해 POST 스트림엔 못 쓴다.
export async function translateStream({
  novelId,
  chapterId,
  signal,
  onLine,
  onProgress,
}: TranslateStreamArgs): Promise<string> {
  let response: Response
  try {
    response = await fetch(
      `${API_BASE}/api/v1/novels/${novelId}/chapters/${chapterId}/translate/stream`,
      { method: 'POST', signal }
    )
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw new TranslationAbortedError()
    }
    throw error
  }

  if (!response.ok || !response.body) {
    const body = await response.json().catch(() => null)
    throw new Error(body?.message ?? '번역 요청에 실패했습니다.')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let eventName: string | null = null
  let dataBuffer = ''
  let doneText: string | null = null

  try {
    while (true) {
      const { value, done } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })

      let newlineIndex: number
      while ((newlineIndex = buffer.indexOf('\n')) !== -1) {
        const line = buffer.slice(0, newlineIndex).replace(/\r$/, '')
        buffer = buffer.slice(newlineIndex + 1)

        if (line.startsWith('event:')) {
          eventName = line.slice(6).trim()
        } else if (line.startsWith('data:')) {
          dataBuffer += line.slice(5).trim()
        } else if (line === '' && eventName) {
          const data: unknown = dataBuffer ? JSON.parse(dataBuffer) : {}

          if (eventName === 'line') {
            onLine((data as { text?: string }).text ?? '')
          } else if (eventName === 'progress') {
            onProgress?.((data as { percent?: number }).percent ?? 0)
          } else if (eventName === 'done') {
            doneText = (data as { translatedText?: string }).translatedText ?? ''
          } else if (eventName === 'error') {
            const errorData = data as SseErrorData
            throw new Error(errorData.message ?? '번역 중 오류가 발생했습니다.')
          }

          eventName = null
          dataBuffer = ''
        }
      }
    }
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw new TranslationAbortedError()
    }
    throw error
  }

  if (doneText === null) {
    throw new Error('번역 스트림이 완료되지 않고 종료되었습니다.')
  }

  return doneText
}
