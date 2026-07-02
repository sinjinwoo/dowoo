import { useEffect, useRef, useState } from 'react'
import { useLiveQuery } from 'dexie-react-hooks'
import AppShell from './components/layout/AppShell'
import TopBar from './components/layout/TopBar'
import ChapterNavBar from './components/layout/ChapterNavBar'
import NovelViewer from './components/viewer/NovelViewer'
import SettingsDrawer from './components/settings/SettingsDrawer'
import LibraryDrawer from './components/library/LibraryDrawer'
import ErrorModal from './components/ui/ErrorModal'
import { db } from './db/db'
import {
  seedNovelsIfEmpty,
  patchNovel,
  patchChapterText,
  upsertNovel,
  deleteNovel,
  reorderNovels,
} from './db/novelsRepo'
import { getApiSettings, saveApiSettings, saveThemePreset, deleteThemePreset } from './db/settingsRepo'
import { loadTheme, saveTheme, loadLastSession, saveLastSession } from './storage/localSettings'
import { defaultApiSettings, defaultTheme, defaultSystemPrompt } from './data/defaults'
import { useHideOnScroll } from './hooks/useHideOnScroll'
import { translateStream, TranslationAbortedError } from './ai/geminiClient'
import { crawlChapter, isSupportedUrl } from './crawl/registry'
import type { ThemeSettings } from './types/settings'
import type { Novel } from './types/novel'

type TranslationError = { type: 'crawling' | 'gemini'; message: string }

function App() {
  useEffect(() => {
    void seedNovelsIfEmpty()
    void (async () => {
      const existing = await getApiSettings()
      if (!existing) await saveApiSettings(defaultApiSettings)
    })()
  }, [])

  const novelsQuery = useLiveQuery(() => db.novels.toArray(), [])
  const apiSettingsQuery = useLiveQuery(() => getApiSettings(), [])
  const customThemePresetsQuery = useLiveQuery(() => db.themePresets.toArray(), [])

  const novels = (novelsQuery ?? []).slice().sort((a, b) => a.order - b.order)
  const apiSettings = apiSettingsQuery ?? defaultApiSettings
  const customThemePresets = customThemePresetsQuery ?? []

  const [lastSession] = useState(() => loadLastSession())
  const [activeNovelId, setActiveNovelId] = useState(lastSession?.activeNovelId ?? '')
  const [currentChapterIndex, setCurrentChapterIndex] = useState(lastSession?.currentChapterIndex ?? 0)
  const [urlInput, setUrlInput] = useState('')

  const [isSettingsOpen, setIsSettingsOpen] = useState(false)
  const [isLibraryOpen, setIsLibraryOpen] = useState(false)

  const [theme, setTheme] = useState<ThemeSettings>(() => loadTheme() ?? defaultTheme)

  const [translationStatus, setTranslationStatus] = useState<'idle' | 'translating'>('idle')
  const [translationError, setTranslationError] = useState<TranslationError | null>(null)
  const [streamingChapterId, setStreamingChapterId] = useState<string | null>(null)
  const [streamingText, setStreamingText] = useState('')
  const [streamingTotalChars, setStreamingTotalChars] = useState(0)
  const abortControllerRef = useRef<AbortController | null>(null)

  const activeNovel = novels.find((n) => n.id === activeNovelId) ?? novels[0]
  const chapter = activeNovel?.chapters[currentChapterIndex]

  useEffect(() => {
    if (!lastSession && activeNovel) {
      setActiveNovelId(activeNovel.id)
      setCurrentChapterIndex(activeNovel.lastReadChapterIndex)
    }
  }, [activeNovel, lastSession])

  useEffect(() => {
    if (activeNovel) {
      saveLastSession({ activeNovelId: activeNovel.id, currentChapterIndex })
    }
  }, [activeNovel?.id, currentChapterIndex])

  useEffect(() => {
    if (chapter) setUrlInput(chapter.sourceUrl)
  }, [chapter])

  useEffect(() => {
    saveTheme(theme)
  }, [theme])

  const [topBarHidden, showTopBar] = useHideOnScroll(10, urlInput)

  const handleCancelTranslation = () => {
    abortControllerRef.current?.abort()
  }

  const handleSubmit = async () => {
    const input = urlInput.trim()
    if (!input || translationStatus === 'translating') return

    setTranslationError(null)

    // 이미 서재(IndexedDB)에 같은 URL/원문으로 저장된 소설이 있으면 새로 만들지 않고 그걸 바로 보여줌
    const existingNovel = novels.find(
      (n) =>
        (n.sourceUrl && n.sourceUrl === input) ||
        n.chapters.some((c) => c.sourceUrl === input || c.originalText === input)
    )
    if (existingNovel) {
      const existingChapterIndex = existingNovel.chapters.findIndex(
        (c) => c.sourceUrl === input || c.originalText === input
      )
      setActiveNovelId(existingNovel.id)
      setCurrentChapterIndex(Math.max(0, existingChapterIndex))
      return
    }

    const isUrl = /^https?:\/\//i.test(input)

    let originalText: string
    let chapterTitle: string
    let chapterSourceUrl = ''
    let prevUrl: string | null = null
    let nextUrl: string | null = null
    let siteName = '직접 입력'

    if (isUrl) {
      if (!isSupportedUrl(input)) {
        setTranslationError({ type: 'crawling', message: '지원하지 않는 사이트 주소입니다.' })
        return
      }

      try {
        const parsed = await crawlChapter(input)
        originalText = parsed.content
        chapterTitle = parsed.title || '제목 미상'
        chapterSourceUrl = input
        prevUrl = parsed.prevUrl
        nextUrl = parsed.nextUrl
        siteName = new URL(input).hostname
      } catch (error) {
        setTranslationError({
          type: 'crawling',
          message: error instanceof Error ? error.message : String(error),
        })
        return
      }
    } else {
      originalText = input
      const firstLine = input
        .split('\n')
        .map((line) => line.trim())
        .find(Boolean)
      chapterTitle = (firstLine ?? '제목 미상').slice(0, 60)
    }

    const newNovelId = crypto.randomUUID()
    const newChapterId = crypto.randomUUID()

    const nextOrder = novels.length > 0 ? Math.max(...novels.map((n) => n.order)) + 1 : 0

    const newNovel: Novel = {
      id: newNovelId,
      title: chapterTitle,
      sourceUrl: chapterSourceUrl,
      siteName,
      translationNote: '',
      systemPrompt: defaultSystemPrompt,
      lastReadChapterIndex: 0,
      order: nextOrder,
      chapters: [
        {
          id: newChapterId,
          title: chapterTitle,
          sourceUrl: chapterSourceUrl,
          originalText,
          translatedText: '',
          prevUrl,
          nextUrl,
        },
      ],
    }

    await upsertNovel(newNovel)
    setActiveNovelId(newNovelId)
    setCurrentChapterIndex(0)

    const controller = new AbortController()
    abortControllerRef.current = controller

    setTranslationStatus('translating')
    setStreamingChapterId(newChapterId)
    setStreamingText('')
    setStreamingTotalChars(originalText.length)

    let accumulatedText = ''

    try {
      await translateStream({
        apiKeys: apiSettings.apiKeys,
        model: apiSettings.model,
        systemPrompt: newNovel.systemPrompt,
        translationNote: newNovel.translationNote,
        originalText,
        signal: controller.signal,
        onLine: (line) => {
          accumulatedText = accumulatedText ? `${accumulatedText}\n${line}` : line
          setStreamingText(accumulatedText)
        },
      })

      await patchChapterText(newNovelId, newChapterId, accumulatedText)
    } catch (error) {
      // 취소되었거나 도중에 실패해도, 그때까지 번역된 내용은 그대로 저장해 둔다
      if (accumulatedText) {
        await patchChapterText(newNovelId, newChapterId, accumulatedText)
      }

      if (!(error instanceof TranslationAbortedError)) {
        setTranslationError({
          type: 'gemini',
          message: error instanceof Error ? error.message : String(error),
        })
      }
    } finally {
      setTranslationStatus('idle')
      abortControllerRef.current = null
    }
  }

  if (novelsQuery === undefined) {
    return <div className="flex min-h-screen items-center justify-center text-gray-500">불러오는 중...</div>
  }

  const displayedTranslatedText =
    chapter && chapter.id === streamingChapterId && streamingText.length > 0 ? streamingText : undefined

  const translationProgress =
    translationStatus === 'translating' && streamingTotalChars > 0
      ? Math.min(99, (streamingText.length / streamingTotalChars) * 100)
      : 0

  return (
    <AppShell
      topBar={
        <TopBar
          urlInput={urlInput}
          onUrlInputChange={setUrlInput}
          onSubmit={() => void handleSubmit()}
          onOpenSettings={() => setIsSettingsOpen(true)}
          onOpenLibrary={() => setIsLibraryOpen(true)}
          hidden={topBarHidden}
          onShow={showTopBar}
          isTranslating={translationStatus === 'translating'}
          onCancelTranslation={handleCancelTranslation}
        />
      }
      topBarHidden={topBarHidden}
      mainStyle={{ backgroundColor: theme.bgColor, color: theme.fontColor }}
    >
      {activeNovel && chapter ? (
        <>
          <NovelViewer
            chapter={chapter}
            theme={theme}
            progress={translationProgress}
            displayedTranslatedText={displayedTranslatedText}
            isTranslating={translationStatus === 'translating' && chapter.id === streamingChapterId}
          />

          <ChapterNavBar
            onPrevChapter={() => setCurrentChapterIndex((i) => Math.max(0, i - 1))}
            onNextChapter={() =>
              setCurrentChapterIndex((i) => Math.min(activeNovel.chapters.length - 1, i + 1))
            }
          />
        </>
      ) : (
        <p className="py-20 text-center text-gray-400">
          서재가 비어 있습니다. 상단바에 번역할 텍스트를 붙여넣고 "불러오기"를 눌러보세요.
        </p>
      )}

      <ErrorModal
        isOpen={translationError !== null}
        message={translationError?.message ?? ''}
        onClose={() => setTranslationError(null)}
      />

      <SettingsDrawer
        isOpen={isSettingsOpen}
        onClose={() => setIsSettingsOpen(false)}
        apiSettings={apiSettings}
        onApiSettingsChange={(settings) => void saveApiSettings(settings)}
        theme={theme}
        onThemeChange={setTheme}
        customThemePresets={customThemePresets}
        onSaveCustomThemePreset={(name) => void saveThemePreset({ name, theme })}
        onDeleteCustomThemePreset={(name) => void deleteThemePreset(name)}
      />

      <LibraryDrawer
        isOpen={isLibraryOpen}
        onClose={() => setIsLibraryOpen(false)}
        novels={novels}
        onSelectNovel={(novel) => {
          setActiveNovelId(novel.id)
          setCurrentChapterIndex(novel.lastReadChapterIndex)
          setIsLibraryOpen(false)
        }}
        onUpdateNovel={(novelId, title, coverUrl, systemPrompt, translationNote) =>
          void patchNovel(novelId, { title, coverUrl, systemPrompt, translationNote })
        }
        onDeleteNovel={(novelId) => void deleteNovel(novelId)}
        onReorderNovels={(orderedIds) => void reorderNovels(orderedIds)}
        onDownloadNovel={() => {}}
      />
    </AppShell>
  )
}

export default App
