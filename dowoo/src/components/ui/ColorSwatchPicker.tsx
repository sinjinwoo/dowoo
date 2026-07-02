export interface ColorSwatchPickerProps {
  label?: string
  value: string
  onChange: (value: string) => void
}

export default function ColorSwatchPicker({ label, value, onChange }: ColorSwatchPickerProps) {
  return (
    <label className="flex items-center justify-between gap-3">
      {label && <span className="text-sm text-gray-700 dark:text-gray-300">{label}</span>}
      <span className="flex items-center gap-2">
        <input
          type="color"
          value={value}
          onChange={(e) => onChange(e.target.value)}
          className="h-8 w-8 cursor-pointer rounded border border-gray-300 dark:border-gray-700"
        />
        <span className="font-mono text-xs text-gray-500">{value}</span>
      </span>
    </label>
  )
}
