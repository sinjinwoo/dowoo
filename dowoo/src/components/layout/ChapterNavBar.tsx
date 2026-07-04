import Button from '../ui/Button'

export interface ChapterNavBarProps {
  onPrevChapter: () => void
  onNextChapter: () => void
  fontColor?: string
}

export default function ChapterNavBar({ onPrevChapter, onNextChapter, fontColor }: ChapterNavBarProps) {
  return (
    <div className="mt-8 flex items-center justify-between gap-3 border-t border-gray-200 pt-4 dark:border-gray-800">
      <Button variant="ghost" onClick={onPrevChapter} style={{ color: fontColor }}>
        ◀ 이전편
      </Button>
      <Button variant="ghost" onClick={onNextChapter} style={{ color: fontColor }}>
        다음편 ▶
      </Button>
    </div>
  )
}
