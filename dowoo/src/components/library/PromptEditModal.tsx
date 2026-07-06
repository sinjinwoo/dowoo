import { useState } from 'react'
import type { Prompt } from '../../types/prompt'
import Modal from '../ui/Modal'
import Button from '../ui/Button'

export interface PromptEditModalProps {
  isOpen: boolean
  /** null이면 새 프롬프트 생성 모드. */
  prompt: Prompt | null
  /** 생성 모드일 때 시스템 프롬프트 란의 초기값으로 채워줄 기본 프롬프트 내용. */
  defaultSystemPrompt: string
  onClose: () => void
  onSave: (title: string, systemPrompt: string, translationNote: string) => void
}

export default function PromptEditModal({
  isOpen,
  prompt,
  defaultSystemPrompt,
  onClose,
  onSave,
}: PromptEditModalProps) {
  const [title, setTitle] = useState('')
  const [systemPrompt, setSystemPrompt] = useState('')
  const [translationNote, setTranslationNote] = useState('')
  const [syncedKey, setSyncedKey] = useState<string | null>(null)

  // 모달이 열릴 때(신규 생성 또는 다른 프롬프트 편집으로 전환될 때)만 폼을 초기화한다 -
  // NovelMetaEditModal과 같은 패턴. 생성 모드는 고정 id가 없어 'new'를 키로 쓴다.
  // 생성 모드에서는 빈 칸 대신 기본 프롬프트 내용을 채워 넣어, 처음부터 다시 쓰지 않고
  // 필요한 부분만 고쳐서 새 프롬프트를 만들 수 있게 한다.
  const key = isOpen ? (prompt?.id ?? 'new') : null
  if (isOpen && key !== syncedKey) {
    setSyncedKey(key)
    setTitle(prompt?.title ?? '')
    setSystemPrompt(prompt?.systemPrompt ?? defaultSystemPrompt)
    setTranslationNote(prompt?.translationNote ?? '')
  }

  if (!isOpen) return null

  const isDefault = prompt?.isDefault ?? false
  const canSave = isDefault || title.trim().length > 0

  return (
    <Modal isOpen={isOpen} onClose={onClose} title={prompt ? '프롬프트 편집' : '새 프롬프트'}>
      <div className="space-y-3">
        <label className="block">
          <span className="mb-1 block text-sm text-gray-700 dark:text-gray-300">제목</span>
          <input
            type="text"
            value={title}
            disabled={isDefault}
            onChange={(e) => setTitle(e.target.value)}
            className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm disabled:opacity-50 dark:border-gray-700 dark:bg-gray-800"
          />
          {isDefault && (
            <span className="mt-1 block text-xs text-gray-400">기본 프롬프트는 제목을 바꿀 수 없습니다.</span>
          )}
        </label>

        <label className="block">
          <span className="mb-1 block text-sm text-gray-700 dark:text-gray-300">시스템 프롬프트</span>
          <textarea
            rows={8}
            value={systemPrompt}
            onChange={(e) => setSystemPrompt(e.target.value)}
            className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm dark:border-gray-700 dark:bg-gray-800"
          />
        </label>

        <label className="block">
          <span className="mb-1 block text-sm text-gray-700 dark:text-gray-300">번역 메모(용어집)</span>
          <textarea
            rows={5}
            value={translationNote}
            onChange={(e) => setTranslationNote(e.target.value)}
            className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm dark:border-gray-700 dark:bg-gray-800"
          />
        </label>

        <div className="flex justify-end gap-2 pt-2">
          <Button variant="ghost" onClick={onClose}>
            취소
          </Button>
          <Button
            disabled={!canSave}
            onClick={() => {
              onSave(title, systemPrompt, translationNote)
              onClose()
            }}
          >
            저장
          </Button>
        </div>
      </div>
    </Modal>
  )
}
