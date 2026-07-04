import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react'
import {
  login as apiLogin,
  signup as apiSignup,
  logout as apiLogout,
  refreshAccessToken,
  fetchMe,
  type UserResponse,
} from '../api/auth'
import { setAccessToken, setUnauthorizedHandler } from '../api/client'

type AuthStatus = 'loading' | 'authenticated' | 'unauthenticated'

interface AuthContextValue {
  status: AuthStatus
  user: UserResponse | null
  login: (username: string, password: string) => Promise<void>
  signup: (username: string, password: string, passwordConfirm: string) => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>('loading')
  const [user, setUser] = useState<UserResponse | null>(null)

  const handleUnauthenticated = useCallback(() => {
    setAccessToken(null)
    setUser(null)
    setStatus('unauthenticated')
  }, [])

  useEffect(() => {
    setUnauthorizedHandler(handleUnauthenticated)
    return () => setUnauthorizedHandler(null)
  }, [handleUnauthenticated])

  // 앱 최초 로딩 시 리프레시 토큰 쿠키로 조용히 재로그인을 시도한다.
  useEffect(() => {
    void (async () => {
      try {
        const tokenResponse = await refreshAccessToken()
        setAccessToken(tokenResponse.accessToken)
        const me = await fetchMe()
        setUser(me)
        setStatus('authenticated')
      } catch {
        handleUnauthenticated()
      }
    })()
  }, [handleUnauthenticated])

  const login = async (username: string, password: string) => {
    const result = await apiLogin(username, password)
    setAccessToken(result.accessToken)
    setUser(result.user)
    setStatus('authenticated')
  }

  const signup = async (username: string, password: string, passwordConfirm: string) => {
    const result = await apiSignup(username, password, passwordConfirm)
    setAccessToken(result.accessToken)
    setUser(result.user)
    setStatus('authenticated')
  }

  const logout = async () => {
    try {
      await apiLogout()
    } finally {
      handleUnauthenticated()
    }
  }

  return (
    <AuthContext.Provider value={{ status, user, login, signup, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
