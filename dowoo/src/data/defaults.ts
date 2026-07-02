import type { ApiSettings, ThemeSettings } from '../types/settings'
import { fontOptions } from './fontOptions'

export const defaultApiSettings: ApiSettings = {
  apiKeys: [],
  model: 'gemini-2.5-flash',
}

export const defaultTheme: ThemeSettings = {
  fontFamily: fontOptions[0].value,
  fontColor: '#08060d',
  bgColor: '#ffffff',
  fontSize: 18,
  fontWeight: 400,
  lineHeight: 1.7,
  textIndent: 1,
}

export const defaultSystemPrompt =
  '당신은 전문 웹소설 번역가입니다. 아래 원문을 자연스러운 한국어로 번역하세요. 문체와 어조를 원문에 맞게 유지하고, 등장인물의 말투 차이를 살려주세요.\n\n{{memo}}'
