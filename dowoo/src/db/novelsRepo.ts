import { db } from './db'
import { mockNovels } from '../data/mockNovels'
import type { Novel } from '../types/novel'

export async function seedNovelsIfEmpty() {
  const count = await db.novels.count()
  if (count === 0) {
    await db.novels.bulkPut(mockNovels)
  }
}

export function upsertNovel(novel: Novel) {
  return db.novels.put(novel)
}

export function patchNovel(id: string, partial: Partial<Novel>) {
  return db.novels.update(id, partial)
}

export function deleteNovel(id: string) {
  return db.novels.delete(id)
}

export async function reorderNovels(orderedIds: string[]) {
  await db.transaction('rw', db.novels, async () => {
    await Promise.all(orderedIds.map((id, index) => db.novels.update(id, { order: index })))
  })
}

export async function patchChapterText(novelId: string, chapterId: string, translatedText: string) {
  const novel = await db.novels.get(novelId)
  if (!novel) return

  const chapters = novel.chapters.map((chapter) =>
    chapter.id === chapterId ? { ...chapter, translatedText } : chapter
  )

  await db.novels.update(novelId, { chapters })
}
