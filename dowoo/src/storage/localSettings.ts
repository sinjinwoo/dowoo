const SESSION_KEY = 'dowoo:lastSession'

export interface LastSession {
  activeNovelId: string
  currentChapterIndex: number
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
