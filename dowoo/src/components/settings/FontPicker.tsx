import type { FontOption } from '../../data/fontOptions'

export interface FontPickerProps {
  value: string
  options: FontOption[]
  onChange: (value: string) => void
}

export default function FontPicker({ value, options, onChange }: FontPickerProps) {
  return (
    <div>
      <span className="mb-1 block text-sm text-gray-700 dark:text-gray-300">폰트</span>
      <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
        {options.map((option) => (
          <button
            key={option.value}
            type="button"
            onClick={() => onChange(option.value)}
            className={`rounded-lg border px-3 py-2 text-left transition-colors ${
              value === option.value
                ? 'border-accent bg-accent/10'
                : 'border-gray-300 hover:border-accent/50 dark:border-gray-700'
            }`}
          >
            <span className="block text-sm font-medium text-gray-900 dark:text-gray-100">
              {option.label}
            </span>
            <span
              className="block truncate text-base text-gray-700 dark:text-gray-300"
              style={{ fontFamily: option.value }}
            >
              가나다 ABC 123
            </span>
          </button>
        ))}
      </div>
    </div>
  )
}
