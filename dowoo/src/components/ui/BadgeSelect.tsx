import { useEffect, useRef, useState } from 'react'

export interface BadgeSelectOption<T extends string = string> {
  value: T
  label: string
  badge?: { text: string; tone: 'free' | 'paid' }
}

export interface BadgeSelectProps<T extends string = string> {
  label?: string
  value: T
  options: BadgeSelectOption<T>[]
  onChange: (value: T) => void
}

const badgeToneClasses: Record<'free' | 'paid', string> = {
  free: 'bg-green-100 text-green-700 dark:bg-green-900/40 dark:text-green-300',
  paid: 'bg-amber-100 text-amber-700 dark:bg-amber-900/40 dark:text-amber-300',
}

function Badge({ tone, text }: { tone: 'free' | 'paid'; text: string }) {
  return (
    <span className={`ml-2 shrink-0 rounded px-1.5 py-0.5 text-[10px] font-medium ${badgeToneClasses[tone]}`}>
      {text}
    </span>
  )
}

// 네이티브 <select>의 <option>은 배지 같은 리치 콘텐츠를 렌더링할 수 없어서(브라우저가 텍스트만
// 표시), 유료/무료 구분 배지가 필요한 모델 선택에 한해 직접 구현한 드롭다운을 쓴다.
export default function BadgeSelect<T extends string = string>({
  label,
  value,
  options,
  onChange,
}: BadgeSelectProps<T>) {
  const [isOpen, setIsOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!isOpen) return
    const handleClickOutside = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setIsOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [isOpen])

  const selected = options.find((o) => o.value === value)

  return (
    <div className="relative" ref={containerRef}>
      {label && <span className="mb-1 block text-sm text-gray-700 dark:text-gray-300">{label}</span>}
      <button
        type="button"
        onClick={() => setIsOpen((prev) => !prev)}
        className="flex w-full items-center justify-between rounded-lg border border-gray-300 bg-white px-3 py-2 text-left text-sm text-gray-900 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100"
        aria-haspopup="listbox"
        aria-expanded={isOpen}
      >
        <span className="truncate">{selected?.label ?? ''}</span>
        <span className="ml-2 flex shrink-0 items-center gap-2">
          {selected?.badge && <Badge tone={selected.badge.tone} text={selected.badge.text} />}
          <span className="text-gray-400">▾</span>
        </span>
      </button>

      {isOpen && (
        <ul
          role="listbox"
          className="absolute z-20 mt-1 max-h-64 w-full overflow-y-auto rounded-lg border border-gray-300 bg-white py-1 shadow-lg dark:border-gray-700 dark:bg-gray-800"
        >
          {options.map((option) => (
            <li key={option.value} role="option" aria-selected={option.value === value}>
              <button
                type="button"
                onClick={() => {
                  onChange(option.value)
                  setIsOpen(false)
                }}
                className={`flex w-full items-center justify-between px-3 py-2 text-left text-sm hover:bg-gray-100 dark:hover:bg-gray-700 ${
                  option.value === value ? 'bg-gray-50 dark:bg-gray-700/50' : ''
                }`}
              >
                <span className="truncate text-gray-900 dark:text-gray-100">{option.label}</span>
                {option.badge && <Badge tone={option.badge.tone} text={option.badge.text} />}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
