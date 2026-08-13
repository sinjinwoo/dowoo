import { apiDelete, apiGet, apiPost, apiPut } from './client'
import type { ThemeSettings, ThemePreset } from '../types/settings'

export interface MaskedApiKey {
  id: string
  masked: string
  order: number
}

export interface ApiSettingsResponse {
  model: string | null
  thinkingBudget: number | null
  modelOrder: string[]
  apiKeys: MaskedApiKey[]
}

export const getApiSettings = () => apiGet<ApiSettingsResponse>('/api/v1/settings/api')

// modelOrder를 생략하면(undefined) 서버가 기존에 저장된 순서를 그대로 둔다 - model만 바꾸는
// 요청(BadgeSelect onChange)이 매번 순서까지 같이 안 보내도 커스텀 순서가 초기화되지 않는다.
export const saveModelSettings = (model: string, thinkingBudget?: number, modelOrder?: string[]) =>
  apiPut<ApiSettingsResponse>('/api/v1/settings/api', { model, thinkingBudget, modelOrder })

export const addApiKey = (apiKey: string) =>
  apiPost<ApiSettingsResponse>('/api/v1/settings/api/keys', { apiKey })

export const addApiKeys = (apiKeys: string[]) =>
  apiPost<ApiSettingsResponse>('/api/v1/settings/api/keys/bulk', { apiKeys })

export const deleteApiKey = (keyId: string) => apiDelete(`/api/v1/settings/api/keys/${keyId}`)

export const getTheme = () => apiGet<ThemeSettings>('/api/v1/settings/theme')

export const saveTheme = (theme: ThemeSettings) => apiPut<ThemeSettings>('/api/v1/settings/theme', theme)

export const listThemePresets = () => apiGet<ThemePreset[]>('/api/v1/settings/theme/presets')

export const saveThemePreset = (preset: ThemePreset) =>
  apiPost<ThemePreset>('/api/v1/settings/theme/presets', preset)

export const deleteThemePreset = (name: string) =>
  apiDelete(`/api/v1/settings/theme/presets/${encodeURIComponent(name)}`)
