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
  apiKeys: MaskedApiKey[]
}

export const getApiSettings = () => apiGet<ApiSettingsResponse>('/api/v1/settings/api')

export const saveModelSettings = (model: string, thinkingBudget?: number) =>
  apiPut<ApiSettingsResponse>('/api/v1/settings/api', { model, thinkingBudget })

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
