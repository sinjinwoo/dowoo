import { useState } from 'react'
import type { MaskedApiKey } from '../../api/settings'
import BadgeSelect, { type BadgeSelectOption } from '../ui/BadgeSelect'
import Button from '../ui/Button'

export interface ApiSettingsPanelProps {
  model: string
  apiKeys: MaskedApiKey[]
  onModelChange: (model: string) => void
  onAddKey: (key: string) => void
  onAddKeys: (keys: string[]) => void
  onDeleteKey: (keyId: string) => void
}

// "gemini-3-flash"(비-preview 이름)는 아직 API에 존재하지 않아 404가 나므로 반드시
// "gemini-3-flash-preview"를 써야 한다(2026-07-05 확인, TranslateService.DEFAULT_MODEL_FALLBACK 참고).
// "자동"을 고르면 서버가 gemini-3.1-flash-lite → gemini-3-flash-preview → gemini-2.5-flash →
// gemini-3.5-flash 순서로(키 로테이션을 모델마다 전부 소진한 뒤) 시도한다. 특정 모델을 고르면
// 그 모델만 시도하고 실패하면 끝(Pro 계열은 자동 목록에 없으므로 직접 선택해야 사용된다).
const modelOptions: BadgeSelectOption[] = [
  { value: '', label: '자동 (무료 모델 순서대로 시도)' },
  { value: 'gemini-2.5-flash', label: 'Gemini 2.5 Flash', badge: { text: '무료', tone: 'free' } },
  { value: 'gemini-2.5-flash-lite', label: 'Gemini 2.5 Flash Lite', badge: { text: '무료', tone: 'free' } },
  { value: 'gemini-2.5-pro', label: 'Gemini 2.5 Pro', badge: { text: '유료', tone: 'paid' } },
  { value: 'gemini-3-flash-preview', label: 'Gemini 3 Flash Preview', badge: { text: '무료', tone: 'free' } },
  { value: 'gemini-3.1-pro', label: 'Gemini 3.1 Pro', badge: { text: '유료', tone: 'paid' } },
  { value: 'gemini-3.1-flash-lite', label: 'Gemini 3.1 Flash Lite', badge: { text: '무료', tone: 'free' } },
  { value: 'gemini-3.5-flash', label: 'Gemini 3.5 Flash', badge: { text: '무료', tone: 'free' } },
]

export default function ApiSettingsPanel({
  model,
  apiKeys,
  onModelChange,
  onAddKey,
  onAddKeys,
  onDeleteKey,
}: ApiSettingsPanelProps) {
  const [newKeyInput, setNewKeyInput] = useState('')
  const [bulkKeyInput, setBulkKeyInput] = useState('')

  const handleAdd = () => {
    const key = newKeyInput.trim()
    if (!key) return
    onAddKey(key)
    setNewKeyInput('')
  }

  const handleBulkAdd = () => {
    const keys = bulkKeyInput
      .split('\n')
      .map((line) => line.trim())
      .filter(Boolean)
    if (keys.length === 0) return
    onAddKeys(keys)
    setBulkKeyInput('')
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
                className="flex items-center justify-between gap-2 rounded-lg border border-gray-300 px-3 py-2 font-mono text-xs text-gray-900 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100"
              >
                <span className="min-w-0 flex-1 truncate">{key.masked}</span>
                <button
                  type="button"
                  onClick={() => onDeleteKey(key.id)}
                  aria-label="키 삭제"
                  className="shrink-0 text-gray-400 hover:text-red-500 dark:text-gray-500 dark:hover:text-red-400"
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

      <label className="block">
        <span className="mb-1 block text-sm text-gray-700 dark:text-gray-300">
          API 키 여러 개 한 번에 추가 (줄바꿈으로 구분)
        </span>
        <textarea
          value={bulkKeyInput}
          onChange={(e) => setBulkKeyInput(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) handleBulkAdd()
          }}
          placeholder={'AIzaSy...\nAIzaSy...\nAIzaSy...'}
          rows={3}
          className="w-full resize-y rounded-lg border border-gray-300 px-3 py-2 font-mono text-xs text-gray-900 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100"
        />
        <Button className="mt-2 w-full" onClick={handleBulkAdd}>
          한 번에 추가
        </Button>
      </label>

      <BadgeSelect label="번역 모델" value={model} options={modelOptions} onChange={onModelChange} />
    </div>
  )
}
