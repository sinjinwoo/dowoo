export interface Chapter {
  id: string
  title: string
  sourceUrl: string
  originalText: string
  translatedText: string
}

export interface Novel {
  id: string
  title: string
  originalTitle?: string
  coverUrl?: string
  sourceUrl: string
  siteName: string
  chapters: Chapter[]
  lastReadChapterIndex: number
  lastReadScrollPos?: number
  translationNote: string
  systemPrompt: string
}
