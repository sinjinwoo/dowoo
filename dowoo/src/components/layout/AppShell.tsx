import type { CSSProperties, ReactNode } from 'react'

export interface AppShellProps {
  topBar: ReactNode
  children: ReactNode
  mainStyle?: CSSProperties
  topBarHidden: boolean
}

export default function AppShell({ topBar, children, mainStyle, topBarHidden }: AppShellProps) {
  return (
    <div className="flex min-h-screen flex-col bg-white text-gray-900 dark:bg-gray-950 dark:text-gray-100">
      {topBar}
      <main
        className={`flex-1 transition-[padding-top] duration-300 sm:pt-14 ${topBarHidden ? 'pt-0' : 'pt-14'}`}
        style={mainStyle}
      >
        <div className="mx-auto w-full max-w-3xl px-4 py-6">{children}</div>
      </main>
    </div>
  )
}
