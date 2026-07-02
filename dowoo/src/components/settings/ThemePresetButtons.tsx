import { useState } from 'react'
import type { ThemePreset, ThemeSettings } from '../../types/settings'
import Button from '../ui/Button'
import { themePresets } from '../../data/presets'

export interface ThemePresetButtonsProps {
  theme: ThemeSettings
  onChange: (theme: ThemeSettings) => void
  customPresets: ThemePreset[]
  onSaveCustomPreset: (name: string) => void
  onDeleteCustomPreset: (name: string) => void
}

export default function ThemePresetButtons({
  theme,
  onChange,
  customPresets,
  onSaveCustomPreset,
  onDeleteCustomPreset,
}: ThemePresetButtonsProps) {
  const [newPresetName, setNewPresetName] = useState('')
  const applyPreset = (preset: ThemePreset) => onChange({ ...theme, ...preset.theme })

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap gap-2">
        {themePresets.map((preset) => (
          <Button key={preset.name} variant="secondary" size="sm" onClick={() => applyPreset(preset)}>
            {preset.name}
          </Button>
        ))}
      </div>

      {customPresets.length > 0 && (
        <div className="flex flex-wrap gap-2">
          {customPresets.map((preset) => (
            <span key={preset.name} className="inline-flex items-center gap-1">
              <Button variant="secondary" size="sm" onClick={() => applyPreset(preset)}>
                {preset.name}
              </Button>
              <button
                type="button"
                aria-label={`${preset.name} 삭제`}
                onClick={() => onDeleteCustomPreset(preset.name)}
                className="text-xs text-gray-400 hover:text-red-500"
              >
                ✕
              </button>
            </span>
          ))}
        </div>
      )}

      <form
        className="flex gap-2"
        onSubmit={(e) => {
          e.preventDefault()
          if (!newPresetName.trim()) return
          onSaveCustomPreset(newPresetName.trim())
          setNewPresetName('')
        }}
      >
        <input
          type="text"
          value={newPresetName}
          onChange={(e) => setNewPresetName(e.target.value)}
          placeholder="현재 테마를 이름 붙여 저장"
          className="min-w-0 flex-1 rounded-lg border border-gray-300 px-3 py-1.5 text-sm dark:border-gray-700 dark:bg-gray-800"
        />
        <Button type="submit" size="sm" variant="secondary">
          저장
        </Button>
      </form>
    </div>
  )
}
