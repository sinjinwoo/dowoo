export interface SliderProps {
  label?: string
  value: number
  min: number
  max: number
  step?: number
  unit?: string
  onChange: (value: number) => void
}

export default function Slider({ label, value, min, max, step = 1, unit = '', onChange }: SliderProps) {
  return (
    <label className="block">
      {label && (
        <span className="mb-1 flex items-center justify-between text-sm text-gray-700 dark:text-gray-300">
          <span>{label}</span>
          <span className="tabular-nums text-gray-500">
            {value}
            {unit}
          </span>
        </span>
      )}
      <input
        type="range"
        min={min}
        max={max}
        step={step}
        value={value}
        onChange={(e) => onChange(Number(e.target.value))}
        className="w-full accent-accent"
      />
    </label>
  )
}
