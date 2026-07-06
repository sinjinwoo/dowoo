import { useState } from 'react'
import type { Novel, NovelDetail } from '../../types/novel'
import type { Prompt } from '../../types/prompt'
import Drawer from '../ui/Drawer'
import Modal from '../ui/Modal'
import Button from '../ui/Button'
import LibraryList from './LibraryList'
import NovelMetaEditModal from './NovelMetaEditModal'
import PromptList from './PromptList'
import PromptEditModal from './PromptEditModal'

export interface LibraryDrawerProps {
  isOpen: boolean
  onClose: () => void
  novels: Novel[]
  prompts: Prompt[]
  onSelectChapter: (novelId: string, chapterIndex: number) => void
  onLoadNovelDetail: (novelId: string) => Promise<NovelDetail>
  onUpdateNovel: (novelId: string, title: string, coverUrl: string, promptId: string | null) => void
  onDeleteNovel: (novelId: string) => void
  onReorderNovels: (orderedIds: string[]) => void
  onDownloadNovel: (novel: Novel) => void
  onCreatePrompt: (title: string, systemPrompt: string, translationNote: string) => void
  onUpdatePrompt: (promptId: string, title: string, systemPrompt: string, translationNote: string) => void
  onDeletePrompt: (promptId: string) => void
}

type Tab = 'novels' | 'prompts'

export default function LibraryDrawer({
  isOpen,
  onClose,
  novels,
  prompts,
  onSelectChapter,
  onLoadNovelDetail,
  onUpdateNovel,
  onDeleteNovel,
  onReorderNovels,
  onDownloadNovel,
  onCreatePrompt,
  onUpdatePrompt,
  onDeletePrompt,
}: LibraryDrawerProps) {
  const [tab, setTab] = useState<Tab>('novels')
  const [editingNovel, setEditingNovel] = useState<NovelDetail | null>(null)
  const [deletingNovel, setDeletingNovel] = useState<Novel | null>(null)
  const [chapterPickerNovel, setChapterPickerNovel] = useState<NovelDetail | null>(null)
  // null = 닫힘, { prompt: null } = 새로 만들기, { prompt: X } = X 편집.
  const [promptModal, setPromptModal] = useState<{ prompt: Prompt | null } | null>(null)
  const [deletingPrompt, setDeletingPrompt] = useState<Prompt | null>(null)

  const handleEdit = async (novel: Novel) => {
    setEditingNovel(await onLoadNovelDetail(novel.id))
  }

  const handleOpenChapterPicker = async (novel: Novel) => {
    setChapterPickerNovel(await onLoadNovelDetail(novel.id))
  }

  const handlePickChapter = (novelId: string, chapterIndex: number) => {
    onSelectChapter(novelId, chapterIndex)
    setChapterPickerNovel(null)
    onClose()
  }

  return (
    <>
      <Drawer isOpen={isOpen} onClose={onClose} side="left" title="내 서재" widthClassName="w-80">
        <div className="mb-4 flex gap-1 rounded-lg bg-gray-100 p-1 dark:bg-gray-800">
          <button
            type="button"
            onClick={() => setTab('novels')}
            className={`flex-1 rounded-md py-1.5 text-sm font-medium transition-colors ${
              tab === 'novels'
                ? 'bg-white text-gray-900 shadow-sm dark:bg-gray-700 dark:text-gray-100'
                : 'text-gray-500 dark:text-gray-400'
            }`}
          >
            서재
          </button>
          <button
            type="button"
            onClick={() => setTab('prompts')}
            className={`flex-1 rounded-md py-1.5 text-sm font-medium transition-colors ${
              tab === 'prompts'
                ? 'bg-white text-gray-900 shadow-sm dark:bg-gray-700 dark:text-gray-100'
                : 'text-gray-500 dark:text-gray-400'
            }`}
          >
            프롬프트
          </button>
        </div>

        {tab === 'novels' ? (
          <LibraryList
            novels={novels}
            onSelect={(novel) => void handleOpenChapterPicker(novel)}
            onEdit={(novel) => void handleEdit(novel)}
            onDownload={onDownloadNovel}
            onDelete={setDeletingNovel}
            onReorder={onReorderNovels}
          />
        ) : (
          <PromptList
            prompts={prompts}
            onCreate={() => setPromptModal({ prompt: null })}
            onEdit={(prompt) => setPromptModal({ prompt })}
            onDelete={setDeletingPrompt}
          />
        )}
      </Drawer>

      <NovelMetaEditModal
        isOpen={editingNovel !== null}
        novel={editingNovel}
        prompts={prompts}
        onClose={() => setEditingNovel(null)}
        onSave={(title, coverUrl, promptId) => {
          if (editingNovel) {
            onUpdateNovel(editingNovel.id, title, coverUrl, promptId)
          }
        }}
      />

      <PromptEditModal
        isOpen={promptModal !== null}
        prompt={promptModal?.prompt ?? null}
        defaultSystemPrompt={prompts.find((p) => p.isDefault)?.systemPrompt ?? ''}
        onClose={() => setPromptModal(null)}
        onSave={(title, systemPrompt, translationNote) => {
          if (promptModal?.prompt) {
            onUpdatePrompt(promptModal.prompt.id, title, systemPrompt, translationNote)
          } else {
            onCreatePrompt(title, systemPrompt, translationNote)
          }
        }}
      />

      <Modal
        isOpen={chapterPickerNovel !== null}
        onClose={() => setChapterPickerNovel(null)}
        title={chapterPickerNovel ? `${chapterPickerNovel.title} - 챕터 선택` : ''}
        size="md"
      >
        {chapterPickerNovel && chapterPickerNovel.chapters.length === 0 ? (
          <p className="py-6 text-center text-sm text-gray-400">아직 불러온 챕터가 없습니다.</p>
        ) : (
          <div className="max-h-[60vh] space-y-1 overflow-y-auto">
            {chapterPickerNovel &&
              [...chapterPickerNovel.chapters]
                .sort((a, b) => b.chapterIndex - a.chapterIndex)
                .map((chapter) => (
                  <button
                    key={chapter.id}
                    type="button"
                    onClick={() => handlePickChapter(chapterPickerNovel.id, chapter.chapterIndex)}
                    className="block w-full truncate rounded-lg px-3 py-2 text-left text-sm text-gray-800 hover:bg-gray-100 dark:text-gray-100 dark:hover:bg-gray-800"
                  >
                    {chapter.title || `${chapter.chapterIndex + 1}화`}
                  </button>
                ))}
          </div>
        )}
      </Modal>

      <Modal isOpen={deletingNovel !== null} onClose={() => setDeletingNovel(null)} title="소설 삭제" size="sm">
        <p className="text-sm text-gray-700 dark:text-gray-300">
          "{deletingNovel?.title}"을(를) 서재에서 삭제할까요? 저장된 번역본도 함께 사라지며 되돌릴 수 없습니다.
        </p>
        <div className="mt-4 flex justify-end gap-2">
          <Button variant="ghost" onClick={() => setDeletingNovel(null)}>
            취소
          </Button>
          <Button
            variant="danger"
            onClick={() => {
              if (deletingNovel) onDeleteNovel(deletingNovel.id)
              setDeletingNovel(null)
            }}
          >
            삭제
          </Button>
        </div>
      </Modal>

      <Modal isOpen={deletingPrompt !== null} onClose={() => setDeletingPrompt(null)} title="프롬프트 삭제" size="sm">
        <p className="text-sm text-gray-700 dark:text-gray-300">
          "{deletingPrompt?.title}"을(를) 삭제할까요? 이 프롬프트를 쓰던 소설은 기본 프롬프트로 자동 전환됩니다.
        </p>
        <div className="mt-4 flex justify-end gap-2">
          <Button variant="ghost" onClick={() => setDeletingPrompt(null)}>
            취소
          </Button>
          <Button
            variant="danger"
            onClick={() => {
              if (deletingPrompt) onDeletePrompt(deletingPrompt.id)
              setDeletingPrompt(null)
            }}
          >
            삭제
          </Button>
        </div>
      </Modal>
    </>
  )
}
