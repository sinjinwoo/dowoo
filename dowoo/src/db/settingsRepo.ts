import { db } from './db'
import type { ApiSettings, ThemePreset } from '../types/settings'

const API_SETTINGS_ID = 'default'

export function getApiSettings() {
  return db.apiSettings.get(API_SETTINGS_ID)
}

export function saveApiSettings(settings: ApiSettings) {
  return db.apiSettings.put({ ...settings, id: API_SETTINGS_ID })
}

export function saveThemePreset(preset: ThemePreset) {
  return db.themePresets.put(preset)
}

export function deleteThemePreset(name: string) {
  return db.themePresets.delete(name)
}
