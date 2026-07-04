import { useState } from 'react'
import type { Novel, NovelDetail } from '../../types/novel'
import Drawer from '../ui/Drawer'
import Modal from '../ui/Modal'
import Button from '../ui/Button'
import LibraryList from './LibraryList'
import NovelMetaEditModal from './NovelMetaEditModal'

export interface LibraryDrawerProps {
  isOpen: boolean
  onClose: () => void
  novels: Novel[]
  onSelectChapter: (novelId: string, chapterIndex: number) => void
  onLoadNovelDetail: (novelId: string) => Promise<NovelDetail>
  onUpdateNovel: (
    novelId: string,
    title: string,
    coverUrl: string,
    systemPrompt: string,
    translationNote: string
  ) => void
  onDeleteNovel: (novelId: string) => void
  onReorderNovels: (orderedIds: string[]) => void
  onDownloadNovel: (novel: Novel) => void
}

export default function LibraryDrawer({
  isOpen,
  onClose,
  novels,
  onSelectChapter,
  onLoadNovelDetail,
  onUpdateNovel,
  onDeleteNovel,
  onReorderNovels,
  onDownloadNovel,
}: LibraryDrawerProps) {
  const [editingNovel, setEditingNovel] = useState<NovelDetail | null>(null)
  const [deletingNovel, setDeletingNovel] = useState<Novel | null>(null)
  const [chapterPickerNovel, setChapterPickerNovel] = useState<NovelDetail | null>(null)

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
        <LibraryList
          novels={novels}
          onSelect={(novel) => void handleOpenChapterPicker(novel)}
          onEdit={(novel) => void handleEdit(novel)}
          onDownload={onDownloadNovel}
          onDelete={setDeletingNovel}
          onReorder={onReorderNovels}
        />
      </Drawer>

      <NovelMetaEditModal
        isOpen={editingNovel !== null}
        novel={editingNovel}
        onClose={() => setEditingNovel(null)}
        onSave={(title, coverUrl, systemPrompt, translationNote) => {
          if (editingNovel) {
            onUpdateNovel(editingNovel.id, title, coverUrl, systemPrompt, translationNote)
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
    </>
  )
}
