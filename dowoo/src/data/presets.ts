import type { ThemePreset } from '../types/settings'

export const themePresets: ThemePreset[] = [
  {
    name: '기본',
    theme: {
      fontColor: '#08060d',
      bgColor: '#ffffff',
    },
  },
  {
    name: '다크 모드',
    theme: {
      fontColor: '#e5e4e7',
      bgColor: '#16171d',
    },
  },
  {
    name: '미디엄 그린',
    theme: {
      fontColor: '#2b2b2b',
      bgColor: '#cbdec8',
    },
  },
  {
    name: '오렌지 페이퍼',
    theme: {
      fontColor: '#3a2c1a',
      bgColor: '#f6ead7',
    },
  },
]
