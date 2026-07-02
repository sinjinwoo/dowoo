export interface CircularProgressProps {
  progress: number
  size?: number
  strokeWidth?: number
  label?: string
}

export default function CircularProgress({
  progress,
  size = 48,
  strokeWidth = 4,
  label,
}: CircularProgressProps) {
  const clamped = Math.min(100, Math.max(0, progress))
  const radius = (size - strokeWidth) / 2
  const circumference = 2 * Math.PI * radius
  const offset = circumference * (1 - clamped / 100)

  return (
    <div className="flex items-center gap-2">
      <svg width={size} height={size} className="-rotate-90">
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          strokeWidth={strokeWidth}
          className="fill-none stroke-gray-200 dark:stroke-gray-700"
        />
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          strokeWidth={strokeWidth}
          strokeDasharray={circumference}
          strokeDashoffset={offset}
          strokeLinecap="round"
          className="fill-none stroke-accent transition-[stroke-dashoffset] duration-300"
        />
      </svg>
      {label && <span className="text-sm text-gray-600 dark:text-gray-300">{label}</span>}
    </div>
  )
}
