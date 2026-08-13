import {
  DndContext,
  PointerSensor,
  TouchSensor,
  useSensor,
  useSensors,
  type DragEndEvent,
} from '@dnd-kit/core'
import { SortableContext, verticalListSortingStrategy, arrayMove, useSortable } from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import type { BadgeSelectOption } from '../ui/BadgeSelect'

const badgeToneClasses: Record<'free' | 'paid', string> = {
  free: 'bg-green-100 text-green-700 dark:bg-green-900/40 dark:text-green-300',
  paid: 'bg-amber-100 text-amber-700 dark:bg-amber-900/40 dark:text-amber-300',
}

export interface ModelOrderListProps {
  order: string[]
  options: BadgeSelectOption[]
  disabled?: boolean
  onReorder: (order: string[]) => void
}

interface ModelOrderRowProps {
  modelId: string
  label: string
  badge?: BadgeSelectOption['badge']
  disabled?: boolean
}

function ModelOrderRow({ modelId, label, badge, disabled }: ModelOrderRowProps) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: modelId,
    disabled,
  })

  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.5 : 1,
  }

  return (
    <li
      ref={setNodeRef}
      style={style}
      className="flex items-center gap-2 rounded-lg border border-gray-300 px-3 py-2 text-xs text-gray-900 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100"
    >
      <button
        type="button"
        {...attributes}
        {...listeners}
        disabled={disabled}
        aria-label="순서 변경 (드래그)"
        className="shrink-0 cursor-grab touch-none text-gray-300 hover:text-gray-500 active:cursor-grabbing disabled:cursor-not-allowed disabled:opacity-40 dark:text-gray-600 dark:hover:text-gray-400"
      >
        ⠿
      </button>
      <span className="min-w-0 flex-1 truncate">{label}</span>
      {badge && (
        <span
          className={`shrink-0 rounded px-1.5 py-0.5 text-[10px] font-medium ${badgeToneClasses[badge.tone]}`}
        >
          {badge.text}
        </span>
      )}
    </li>
  )
}

/** "자동" 모드에서 무료 모델을 시도할 순서를 드래그로 재배열한다(설정탭 인라인, 별도 모달 없음). */
export default function ModelOrderList({ order, options, disabled, onReorder }: ModelOrderListProps) {
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 4 } }),
    useSensor(TouchSensor, { activationConstraint: { delay: 150, tolerance: 8 } })
  )

  const handleDragEnd = (event: DragEndEvent) => {
    const { active, over } = event
    if (!over || active.id === over.id) return

    const oldIndex = order.indexOf(String(active.id))
    const newIndex = order.indexOf(String(over.id))
    if (oldIndex === -1 || newIndex === -1) return

    onReorder(arrayMove(order, oldIndex, newIndex))
  }

  return (
    <DndContext sensors={sensors} onDragEnd={handleDragEnd}>
      <SortableContext items={order} strategy={verticalListSortingStrategy}>
        <ul className="space-y-1">
          {order.map((modelId) => {
            const option = options.find((o) => o.value === modelId)
            return (
              <ModelOrderRow
                key={modelId}
                modelId={modelId}
                label={option?.label ?? modelId}
                badge={option?.badge}
                disabled={disabled}
              />
            )
          })}
        </ul>
      </SortableContext>
    </DndContext>
  )
}
