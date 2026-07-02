import type { Novel } from '../../types/novel'
import Button from '../ui/Button'

export interface LibraryNovelCardProps {
  novel: Novel
  onSelect: () => void
  onEdit: () => void
  onDownload: () => void
}

export default function LibraryNovelCard({ novel, onSelect, onEdit, onDownload }: LibraryNovelCardProps) {
  return (
    <div className="flex gap-3 rounded-lg border border-gray-200 p-3 dark:border-gray-800">
      <button
        type="button"
        onClick={onSelect}
        className="h-20 w-14 shrink-0 overflow-hidden rounded bg-gray-100 dark:bg-gray-800"
      >
        {novel.coverUrl ? (
          <img src={novel.coverUrl} alt="" className="h-full w-full object-cover" />
        ) : (
          <span className="flex h-full items-center justify-center text-center text-[10px] text-gray-400">
            표지 없음
          </span>
        )}
      </button>
      <div className="min-w-0 flex-1">
        <button
          type="button"
          onClick={onSelect}
          className="block truncate text-left text-sm font-semibold hover:underline"
        >
          {novel.title}
        </button>
        <p className="truncate text-xs text-gray-500">{novel.siteName}</p>
        <p className="truncate text-xs text-gray-400">
          {novel.chapters[novel.lastReadChapterIndex]?.title}
        </p>
        <div className="mt-2 flex gap-1">
          <Button variant="ghost" size="sm" onClick={onEdit}>
            편집
          </Button>
          <Button variant="ghost" size="sm" onClick={onDownload}>
            다운로드
          </Button>
        </div>
      </div>
    </div>
  )
}
