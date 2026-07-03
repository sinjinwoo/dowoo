import type { CSSProperties, MouseEvent, ReactNode } from 'react'

export interface AppShellProps {
  topBar: ReactNode
  children: ReactNode
  mainStyle?: CSSProperties
  topBarHidden: boolean
  onContentClick?: () => void
}

const INTERACTIVE_SELECTOR = 'button, a, input, textarea, select, [role="button"]'

export default function AppShell({ topBar, children, mainStyle, topBarHidden, onContentClick }: AppShellProps) {
  const handleClick = (e: MouseEvent<HTMLElement>) => {
    // 버튼/링크/입력 등 실제 컨트롤 클릭은 각자 동작만 하고, 빈 영역 클릭일 때만 상단바를 토글한다.
    const target = e.target as HTMLElement
    if (!target.closest(INTERACTIVE_SELECTOR)) {
      onContentClick?.()
    }
  }

  return (
    <div className="flex min-h-screen flex-col bg-white text-gray-900 dark:bg-gray-950 dark:text-gray-100">
      {topBar}
      <main
        className={`flex-1 transition-[padding-top] duration-300 sm:pt-14 ${topBarHidden ? 'pt-0' : 'pt-14'}`}
        style={mainStyle}
        onClick={handleClick}
      >
        <div className="mx-auto w-full max-w-3xl px-4 py-6">{children}</div>
      </main>
    </div>
  )
}
