import type { Novel } from '../../types/novel'
import LibraryNovelCard from './LibraryNovelCard'

export interface LibraryListProps {
  novels: Novel[]
  onSelect: (novel: Novel) => void
  onEdit: (novel: Novel) => void
  onDownload: (novel: Novel) => void
}

export default function LibraryList({ novels, onSelect, onEdit, onDownload }: LibraryListProps) {
  if (novels.length === 0) {
    return <p className="py-8 text-center text-sm text-gray-400">서재가 비어 있습니다.</p>
  }

  return (
    <div className="space-y-3">
      {novels.map((novel) => (
        <LibraryNovelCard
          key={novel.id}
          novel={novel}
          onSelect={() => onSelect(novel)}
          onEdit={() => onEdit(novel)}
          onDownload={() => onDownload(novel)}
        />
      ))}
    </div>
  )
}
