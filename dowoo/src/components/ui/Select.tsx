import type { CSSProperties } from 'react'

export interface SelectOption<T extends string = string> {
  value: T
  label: string
  style?: CSSProperties
}

export interface SelectProps<T extends string = string> {
  label?: string
  value: T
  options: SelectOption<T>[]
  onChange: (value: T) => void
}

export default function Select<T extends string = string>({ label, value, options, onChange }: SelectProps<T>) {
  return (
    <label className="block">
      {label && <span className="mb-1 block text-sm text-gray-700 dark:text-gray-300">{label}</span>}
      <select
        value={value}
        onChange={(e) => onChange(e.target.value as T)}
        className="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100"
      >
        {options.map((option) => (
          <option key={option.value} value={option.value} style={option.style}>
            {option.label}
          </option>
        ))}
      </select>
    </label>
  )
}
