import type { CSSProperties } from 'react'
import type { Chapter } from '../../types/novel'
import type { ThemeSettings } from '../../types/settings'
import OriginalParagraph from './OriginalParagraph'
import Spinner from '../ui/Spinner'

export interface ViewerContentProps {
  chapter: Chapter
  theme: ThemeSettings
  displayedTranslatedText?: string
  isTranslating?: boolean
  progress?: number
}

export default function ViewerContent({
  chapter,
  theme,
  displayedTranslatedText,
  isTranslating,
  progress = 0,
}: ViewerContentProps) {
  const translatedParagraphs = (displayedTranslatedText ?? chapter.translatedText).split('\n').filter(Boolean)
  const originalParagraphs = chapter.originalText.split('\n').filter(Boolean)

  const textStyle: CSSProperties = {
    fontFamily: theme.fontFamily || undefined,
    fontSize: `${theme.fontSize}px`,
    fontWeight: theme.fontWeight,
    lineHeight: theme.lineHeight,
  }

  return (
    <div style={textStyle}>
      {translatedParagraphs.map((translated, i) => (
        <OriginalParagraph
          key={i}
          translatedText={translated}
          originalText={originalParagraphs[i] ?? ''}
          textIndent={theme.textIndent}
          fontSize={theme.fontSize}
        />
      ))}
      {isTranslating && <Spinner label={`번역 중...${Math.round(progress)}%`} />}
    </div>
  )
}