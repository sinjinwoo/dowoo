import { useState } from 'react'
import type { Novel } from '../../types/novel'
import Drawer from '../ui/Drawer'
import LibraryList from './LibraryList'
import NovelMetaEditModal from './NovelMetaEditModal'

export interface LibraryDrawerProps {
  isOpen: boolean
  onClose: () => void
  novels: Novel[]
  onSelectNovel: (novel: Novel) => void
  onUpdateNovel: (
    novelId: string,
    title: string,
    coverUrl: string,
    systemPrompt: string,
    translationNote: string
  ) => void
  onDownloadNovel: (novel: Novel) => void
}

export default function LibraryDrawer({
  isOpen,
  onClose,
  novels,
  onSelectNovel,
  onUpdateNovel,
  onDownloadNovel,
}: LibraryDrawerProps) {
  const [editingNovel, setEditingNovel] = useState<Novel | null>(null)

  return (
    <>
      <Drawer
        isOpen={isOpen}
        onClose={onClose}
        side="left"
        title="내 서재"
        widthClassName="w-80"
      >
        <LibraryList
          novels={novels}
          onSelect={onSelectNovel}
          onEdit={setEditingNovel}
          onDownload={onDownloadNovel}
        />
      </Drawer>

      <NovelMetaEditModal
        isOpen={editingNovel !== null}
        novel={editingNovel}
        onClose={() => setEditingNovel(null)}
        onSave={(title, coverUrl, systemPrompt, translationNote) => {
          if (editingNovel) {
            onUpdateNovel(
              editingNovel.id,
              title,
              coverUrl,
              systemPrompt,
              translationNote
            )
          }
        }}
      />
    </>
  )
}