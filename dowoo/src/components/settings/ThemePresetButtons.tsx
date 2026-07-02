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

function PresetSwatch({
  preset,
  onClick,
  onDelete,
}: {
  preset: ThemePreset
  onClick: () => void
  onDelete?: () => void
}) {
  return (
    <div className="flex flex-col items-center gap-1">
      <button
        type="button"
        onClick={onClick}
        aria-label={preset.name}
        className="flex h-12 w-16 items-center justify-center rounded-lg border border-black/10 text-lg font-medium shadow-sm transition-transform hover:scale-105 dark:border-white/10"
        style={{ backgroundColor: preset.theme.bgColor, color: preset.theme.fontColor }}
      >
        가
      </button>
      <span className="flex items-center gap-1 text-xs text-gray-600 dark:text-gray-300">
        {preset.name}
        {onDelete && (
          <button
            type="button"
            aria-label={`${preset.name} 삭제`}
            onClick={onDelete}
            className="text-gray-400 hover:text-red-500"
          >
            ✕
          </button>
        )}
      </span>
    </div>
  )
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
      <div className="flex flex-wrap gap-3">
        {themePresets.map((preset) => (
          <PresetSwatch key={preset.name} preset={preset} onClick={() => applyPreset(preset)} />
        ))}
      </div>

      {customPresets.length > 0 && (
        <div className="flex flex-wrap gap-3">
          {customPresets.map((preset) => (
            <PresetSwatch
              key={preset.name}
              preset={preset}
              onClick={() => applyPreset(preset)}
              onDelete={() => onDeleteCustomPreset(preset.name)}
            />
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
