import { useState, useEffect } from 'react'
import type { NovelDetail } from '../../types/novel'
import Modal from '../ui/Modal'
import Button from '../ui/Button'

export interface NovelMetaEditModalProps {
  isOpen: boolean
  novel: NovelDetail | null
  onClose: () => void
  onSave: (
    title: string,
    coverUrl: string,
    systemPrompt: string,
    translationNote: string
  ) => void
}

export default function NovelMetaEditModal({
  isOpen,
  novel,
  onClose,
  onSave,
}: NovelMetaEditModalProps) {
  const [title, setTitle] = useState('')
  const [coverUrl, setCoverUrl] = useState('')
  const [systemPrompt, setSystemPrompt] = useState('')
  const [translationNote, setTranslationNote] = useState('')

  useEffect(() => {
    if (novel) {
      setTitle(novel.title)
      setCoverUrl(novel.coverUrl ?? '')
      setSystemPrompt(novel.systemPrompt ?? '')
      setTranslationNote(novel.translationNote ?? '')
    }
  }, [novel])

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
            시스템 프롬프트
          </span>
          <textarea
            rows={5}
            value={systemPrompt}
            onChange={(e) => setSystemPrompt(e.target.value)}
            className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm dark:border-gray-700 dark:bg-gray-800"
          />
        </label>

        <label className="block">
          <span className="mb-1 block text-sm text-gray-700 dark:text-gray-300">
            번역 메모
          </span>
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
            onClick={() => {
              onSave(title, coverUrl, systemPrompt, translationNote)
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