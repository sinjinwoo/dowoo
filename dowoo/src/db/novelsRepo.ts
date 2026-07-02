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
