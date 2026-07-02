import type { Chapter } from '../../types/novel'
import type { ThemeSettings } from '../../types/settings'
import ViewerToolbar from './ViewerToolbar'
import ViewerContent from './ViewerContent'
import TranslationProgressBar from './TranslationProgressBar'

export interface NovelViewerProps {
  chapter: Chapter
  theme: ThemeSettings
  progress: number
  displayedTranslatedText?: string
  isTranslating?: boolean
}

export default function NovelViewer({
  chapter,
  theme,
  progress,
  displayedTranslatedText,
  isTranslating,
}: NovelViewerProps) {
  return (
    <div>
      <ViewerToolbar chapterTitle={chapter.title} />
      <ViewerContent
        chapter={chapter}
        theme={theme}
        displayedTranslatedText={displayedTranslatedText}
        isTranslating={isTranslating}
      />
      <TranslationProgressBar progress={progress} />
    </div>
  )
}
