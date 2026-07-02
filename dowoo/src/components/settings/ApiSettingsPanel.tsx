import type { ApiSettings } from '../../types/settings'
import Select from '../ui/Select'

export interface ApiSettingsPanelProps {
  apiSettings: ApiSettings
  onChange: (settings: ApiSettings) => void
}

const modelOptions = [
  { value: 'gemini-2.5-flash', label: 'Gemini 2.5 Flash' },
  { value: 'gemini-2.5-pro', label: 'Gemini 2.5 Pro' },
  { value: 'gemini-3-flash-preview', label: 'Gemini 3 Flash (Preview)' },
]

export default function ApiSettingsPanel({ apiSettings, onChange }: ApiSettingsPanelProps) {
  return (
    <div className="space-y-4">
      <label className="block">
        <span className="mb-1 block text-sm text-gray-700 dark:text-gray-300">
          Gemini API 키 (한 줄에 하나씩 입력하면 자동 로테이션)
        </span>
        <textarea
          rows={4}
          value={apiSettings.apiKeys.join('\n')}
          onChange={(e) => onChange({ ...apiSettings, apiKeys: e.target.value.split('\n') })}
          placeholder="AIzaSy..."
          className="w-full rounded-lg border border-gray-300 px-3 py-2 font-mono text-xs text-gray-900 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100"
        />
      </label>

      <Select
        label="번역 모델"
        value={apiSettings.model}
        options={modelOptions}
        onChange={(model) => onChange({ ...apiSettings, model })}
      />
    </div>
  )
}
