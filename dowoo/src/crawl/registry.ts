import { parseIdxzs } from './parser/idx'
import { parseMxsw } from './parser/mxsw'
import { parse69shuba } from './parser/69shuba'
import { parseTwkan } from './parser/twkan'

export interface ParsedChapter {
  title: string
  content: string
  prevUrl: string | null
  nextUrl: string | null
}

interface RawParsedChapter {
  title: string
  content: string
  prev: string | null
  next: string | null
}

function resolveParser(hostname: string): ((html: string) => RawParsedChapter) | null {
  if (hostname.endsWith('ixdzs8.com')) return parseIdxzs
  if (hostname.endsWith('m.xsw.tw')) return parseMxsw
  if (hostname.endsWith('69shuba.com')) return parse69shuba
  if (hostname.endsWith('twkan.com')) return parseTwkan
  return null
}

function resolveUrl(maybeRelative: string | null, base: string): string | null {
  if (!maybeRelative) return null
  try {
    return new URL(maybeRelative, base).toString()
  } catch {
    return null
  }
}

async function fetchChapterHtml(url: string): Promise<string> {
  const response = await fetch(`/api/proxy?url=${encodeURIComponent(url)}`)
  if (!response.ok) {
    throw new Error(await response.text())
  }
  return response.text()
}

export function isSupportedUrl(url: string): boolean {
  try {
    return resolveParser(new URL(url).hostname) !== null
  } catch {
    return false
  }
}

export async function crawlChapter(url: string): Promise<ParsedChapter> {
  const hostname = new URL(url).hostname
  const parser = resolveParser(hostname)
  if (!parser) {
    throw new Error(`지원하지 않는 사이트입니다: ${hostname}`)
  }

  const html = await fetchChapterHtml(url)
  const raw = parser(html)

  return {
    title: raw.title,
    content: raw.content,
    prevUrl: resolveUrl(raw.prev, url),
    nextUrl: resolveUrl(raw.next, url),
  }
}
