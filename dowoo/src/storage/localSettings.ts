import type { ThemeSettings } from '../types/settings'

const THEME_KEY = 'dowoo:theme'
const SESSION_KEY = 'dowoo:lastSession'

export interface LastSession {
  activeNovelId: string
  currentChapterIndex: number
}

export function loadTheme(): ThemeSettings | null {
  try {
    const raw = localStorage.getItem(THEME_KEY)
    return raw ? (JSON.parse(raw) as ThemeSettings) : null
  } catch {
    return null
  }
}

export function saveTheme(theme: ThemeSettings) {
  localStorage.setItem(THEME_KEY, JSON.stringify(theme))
}

export function loadLastSession(): LastSession | null {
  try {
    const raw = localStorage.getItem(SESSION_KEY)
    return raw ? (JSON.parse(raw) as LastSession) : null
  } catch {
    return null
  }
}

export function saveLastSession(session: LastSession) {
  localStorage.setItem(SESSION_KEY, JSON.stringify(session))
}
