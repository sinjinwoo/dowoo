import { useState } from 'react'
import type { NovelDetail } from '../../types/novel'
import type { Prompt } from '../../types/prompt'
import Modal from '../ui/Modal'
import Button from '../ui/Button'

export interface NovelMetaEditModalProps {
  isOpen: boolean
  novel: NovelDetail | null
  prompts: Prompt[]
  onClose: () => void
  onSave: (title: string, coverUrl: string, promptId: string | null) => void
}

const DEFAULT_PROMPT_OPTION_VALUE = ''

export default function NovelMetaEditModal({
  isOpen,
  novel,
  prompts,
  onClose,
  onSave,
}: NovelMetaEditModalProps) {
  const [title, setTitle] = useState('')
  const [coverUrl, setCoverUrl] = useState('')
  const [promptId, setPromptId] = useState<string | null>(null)
  const [syncedNovelId, setSyncedNovelId] = useState<string | null>(null)

  // novel prop이 바뀔 때만(다른 소설로 모달이 열릴 때) 폼을 초기화한다.
  // 렌더링 도중 상태를 조정하는 React의 권장 패턴이라 useEffect 대신 여기서 직접 처리한다.
  if (novel && novel.id !== syncedNovelId) {
    setSyncedNovelId(novel.id)
    setTitle(novel.title)
    setCoverUrl(novel.coverUrl ?? '')
    setPromptId(novel.promptId)
  }

  if (!novel) return null

  return (
    <Modal isOpen={isOpen} onClose={onClose} title="소설 정보 편집">
      <div className="space-y-3">
        <label className="block">
          <span className="mb-1 block text-sm text-gray-700 dark:text-gray-300">
            제목
          </span>
          <input
            type="text"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm dark:border-gray-700 dark:bg-gray-800"
          />
        </label>

        <label className="block">
          <span className="mb-1 block text-sm text-gray-700 dark:text-gray-300">
            표지 URL
          </span>
          <input
            type="text"
            value={coverUrl}
            onChange={(e) => setCoverUrl(e.target.value)}
            className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm dark:border-gray-700 dark:bg-gray-800"
          />
        </label>

        <label className="block">
          <span className="mb-1 block text-sm text-gray-700 dark:text-gray-300">
            프롬프트
          </span>
          <select
            value={promptId ?? DEFAULT_PROMPT_OPTION_VALUE}
            onChange={(e) => setPromptId(e.target.value === DEFAULT_PROMPT_OPTION_VALUE ? null : e.target.value)}
            className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm dark:border-gray-700 dark:bg-gray-800"
          >
            <option value={DEFAULT_PROMPT_OPTION_VALUE}>기본 프롬프트</option>
            {prompts
              .filter((p) => !p.isDefault)
              .map((p) => (
                <option key={p.id} value={p.id}>
                  {p.title}
                </option>
              ))}
          </select>
        </label>

        <div className="flex justify-end gap-2 pt-2">
          <Button variant="ghost" onClick={onClose}>
            취소
          </Button>

          <Button
            onClick={() => {
              onSave(title, coverUrl, promptId)
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
