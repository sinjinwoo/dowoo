import { GoogleGenAI, ApiError } from '@google/genai'
import { resolveSystemPrompt } from './prompt'

export interface TranslateStreamArgs {
  apiKeys: string[]
  model: string
  systemPrompt: string
  translationNote: string
  originalText: string
  onLine: (line: string) => void
  signal: AbortSignal
}

export class TranslationAbortedError extends Error {
  constructor() {
    super('번역이 취소되었습니다.')
    this.name = 'TranslationAbortedError'
  }
}

function getStatus(error: unknown): number | null {
  if (error instanceof ApiError) return error.status
  const raw = error instanceof Error ? error.message : String(error)
  const match = raw.match(/\b(4\d{2}|5\d{2})\b/)
  return match ? Number(match[1]) : null
}

function isAuthOrQuotaError(error: unknown): boolean {
  const status = getStatus(error)
  if (status !== null) return status === 400 || status === 401 || status === 403 || status === 429
  const message = error instanceof Error ? error.message : String(error)
  return /api key|permission|quota|rate limit/i.test(message)
}

// 개발 지식이 없는 사용자도 이해할 수 있도록 원인별로 안내 문구를 나눔
function toFriendlyMessage(error: unknown): string {
  const status = getStatus(error)

  if (status === 400) {
    return 'API 키가 올바르지 않습니다. 설정 화면에서 API 키를 다시 확인해주세요.'
  }

  if (status === 401 || status === 403) {
    return 'API 키가 올바르지 않거나 사용 권한이 없습니다. 설정 화면에서 API 키를 다시 확인해주세요.'
  }

  if (status === 429) {
    return '구글 서버 사용량 한도를 초과했습니다. 잠시 후 다시 시도해주세요.'
  }

  if (status !== null && status >= 500) {
    return '구글 서버에 일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요.'
  }

  const raw = error instanceof Error ? error.message : String(error)

  if (/api key/i.test(raw)) {
    return 'API 키가 올바르지 않습니다. 설정 화면에서 API 키를 다시 확인해주세요.'
  }

  if (/quota|rate limit/i.test(raw)) {
    return '구글 서버 사용량 한도를 초과했습니다. 잠시 후 다시 시도해주세요.'
  }

  if (/network|fetch|failed to fetch/i.test(raw)) {
    return '네트워크 연결을 확인해주세요.'
  }

  if (/iso-8859-1|non.?ascii|headers/i.test(raw)) {
    return 'API 키에 사용할 수 없는 문자가 포함되어 있습니다. 복사할 때 공백이나 특수문자가 섞이지 않았는지 확인 후 다시 입력해주세요.'
  }

  return `번역 중 알 수 없는 오류가 발생했습니다. 잠시 후 다시 시도해주세요. (${raw})`
}

// Google API 키는 항상 출력 가능한 ASCII 문자로만 구성됨 (붙여넣기 시 섞여 들어온
// 전각 공백/특수문자 등이 있으면 HTTP 헤더에 넣을 수 없어 브라우저가 예외를 던짐)
function isAsciiApiKey(key: string): boolean {
  return /^[\x21-\x7E]+$/.test(key)
}

export async function translateStream({
  apiKeys,
  model,
  systemPrompt,
  translationNote,
  originalText,
  onLine,
  signal,
}: TranslateStreamArgs): Promise<void> {
  const rawKeys = apiKeys.map((key) => key.trim()).filter(Boolean)
  if (rawKeys.length === 0) {
    throw new Error('번역에 사용할 API 키가 없습니다. 설정 화면에서 API 키를 입력해주세요.')
  }

  const keys = rawKeys.filter(isAsciiApiKey)
  if (keys.length === 0) {
    throw new Error(
      'API 키에 사용할 수 없는 문자가 포함되어 있습니다. 복사할 때 공백이나 특수문자가 섞이지 않았는지 확인 후 다시 입력해주세요.'
    )
  }

  const resolvedPrompt = resolveSystemPrompt(systemPrompt, translationNote)
  let lastError: unknown = null

  // 매 요청마다 시작 키를 무작위로 골라서 특정 키(주로 1번)에만 호출이 몰리지 않도록 분산
  const startIndex = Math.floor(Math.random() * keys.length)

  for (let attempt = 0; attempt < keys.length; attempt++) {
    if (signal.aborted) throw new TranslationAbortedError()

    const keyIndex = (startIndex + attempt) % keys.length
    const ai = new GoogleGenAI({ apiKey: keys[keyIndex] })
    let buffer = ''

    try {
      const stream = await ai.models.generateContentStream({
        model,
        contents: originalText,
        config: { systemInstruction: resolvedPrompt, abortSignal: signal },
      })

      for await (const chunk of stream) {
        if (signal.aborted) throw new TranslationAbortedError()

        const text = chunk.text
        if (!text) continue

        buffer += text
        let newlineIndex: number
        while ((newlineIndex = buffer.indexOf('\n')) !== -1) {
          const line = buffer.slice(0, newlineIndex)
          buffer = buffer.slice(newlineIndex + 1)
          onLine(line)
        }
      }

      if (signal.aborted) throw new TranslationAbortedError()

      if (buffer.length > 0) {
        onLine(buffer)
      }

      return
    } catch (error) {
      if (error instanceof TranslationAbortedError) throw error
      if (signal.aborted) throw new TranslationAbortedError()

      lastError = error
      if (isAuthOrQuotaError(error) && attempt < keys.length - 1) {
        continue
      }

      throw new Error(toFriendlyMessage(error))
    }
  }

  throw new Error(toFriendlyMessage(lastError))
}
