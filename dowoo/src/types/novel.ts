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

/** 소설 상세(GET /api/v1/novels/{id}) - 연결된 프롬프트 참조와 챕터 메타 목록(본문 제외) 포함. */
export interface NovelDetail extends Novel {
  /** null이면 사용자의 기본 프롬프트를 쓴다는 뜻. */
  promptId: string | null
  /** 화면 표시용으로 해석된 제목 - promptId가 null이면 "기본 프롬프트". */
  promptTitle: string
  lastReadScrollPos?: number | null
  chapters: ChapterMeta[]
  createdAt: string
}

export interface ReadResult {
  novelId: string
  chapterId: string
  translatedText: string
}
