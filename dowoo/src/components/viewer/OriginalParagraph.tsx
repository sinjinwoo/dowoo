import { useState } from 'react'

export interface OriginalParagraphProps {
  translatedText: string
  originalText: string
  textIndent: number
  fontSize: number
}

export default function OriginalParagraph({
  translatedText,
  originalText,
  textIndent,
  fontSize,
}: OriginalParagraphProps) {
  const [isOpen, setIsOpen] = useState(false)

  return (
    <div className="mb-4">
      <p
        className="cursor-pointer whitespace-pre-wrap"
        style={{ textIndent: `${textIndent}em` }}
        onClick={(e) => {
          e.stopPropagation()
          setIsOpen((v) => !v)
        }}
      >
        {translatedText}
      </p>

      {isOpen && originalText && (
        <p
          className="mt-2 whitespace-pre-wrap text-xs leading-relaxed text-gray-500 dark:text-gray-400"
          style={{
            textIndent: `${textIndent * fontSize}px`,
          }}
        >
          {originalText}
        </p>
      )}
    </div>
  )
}
