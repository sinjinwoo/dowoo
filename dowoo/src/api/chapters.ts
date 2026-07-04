import { apiGet, apiPatch, apiPost } from './client'
import type { Chapter } from '../types/novel'

export const getChapter = (novelId: string, chapterId: string) =>
  apiGet<Chapter>(`/api/v1/novels/${novelId}/chapters/${chapterId}`)

export const patchChapterTranslation = (novelId: string, chapterId: string, translatedText: string) =>
  apiPatch<Chapter>(`/api/v1/novels/${novelId}/chapters/${chapterId}`, { translatedText })

export const createChapter = (
  novelId: string,
  chapter: { sourceUrl: string; title: string; originalText: string; prevUrl?: string | null; nextUrl?: string | null }
) => apiPost<Chapter>(`/api/v1/novels/${novelId}/chapters`, chapter)
