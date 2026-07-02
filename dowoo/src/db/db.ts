import Dexie, { type Table } from 'dexie'
import type { Novel } from '../types/novel'
import type { ApiSettings, ThemePreset } from '../types/settings'

export interface StoredApiSettings extends ApiSettings {
  id: 'default'
}

class AppDatabase extends Dexie {
  novels!: Table<Novel, string>
  apiSettings!: Table<StoredApiSettings, string>
  themePresets!: Table<ThemePreset, string>

  constructor() {
    super('dowoo')
    this.version(1).stores({
      novels: 'id',
      apiSettings: 'id',
      themePresets: 'name',
    })
  }
}

export const db = new AppDatabase()
