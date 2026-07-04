import { apiDelete, apiGet, apiPatch, apiPost } from './client'
import type { Novel, NovelDetail, ReadResult } from '../types/novel'

export const listNovels = (keyword?: string) =>
  apiGet<Novel[]>(`/api/v1/novels${keyword ? `?keyword=${encodeURIComponent(keyword)}` : ''}`)

export const getNovelDetail = (novelId: string) => apiGet<NovelDetail>(`/api/v1/novels/${novelId}`)

export const patchNovel = (
  novelId: string,
  partial: { title?: string; originalTitle?: string; coverUrl?: string; systemPrompt?: string; translationNote?: string }
) => apiPatch<NovelDetail>(`/api/v1/novels/${novelId}`, partial)

export const deleteNovel = (novelId: string) => apiDelete(`/api/v1/novels/${novelId}`)

export const reorderNovels = (orderedIds: string[]) => apiPatch<void>('/api/v1/novels/reorder', { orderedIds })

export const updateLastRead = (novelId: string, lastReadChapterIndex: number, lastReadScrollPos?: number) =>
  apiPatch<void>(`/api/v1/novels/${novelId}/last-read`, { lastReadChapterIndex, lastReadScrollPos })

export const exportNovelUrl = (novelId: string, lang: 'translated' | 'original' | 'both' = 'translated') =>
  `/api/v1/novels/${novelId}/export?lang=${lang}`

export const readSource = (input: { sourceUrl?: string; pastedText?: string; forceRecrawl?: boolean }) =>
  apiPost<ReadResult>('/api/v1/read', input)

export interface CrawlResult {
  title: string
  bookTitle: string
  content: string
  prevUrl?: string | null
  nextUrl?: string | null
  siteName: string
}

// 이전/다음 챕터 이동처럼 novelId를 이미 아는 경우 - /read(§6.2)는 캐시 미스 시 새 소설을 만들어버리므로
// 대신 크롤링(§6.1) + 챕터 생성(§3.2)을 직접 두 단계로 호출한다(api-spec.md 6.1 "추가 참고사항").
export const crawlUrl = (url: string) => apiPost<CrawlResult>('/api/v1/crawl', { url })
