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
