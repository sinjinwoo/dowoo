export interface SpinnerProps {
  label?: string
}

export default function Spinner({ label = '번역 중...' }: SpinnerProps) {
  return (
    <div className="flex items-center gap-3 py-4 text-gray-400 dark:text-gray-500">
      <span className="h-5 w-5 animate-spin rounded-full border-2 border-current border-t-transparent" />
      {label && <span className="text-sm">{label}</span>}
    </div>
  )
}
