import { useState } from 'react'
import type { MaskedApiKey } from '../../api/settings'
import Select from '../ui/Select'
import Button from '../ui/Button'

export interface ApiSettingsPanelProps {
  model: string
  apiKeys: MaskedApiKey[]
  onModelChange: (model: string) => void
  onAddKey: (key: string) => void
  onDeleteKey: (keyId: string) => void
}

const modelOptions = [
  { value: 'gemini-2.5-flash', label: 'Gemini 2.5 Flash' },
  { value: 'gemini-2.5-pro', label: 'Gemini 2.5 Pro' },
  { value: 'gemini-3-flash-preview', label: 'Gemini 3 Flash (Preview)' },
]

export default function ApiSettingsPanel({ model, apiKeys, onModelChange, onAddKey, onDeleteKey }: ApiSettingsPanelProps) {
  const [newKeyInput, setNewKeyInput] = useState('')

  const handleAdd = () => {
    const key = newKeyInput.trim()
    if (!key) return
    onAddKey(key)
    setNewKeyInput('')
  }

  return (
    <div className="space-y-4">
      <div>
        <span className="mb-1 block text-sm text-gray-700 dark:text-gray-300">
          등록된 Gemini API 키 (순서대로 자동 로테이션)
        </span>
        {apiKeys.length === 0 ? (
          <p className="text-xs text-gray-400">등록된 키가 없습니다. 아래에서 추가해주세요.</p>
        ) : (
          <ul className="space-y-1">
            {apiKeys.map((key) => (
              <li
                key={key.id}
                className="flex items-center justify-between rounded-lg border border-gray-300 px-3 py-2 font-mono text-xs text-gray-900 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100"
              >
                <span>{key.masked}</span>
                <button
                  type="button"
                  onClick={() => onDeleteKey(key.id)}
                  aria-label="키 삭제"
                  className="text-gray-400 hover:text-red-500 dark:text-gray-500 dark:hover:text-red-400"
                >
                  삭제
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>

      <label className="block">
        <span className="mb-1 block text-sm text-gray-700 dark:text-gray-300">새 API 키 추가</span>
        <div className="flex gap-2">
          <input
            type="text"
            value={newKeyInput}
            onChange={(e) => setNewKeyInput(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') handleAdd()
            }}
            placeholder="AIzaSy..."
            className="w-full rounded-lg border border-gray-300 px-3 py-2 font-mono text-xs text-gray-900 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100"
          />
          <Button onClick={handleAdd}>추가</Button>
        </div>
      </label>

      <Select label="번역 모델" value={model} options={modelOptions} onChange={onModelChange} />
    </div>
  )
}
