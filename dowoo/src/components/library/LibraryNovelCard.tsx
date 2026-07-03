import { useSortable } from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import type { Novel } from '../../types/novel'
import Button from '../ui/Button'

export interface LibraryNovelCardProps {
  novel: Novel
  onSelect: () => void
  onEdit: () => void
  onDownload: () => void
  onDelete: () => void
}

export default function LibraryNovelCard({
  novel,
  onSelect,
  onEdit,
  onDownload,
  onDelete,
}: LibraryNovelCardProps) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: novel.id,
  })

  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.5 : 1,
  }

  return (
    <div
      ref={setNodeRef}
      style={style}
      className="relative flex w-full min-w-0 gap-3 overflow-hidden rounded-lg border border-gray-200 bg-white p-3 dark:border-gray-800 dark:bg-gray-900"
    >
      <button
        type="button"
        onClick={onDelete}
        aria-label="삭제"
        className="absolute right-2 top-2 rounded p-1 text-gray-300 hover:text-red-500 dark:text-gray-600 dark:hover:text-red-400"
      >
        <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="currentColor">
          <path d="M 12 2 C 6.4889971 2 2 6.4889971 2 12 C 2 17.511003 6.4889971 22 12 22 C 17.511003 22 22 17.511003 22 12 C 22 6.4889971 17.511003 2 12 2 z M 12 4 C 16.430123 4 20 7.5698774 20 12 C 20 16.430123 16.430123 20 12 20 C 7.5698774 20 4 16.430123 4 12 C 4 7.5698774 7.5698774 4 12 4 z M 8.7070312 7.2929688 L 7.2929688 8.7070312 L 10.585938 12 L 7.2929688 15.292969 L 8.7070312 16.707031 L 12 13.414062 L 15.292969 16.707031 L 16.707031 15.292969 L 13.414062 12 L 16.707031 8.7070312 L 15.292969 7.2929688 L 12 10.585938 L 8.7070312 7.2929688 z" />
        </svg>
      </button>

      <button
        type="button"
        {...attributes}
        {...listeners}
        aria-label="순서 변경 (드래그)"
        className="flex shrink-0 cursor-grab touch-none items-center text-gray-300 hover:text-gray-500 active:cursor-grabbing dark:text-gray-600 dark:hover:text-gray-400"
      >
        ⠿
      </button>

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
      <div className="min-w-0 flex-1 pr-6">
        <button
          type="button"
          onClick={onSelect}
          className="block truncate text-left text-sm font-semibold hover:underline"
        >
          {novel.title}
        </button>
        <p className="truncate text-xs text-gray-400">{novel.lastReadChapterTitle}</p>
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
