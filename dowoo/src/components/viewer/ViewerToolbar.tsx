export interface ViewerToolbarProps {
  chapterTitle: string
}

export default function ViewerToolbar({ chapterTitle }: ViewerToolbarProps) {
  return (
    <div className="mb-8">
      <h2 className="truncate text-2xl font-semibold">{chapterTitle}</h2>
    </div>
  )
}
