import { apiDelete, apiGet, apiPatch, apiPost } from './client'
import type { Prompt } from '../types/prompt'

export const listPrompts = () => apiGet<Prompt[]>('/api/v1/prompts')

export const createPrompt = (input: { title: string; systemPrompt?: string; translationNote?: string }) =>
  apiPost<Prompt>('/api/v1/prompts', input)

export const updatePrompt = (
  promptId: string,
  partial: { title?: string; systemPrompt?: string; translationNote?: string }
) => apiPatch<Prompt>(`/api/v1/prompts/${promptId}`, partial)

export const deletePrompt = (promptId: string) => apiDelete(`/api/v1/prompts/${promptId}`)
