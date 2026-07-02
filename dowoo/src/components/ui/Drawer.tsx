import type { ReactNode } from 'react'

export interface DrawerProps {
  isOpen: boolean
  onClose: () => void
  side?: 'left' | 'right'
  title?: string
  children: ReactNode
  widthClassName?: string
}

export default function Drawer({
  isOpen,
  onClose,
  side = 'right',
  title,
  children,
  widthClassName = 'w-80',
}: DrawerProps) {
  if (!isOpen) return null

  const sideClasses = side === 'right' ? 'right-0' : 'left-0'

  return (
    <div className="fixed inset-0 z-50">
      <button
        type="button"
        aria-label="닫기"
        className="absolute inset-0 cursor-default bg-black/40"
        onClick={onClose}
      />
      <div
        className={`absolute top-0 ${sideClasses} flex h-full ${widthClassName} max-w-full flex-col bg-white text-gray-900 shadow-xl dark:bg-gray-900 dark:text-gray-100`}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between bg-accent px-4 py-3 text-white">
          <h2 className="text-base font-semibold">{title}</h2>
          <button
            type="button"
            onClick={onClose}
            aria-label="닫기"
            className="rounded-md px-2 py-1 text-white/80 hover:bg-white/10 hover:text-white"
          >
            ✕
          </button>
        </div>
        <div className="flex-1 overflow-y-auto p-4">{children}</div>
      </div>
    </div>
  )
}
