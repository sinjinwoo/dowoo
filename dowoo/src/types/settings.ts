export interface ApiSettings {
  apiKeys: string[]
  model: string
  thinkingBudget?: number
}

export interface ThemeSettings {
  fontFamily: string
  fontColor: string
  bgColor: string
  fontSize: number
  fontWeight: number
  lineHeight: number
  textIndent: number
}

export interface ThemePreset {
  name: string
  theme: Partial<ThemeSettings>
}
