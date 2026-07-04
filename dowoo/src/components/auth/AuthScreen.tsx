import { useState, type FormEvent } from 'react'
import Button from '../ui/Button'
import Spinner from '../ui/Spinner'
import { useAuth } from '../../auth/AuthContext'
import { checkUsername } from '../../api/auth'
import { ApiRequestError } from '../../api/client'

type Mode = 'login' | 'signup'
type UsernameCheckStatus = 'idle' | 'checking' | 'available' | 'taken'

export default function AuthScreen() {
  const { login, signup } = useAuth()
  const [mode, setMode] = useState<Mode>('login')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [passwordConfirm, setPasswordConfirm] = useState('')
  const [usernameCheck, setUsernameCheck] = useState<UsernameCheckStatus>('idle')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const switchMode = (next: Mode) => {
    setMode(next)
    setError(null)
    setUsernameCheck('idle')
  }

  const handleCheckUsername = async () => {
    if (!username.trim()) return
    setUsernameCheck('checking')
    try {
      const result = await checkUsername(username.trim())
      setUsernameCheck(result.available ? 'available' : 'taken')
    } catch {
      setUsernameCheck('idle')
    }
  }

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)

    if (mode === 'signup' && password !== passwordConfirm) {
      setError('비밀번호와 비밀번호 확인이 일치하지 않습니다.')
      return
    }

    setIsSubmitting(true)
    try {
      if (mode === 'login') {
        await login(username.trim(), password)
      } else {
        await signup(username.trim(), password, passwordConfirm)
      }
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : '요청 처리 중 오류가 발생했습니다.')
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-chrome px-4 dark:bg-gray-950">
      <div className="w-full max-w-sm rounded-xl bg-chrome p-6 shadow-xl ring-1 ring-purple-100 dark:bg-gray-900 dark:ring-0">
        {mode === 'login' ? (
          <div className="mb-6 flex items-center justify-center gap-2">
            <img src="/logo.svg" alt="" className="h-14 w-auto" />
            <h1 className="text-3xl font-semibold text-accent">dowoo</h1>
          </div>
        ) : (
          <h1 className="mb-6 text-center text-xl font-semibold text-gray-900 dark:text-gray-100">
            회원가입
          </h1>
        )}

        <form onSubmit={(e) => void handleSubmit(e)} className="flex flex-col gap-3">
          <div className="flex gap-2">
            <input
              type="text"
              value={username}
              onChange={(e) => {
                setUsername(e.target.value)
                setUsernameCheck('idle')
              }}
              placeholder="아이디"
              autoComplete="username"
              className="min-w-0 flex-1 rounded-lg border border-gray-300 px-4 py-2.5 text-sm text-gray-900 shadow-inner focus:border-accent focus:outline-none dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100"
            />
            {mode === 'signup' && (
              <Button
                type="button"
                variant="secondary"
                className="shrink-0 whitespace-nowrap px-3"
                onClick={() => void handleCheckUsername()}
              >
                중복확인
              </Button>
            )}
          </div>
          {mode === 'signup' && usernameCheck !== 'idle' && (
            <p
              className={`-mt-2 text-xs ${
                usernameCheck === 'available'
                  ? 'text-green-600 dark:text-green-400'
                  : usernameCheck === 'taken'
                    ? 'text-red-600 dark:text-red-400'
                    : 'text-gray-400'
              }`}
            >
              {usernameCheck === 'checking' && '확인 중...'}
              {usernameCheck === 'available' && '사용 가능한 아이디입니다.'}
              {usernameCheck === 'taken' && '이미 사용 중인 아이디입니다.'}
            </p>
          )}

          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="비밀번호"
            autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
            className="rounded-lg border border-gray-300 px-4 py-2.5 text-sm text-gray-900 shadow-inner focus:border-accent focus:outline-none dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100"
          />

          {mode === 'signup' && (
            <input
              type="password"
              value={passwordConfirm}
              onChange={(e) => setPasswordConfirm(e.target.value)}
              placeholder="비밀번호 확인"
              autoComplete="new-password"
              className="rounded-lg border border-gray-300 px-4 py-2.5 text-sm text-gray-900 shadow-inner focus:border-accent focus:outline-none dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100"
            />
          )}

          {error && <p className="text-sm text-red-600 dark:text-red-400">{error}</p>}

          {isSubmitting ? (
            <Spinner label={mode === 'login' ? '로그인 중...' : '가입 중...'} />
          ) : (
            <Button type="submit" size="lg" className="mt-1 w-full">
              {mode === 'login' ? '로그인' : '가입하기'}
            </Button>
          )}
        </form>

        <button
          type="button"
          onClick={() => switchMode(mode === 'login' ? 'signup' : 'login')}
          className="mt-4 w-full text-center text-sm text-gray-500 hover:text-accent dark:text-gray-400"
        >
          {mode === 'login' ? '계정이 없으신가요? 회원가입' : '이미 계정이 있으신가요? 로그인'}
        </button>
      </div>
    </div>
  )
}
