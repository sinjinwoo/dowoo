export interface ChapterMeta {
  id: string
  title: string
  sourceUrl: string
  chapterIndex: number
}

export interface Chapter {
  id: string
  novelId: string
  title: string
  sourceUrl: string
  originalText: string
  translatedText: string
  prevUrl?: string | null
  nextUrl?: string | null
  chapterIndex: number
}

/** 서재 목록(GET /api/v1/novels)에 쓰는 요약 형태 - 챕터 본문은 포함하지 않는다. */
export interface Novel {
  id: string
  title: string
  originalTitle?: string | null
  coverUrl?: string | null
  sourceUrl: string
  siteName: string
  chapterCount: number
  lastReadChapterIndex: number | null
  lastReadChapterTitle?: string | null
  order: number
  updatedAt: string
}

/** 소설 상세(GET /api/v1/novels/{id}) - 프롬프트/노트와 챕터 메타 목록(본문 제외) 포함. */
export interface NovelDetail extends Novel {
  systemPrompt: string | null
  translationNote: string | null
  lastReadScrollPos?: number | null
  chapters: ChapterMeta[]
  createdAt: string
}

export interface ReadResult {
  novelId: string
  chapterId: string
  translatedText: string
}
