import type { CSSProperties } from 'react'
import type { Chapter } from '../../types/novel'
import type { ThemeSettings } from '../../types/settings'
import OriginalParagraph from './OriginalParagraph'

export interface ViewerContentProps {
  chapter: Chapter
  theme: ThemeSettings
}

export default function ViewerContent({ chapter, theme }: ViewerContentProps) {
  const translatedParagraphs = chapter.translatedText.split('\n').filter(Boolean)
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
    </div>
  )
}