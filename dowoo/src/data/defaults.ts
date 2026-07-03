import type { ThemeSettings } from '../types/settings'
import { fontOptions } from './fontOptions'

export const defaultTheme: ThemeSettings = {
  fontFamily: fontOptions[0].value,
  fontColor: '#08060d',
  bgColor: '#ffffff',
  fontSize: 18,
  fontWeight: 400,
  lineHeight: 1.7,
  textIndent: 1,
}
