import { apiGet, apiPost } from './client'

export interface UserResponse {
  id: string
  username: string
  createdAt: string
}

export interface AuthResponse {
  accessToken: string
  accessTokenExpiresIn: number
  user: UserResponse
}

export interface AccessTokenResponse {
  accessToken: string
  accessTokenExpiresIn: number
}

export interface UsernameAvailability {
  username: string
  available: boolean
}

export const checkUsername = (username: string) =>
  apiGet<UsernameAvailability>(`/api/v1/auth/check-username?username=${encodeURIComponent(username)}`)

export const signup = (username: string, password: string, passwordConfirm: string) =>
  apiPost<AuthResponse>('/api/v1/auth/signup', { username, password, passwordConfirm })

export const login = (username: string, password: string) =>
  apiPost<AuthResponse>('/api/v1/auth/login', { username, password })

export const refreshAccessToken = () => apiPost<AccessTokenResponse>('/api/v1/auth/refresh')

export const logout = () => apiPost<void>('/api/v1/auth/logout')

export const fetchMe = () => apiGet<UserResponse>('/api/v1/users/me')
