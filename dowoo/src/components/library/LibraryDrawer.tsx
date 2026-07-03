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
  onSelectNovel: (novel: Novel) => void
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
  onSelectNovel,
  onLoadNovelDetail,
  onUpdateNovel,
  onDeleteNovel,
  onReorderNovels,
  onDownloadNovel,
}: LibraryDrawerProps) {
  const [editingNovel, setEditingNovel] = useState<NovelDetail | null>(null)
  const [deletingNovel, setDeletingNovel] = useState<Novel | null>(null)

  const handleEdit = async (novel: Novel) => {
    setEditingNovel(await onLoadNovelDetail(novel.id))
  }

  return (
    <>
      <Drawer isOpen={isOpen} onClose={onClose} side="left" title="내 서재" widthClassName="w-80">
        <LibraryList
          novels={novels}
          onSelect={onSelectNovel}
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
