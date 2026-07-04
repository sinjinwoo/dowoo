import { useEffect, useRef, useState } from 'react'

export function useHideOnScroll(topThreshold = 10, resetKey?: unknown) {
  const [hidden, setHidden] = useState(true)
  const suppressUntil = useRef(0)
  const [prevResetKey, setPrevResetKey] = useState(resetKey)

  // resetKey가 바뀔 때만(새 챕터 로딩 등) 즉시 보이도록 초기화한다.
  // 렌더링 도중 상태를 조정하는 React의 권장 패턴이라 useEffect 대신 여기서 직접 처리한다.
  // ref 갱신(suppressUntil)과 Date.now() 호출은 렌더링 중에 할 수 없으므로 아래 useEffect로 분리한다.
  if (prevResetKey !== resetKey) {
    setPrevResetKey(resetKey)
    setHidden(true)
  }

  useEffect(() => {
    suppressUntil.current = Date.now() + 400
  }, [resetKey])

  useEffect(() => {
    const handleScroll = () => {
      if (Date.now() < suppressUntil.current) return
      setHidden(window.scrollY > topThreshold)
    }

    window.addEventListener('scroll', handleScroll, { passive: true })
    return () => window.removeEventListener('scroll', handleScroll)
  }, [topThreshold])

  const toggle = () => setHidden((h) => !h)

  return [hidden, toggle] as const
}
