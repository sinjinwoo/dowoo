export interface Prompt {
  id: string
  title: string
  systemPrompt: string | null
  translationNote: string | null
  isDefault: boolean
  createdAt: string
  updatedAt: string
}
