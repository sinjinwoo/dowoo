import {
  DndContext,
  PointerSensor,
  TouchSensor,
  useSensor,
  useSensors,
  type DragEndEvent,
} from '@dnd-kit/core'
import { SortableContext, verticalListSortingStrategy, arrayMove } from '@dnd-kit/sortable'
import type { Novel } from '../../types/novel'
import LibraryNovelCard from './LibraryNovelCard'

export interface LibraryListProps {
  novels: Novel[]
  onSelect: (novel: Novel) => void
  onEdit: (novel: Novel) => void
  onDownload: (novel: Novel) => void
  onDelete: (novel: Novel) => void
  onReorder: (orderedIds: string[]) => void
}

export default function LibraryList({
  novels,
  onSelect,
  onEdit,
  onDownload,
  onDelete,
  onReorder,
}: LibraryListProps) {
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 4 } }),
    useSensor(TouchSensor, { activationConstraint: { delay: 150, tolerance: 8 } })
  )

  if (novels.length === 0) {
    return <p className="py-8 text-center text-sm text-gray-400">서재가 비어 있습니다.</p>
  }

  const handleDragEnd = (event: DragEndEvent) => {
    const { active, over } = event
    if (!over || active.id === over.id) return

    const oldIndex = novels.findIndex((n) => n.id === active.id)
    const newIndex = novels.findIndex((n) => n.id === over.id)
    if (oldIndex === -1 || newIndex === -1) return

    const reordered = arrayMove(novels, oldIndex, newIndex)
    onReorder(reordered.map((n) => n.id))
  }

  return (
    <DndContext sensors={sensors} onDragEnd={handleDragEnd}>
      <SortableContext items={novels.map((n) => n.id)} strategy={verticalListSortingStrategy}>
        <div className="space-y-3">
          {novels.map((novel) => (
            <LibraryNovelCard
              key={novel.id}
              novel={novel}
              onSelect={() => onSelect(novel)}
              onEdit={() => onEdit(novel)}
              onDownload={() => onDownload(novel)}
              onDelete={() => onDelete(novel)}
            />
          ))}
        </div>
      </SortableContext>
    </DndContext>
  )
}
