import type { ThemePreset, ThemeSettings } from '../../types/settings'
import Slider from '../ui/Slider'
import ColorSwatchPicker from '../ui/ColorSwatchPicker'
import FontPicker from './FontPicker'
import ThemePresetButtons from './ThemePresetButtons'
import { fontOptions } from '../../data/fontOptions'

export interface ThemeSettingsPanelProps {
  theme: ThemeSettings
  onChange: (theme: ThemeSettings) => void
  customPresets: ThemePreset[]
  onSaveCustomPreset: (name: string) => void
  onDeleteCustomPreset: (name: string) => void
}

export default function ThemeSettingsPanel({
  theme,
  onChange,
  customPresets,
  onSaveCustomPreset,
  onDeleteCustomPreset,
}: ThemeSettingsPanelProps) {
  const set = <K extends keyof ThemeSettings>(key: K, value: ThemeSettings[K]) =>
    onChange({ ...theme, [key]: value })

  return (
    <div className="space-y-4">
      <ThemePresetButtons
        theme={theme}
        onChange={onChange}
        customPresets={customPresets}
        onSaveCustomPreset={onSaveCustomPreset}
        onDeleteCustomPreset={onDeleteCustomPreset}
      />

      <FontPicker value={theme.fontFamily} options={fontOptions} onChange={(value) => set('fontFamily', value)} />

      <ColorSwatchPicker label="글자 색상" value={theme.fontColor} onChange={(v) => set('fontColor', v)} />
      <ColorSwatchPicker label="배경 색상" value={theme.bgColor} onChange={(v) => set('bgColor', v)} />

      <Slider
        label="글자 크기"
        value={theme.fontSize}
        min={12}
        max={32}
        unit="px"
        onChange={(v) => set('fontSize', v)}
      />
      <Slider
        label="글자 두께"
        value={theme.fontWeight}
        min={300}
        max={800}
        step={100}
        onChange={(v) => set('fontWeight', v)}
      />
      <Slider
        label="줄 간격"
        value={theme.lineHeight}
        min={1}
        max={3}
        step={0.1}
        onChange={(v) => set('lineHeight', v)}
      />
      <Slider
        label="들여쓰기"
        value={theme.textIndent}
        min={0}
        max={3}
        step={0.5}
        unit="em"
        onChange={(v) => set('textIndent', v)}
      />
    </div>
  )
}
