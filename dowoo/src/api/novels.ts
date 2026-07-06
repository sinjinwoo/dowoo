import { apiDelete, apiDownload, apiGet, apiPatch, apiPost } from './client'
import type { Novel, NovelDetail, ReadResult } from '../types/novel'

export const listNovels = (keyword?: string) =>
  apiGet<Novel[]>(`/api/v1/novels${keyword ? `?keyword=${encodeURIComponent(keyword)}` : ''}`)

export const getNovelDetail = (novelId: string) => apiGet<NovelDetail>(`/api/v1/novels/${novelId}`)

export const patchNovel = (
  novelId: string,
  partial: { title?: string; originalTitle?: string; coverUrl?: string }
) => apiPatch<NovelDetail>(`/api/v1/novels/${novelId}`, partial)

// promptId=null은 "기본 프롬프트로 되돌린다"는 유효한 값이라 patchNovel의 부분 수정과 성격이
// 달라(생략=미변경 vs null=명시적 해제를 구분해야 함) 전용 엔드포인트로 분리했다(api-spec 2.9).
export const selectNovelPrompt = (novelId: string, promptId: string | null) =>
  apiPatch<NovelDetail>(`/api/v1/novels/${novelId}/prompt`, { promptId })

export const deleteNovel = (novelId: string) => apiDelete(`/api/v1/novels/${novelId}`)

export const reorderNovels = (orderedIds: string[]) => apiPatch<void>('/api/v1/novels/reorder', { orderedIds })

export const updateLastRead = (novelId: string, lastReadChapterIndex: number, lastReadScrollPos?: number) =>
  apiPatch<void>(`/api/v1/novels/${novelId}/last-read`, { lastReadChapterIndex, lastReadScrollPos })

// 다운로드 버튼은 단순 <a href> 이동이 아니라 인증 헤더가 실린 fetch로 받아와야 한다 -
// 이 앱은 Authorization 헤더로 인증하는데, 브라우저의 순수 네비게이션은 그 헤더를 붙이지 않아 401이 난다.
export const downloadNovelExport = async (
  novelId: string,
  lang: 'translated' | 'original' | 'both' = 'translated'
): Promise<void> => {
  const { blob, filename } = await apiDownload(`/api/v1/novels/${novelId}/export?lang=${lang}`)
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename ?? 'novel.txt'
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

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
