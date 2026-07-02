import { useEffect, useRef, useState } from 'react'

export function useHideOnScroll(topThreshold = 10, resetKey?: unknown) {
  const [hidden, setHidden] = useState(true)
  const suppressUntil = useRef(0)

  useEffect(() => {
    setHidden(true)
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

  const show = () => setHidden(false)
  const toggle = () => setHidden((h) => !h)

  return [hidden, show, toggle] as const
}
