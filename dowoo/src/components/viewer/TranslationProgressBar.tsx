import CircularProgress from '../ui/CircularProgress'

export interface TranslationProgressBarProps {
  progress: number
}

export default function TranslationProgressBar({ progress }: TranslationProgressBarProps) {
  if (progress <= 0 || progress >= 100) return null

  return (
    <div className="fixed bottom-4 right-4 z-30 rounded-full bg-white/90 px-3 py-2 shadow-lg dark:bg-gray-900/90">
      <CircularProgress progress={progress} label="번역 중..." />
    </div>
  )
}
