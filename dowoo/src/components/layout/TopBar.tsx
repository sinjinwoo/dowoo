import { useState } from 'react'
import Button from '../ui/Button'

export interface TopBarProps {
  urlInput: string
  onUrlInputChange: (value: string) => void
  onSubmit: () => void
  onOpenSettings: () => void
  onOpenLibrary: () => void
  hidden: boolean
}

export default function TopBar({
  urlInput,
  onUrlInputChange,
  onSubmit,
  onOpenSettings,
  onOpenLibrary,
  hidden,
}: TopBarProps) {
  const [isMenuOpen, setIsMenuOpen] = useState(false)

  return (
    <header
        className={`fixed inset-x-0 top-0 z-40 border-b border-gray-200 bg-white shadow-sm transition-transform duration-300 dark:border-gray-800 dark:bg-gray-900 ${
          hidden ? '-translate-y-full' : 'translate-y-0'
        }`}
      >
        <div className="flex items-center gap-3 px-4 py-3">
          {/* 모바일 햄버거 */}
          <button
            type="button"
            onClick={() => setIsMenuOpen((v) => !v)}
            aria-label="메뉴"
            className="rounded-lg p-2 hover:bg-gray-100 sm:hidden dark:hover:bg-gray-800"
          >
            ☰
          </button>

          {/* 데스크톱 버튼 */}
          <div className="hidden shrink-0 items-center gap-2 sm:flex">
            <Button variant="secondary" className="px-4 py-2 shadow-sm" onClick={onOpenLibrary}>
              서재
            </Button>

            <Button variant="secondary" className="px-4 py-2 shadow-sm" onClick={onOpenSettings}>
              설정
            </Button>
          </div>

          {/* URL 입력 */}
          <form
            className="flex min-w-0 flex-1 gap-2"
            onSubmit={(e) => {
              e.preventDefault()
              onSubmit()
            }}
          >
            <input
              type="text"
              value={urlInput}
              onChange={(e) => onUrlInputChange(e.target.value)}
              placeholder="소설 URL 또는 텍스트를 입력하세요"
              className="min-w-0 flex-1 rounded-lg border border-gray-300 px-4 py-2 text-sm text-gray-900 shadow-inner focus:border-accent focus:outline-none dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100"
            />

            <Button type="submit" className="whitespace-nowrap px-5 py-2 shadow-sm">
              불러오기
            </Button>
          </form>
        </div>

        {/* 모바일 메뉴 - 좌측 정렬된 텍스트 링크 형태 */}
        {isMenuOpen && (
          <div className="flex flex-col items-start gap-1 border-t border-gray-200 bg-white px-4 py-2 sm:hidden dark:border-gray-800 dark:bg-gray-900">
            <button
              type="button"
              onClick={() => {
                onOpenLibrary()
                setIsMenuOpen(false)
              }}
              className="py-1 text-sm text-gray-700 hover:text-accent dark:text-gray-200"
            >
              서재
            </button>

            <button
              type="button"
              onClick={() => {
                onOpenSettings()
                setIsMenuOpen(false)
              }}
              className="py-1 text-sm text-gray-700 hover:text-accent dark:text-gray-200"
            >
              설정
            </button>
          </div>
        )}
    </header>
  )
}
