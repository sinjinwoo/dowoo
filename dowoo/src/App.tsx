import { useEffect, useRef, useState } from 'react'
import AppShell from './components/layout/AppShell'
import TopBar from './components/layout/TopBar'
import ChapterNavBar from './components/layout/ChapterNavBar'
import NovelViewer from './components/viewer/NovelViewer'
import SettingsDrawer from './components/settings/SettingsDrawer'
import LibraryDrawer from './components/library/LibraryDrawer'
import ErrorModal from './components/ui/ErrorModal'
import { API_BASE } from './api/client'
import {
  listNovels,
  getNovelDetail,
  patchNovel,
  deleteNovel,
  reorderNovels,
  updateLastRead,
  exportNovelUrl,
  readSource,
} from './api/novels'
import { getChapter } from './api/chapters'
import {
  getApiSettings,
  saveModelSettings,
  addApiKey,
  deleteApiKey,
  getTheme,
  saveTheme,
  listThemePresets,
  saveThemePreset,
  deleteThemePreset,
  type ApiSettingsResponse,
} from './api/settings'
import { translateStream, TranslationAbortedError } from './api/translateStream'
import { loadLastSession, saveLastSession } from './storage/localSettings'
import { defaultTheme } from './data/defaults'
import { useHideOnScroll } from './hooks/useHideOnScroll'
import type { ThemeSettings } from './types/settings'
import type { Novel, NovelDetail, Chapter } from './types/novel'

type TranslationError = { type: 'crawling' | 'gemini'; message: string }

function App() {
  const [isLoading, setIsLoading] = useState(true)
  const [novels, setNovels] = useState<Novel[]>([])
  const [activeNovelDetail, setActiveNovelDetail] = useState<NovelDetail | null>(null)
  const [currentChapterIndex, setCurrentChapterIndex] = useState(0)
  const [activeChapter, setActiveChapter] = useState<Chapter | null>(null)

  const [apiSettings, setApiSettings] = useState<ApiSettingsResponse>({ model: 'gemini-2.5-flash', thinkingBudget: null, apiKeys: [] })
  const [theme, setTheme] = useState<ThemeSettings>(defaultTheme)
  const [customThemePresets, setCustomThemePresets] = useState<{ name: string; theme: Partial<ThemeSettings> }[]>([])

  const [urlInput, setUrlInput] = useState('')
  const [isSettingsOpen, setIsSettingsOpen] = useState(false)
  const [isLibraryOpen, setIsLibraryOpen] = useState(false)

  const [translationStatus, setTranslationStatus] = useState<'idle' | 'translating'>('idle')
  const [translationError, setTranslationError] = useState<TranslationError | null>(null)
  const [streamingChapterId, setStreamingChapterId] = useState<string | null>(null)
  const [streamingText, setStreamingText] = useState('')
  const [streamingProgress, setStreamingProgress] = useState(0)
  const abortControllerRef = useRef<AbortController | null>(null)

  const refreshNovels = async () => {
    setNovels(await listNovels())
  }

  const openNovelDetail = async (novelId: string, chapterIndex: number) => {
    const detail = await getNovelDetail(novelId)
    setActiveNovelDetail(detail)
    setCurrentChapterIndex(Math.min(Math.max(0, chapterIndex), Math.max(0, detail.chapters.length - 1)))
  }

  // 초기 로딩: 서재/설정/테마를 병렬로 가져오고, 마지막 세션(localStorage)이 있으면 이어서 연다.
  useEffect(() => {
    void (async () => {
      try {
        const [novelList, settings, themeData, presets] = await Promise.all([
          listNovels(),
          getApiSettings(),
          getTheme(),
          listThemePresets(),
        ])
        setNovels(novelList)
        setApiSettings(settings)
        setTheme(themeData)
        setCustomThemePresets(presets)

        const session = loadLastSession()
        if (session && novelList.some((n) => n.id === session.activeNovelId)) {
          await openNovelDetail(session.activeNovelId, session.currentChapterIndex)
        } else if (novelList.length > 0) {
          await openNovelDetail(novelList[0].id, novelList[0].lastReadChapterIndex ?? 0)
        }
      } catch (error) {
        setTranslationError({
          type: 'gemini',
          message: error instanceof Error ? error.message : String(error),
        })
      } finally {
        setIsLoading(false)
      }
    })()
  }, [])

  // 활성 소설/챕터 인덱스가 바뀌면 해당 챕터 본문을 불러온다.
  useEffect(() => {
    const chapterMeta = activeNovelDetail?.chapters[currentChapterIndex]
    if (!activeNovelDetail || !chapterMeta) {
      setActiveChapter(null)
      return
    }

    let cancelled = false
    setActiveChapter(null)
    getChapter(activeNovelDetail.id, chapterMeta.id)
      .then((chapter) => {
        if (!cancelled) setActiveChapter(chapter)
      })
      .catch((error) => {
        if (!cancelled) {
          setTranslationError({
            type: 'gemini',
            message: error instanceof Error ? error.message : String(error),
          })
        }
      })
    return () => {
      cancelled = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeNovelDetail?.id, currentChapterIndex, activeNovelDetail?.chapters.length])

  useEffect(() => {
    if (activeChapter) setUrlInput(activeChapter.sourceUrl)
  }, [activeChapter])

  useEffect(() => {
    if (activeNovelDetail) {
      saveLastSession({ activeNovelId: activeNovelDetail.id, currentChapterIndex })
      void updateLastRead(activeNovelDetail.id, currentChapterIndex)
    }
  }, [activeNovelDetail?.id, currentChapterIndex])

  const [topBarHidden, showTopBar] = useHideOnScroll(10, urlInput)

  const handleCancelTranslation = () => {
    abortControllerRef.current?.abort()
  }

  const handleTranslate = async (novelId: string, chapterId: string) => {
    const controller = new AbortController()
    abortControllerRef.current = controller

    setTranslationStatus('translating')
    setStreamingChapterId(chapterId)
    setStreamingText('')
    setStreamingProgress(0)
    setTranslationError(null)

    let accumulated = ''
    try {
      const finalText = await translateStream({
        novelId,
        chapterId,
        signal: controller.signal,
        onLine: (line) => {
          accumulated = accumulated ? `${accumulated}\n${line}` : line
          setStreamingText(accumulated)
        },
        onProgress: setStreamingProgress,
      })
      setActiveChapter((prev) => (prev && prev.id === chapterId ? { ...prev, translatedText: finalText } : prev))
    } catch (error) {
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

  // 챕터를 불러왔는데 아직 번역이 안 되어 있으면(최초 진입 또는 이전 시도가 끊긴 경우) 자동으로 번역을 시작한다.
  useEffect(() => {
    if (activeChapter && !activeChapter.translatedText && translationStatus === 'idle') {
      void handleTranslate(activeChapter.novelId, activeChapter.id)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeChapter?.id])

  const handleSubmit = async () => {
    const input = urlInput.trim()
    if (!input || translationStatus === 'translating') return
    setTranslationError(null)

    const isUrl = /^https?:\/\//i.test(input)

    try {
      const result = await readSource(isUrl ? { sourceUrl: input } : { pastedText: input })
      await refreshNovels()
      const detail = await getNovelDetail(result.novelId)
      setActiveNovelDetail(detail)
      const index = detail.chapters.findIndex((c) => c.id === result.chapterId)
      setCurrentChapterIndex(index >= 0 ? index : 0)
    } catch (error) {
      setTranslationError({
        type: 'crawling',
        message: error instanceof Error ? error.message : String(error),
      })
    }
  }

  const handleSelectNovel = async (novel: Novel) => {
    await openNovelDetail(novel.id, novel.lastReadChapterIndex ?? 0)
    setIsLibraryOpen(false)
  }

  const handleUpdateNovel = async (
    novelId: string,
    title: string,
    coverUrl: string,
    systemPrompt: string,
    translationNote: string
  ) => {
    await patchNovel(novelId, { title, coverUrl, systemPrompt, translationNote })
    void refreshNovels()
    if (activeNovelDetail?.id === novelId) {
      setActiveNovelDetail(await getNovelDetail(novelId))
    }
  }

  const handleDeleteNovel = async (novelId: string) => {
    await deleteNovel(novelId)
    void refreshNovels()
    if (activeNovelDetail?.id === novelId) {
      setActiveNovelDetail(null)
      setActiveChapter(null)
    }
  }

  const handleReorderNovels = async (orderedIds: string[]) => {
    setNovels((prev) => {
      const byId = new Map(prev.map((n) => [n.id, n]))
      return orderedIds.map((id, index) => ({ ...byId.get(id)!, order: index }))
    })
    await reorderNovels(orderedIds)
  }

  const handleDownloadNovel = (novel: Novel) => {
    const link = document.createElement('a')
    link.href = `${API_BASE}${exportNovelUrl(novel.id)}`
    link.click()
  }

  if (isLoading) {
    return <div className="flex min-h-screen items-center justify-center text-gray-500">불러오는 중...</div>
  }

  const displayedTranslatedText =
    activeChapter && activeChapter.id === streamingChapterId && streamingText.length > 0 ? streamingText : undefined

  const translationProgress = translationStatus === 'translating' ? streamingProgress : 0

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
      {activeNovelDetail && activeChapter ? (
        <>
          <NovelViewer
            chapter={activeChapter}
            theme={theme}
            progress={translationProgress}
            displayedTranslatedText={displayedTranslatedText}
            isTranslating={translationStatus === 'translating' && activeChapter.id === streamingChapterId}
          />

          <ChapterNavBar
            onPrevChapter={() => setCurrentChapterIndex((i) => Math.max(0, i - 1))}
            onNextChapter={() =>
              setCurrentChapterIndex((i) => Math.min(activeNovelDetail.chapters.length - 1, i + 1))
            }
          />
        </>
      ) : activeNovelDetail ? (
        <p className="py-20 text-center text-gray-400">챕터를 불러오는 중...</p>
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
        model={apiSettings.model ?? 'gemini-2.5-flash'}
        apiKeys={apiSettings.apiKeys}
        onModelChange={(model) => void saveModelSettings(model, apiSettings.thinkingBudget ?? undefined).then(setApiSettings)}
        onAddApiKey={(key) => void addApiKey(key).then(setApiSettings)}
        onDeleteApiKey={(keyId) => {
          void deleteApiKey(keyId)
          setApiSettings((prev) => ({ ...prev, apiKeys: prev.apiKeys.filter((k) => k.id !== keyId) }))
        }}
        theme={theme}
        onThemeChange={(next) => {
          setTheme(next)
          void saveTheme(next)
        }}
        customThemePresets={customThemePresets}
        onSaveCustomThemePreset={(name) =>
          void saveThemePreset({ name, theme }).then(() => listThemePresets()).then(setCustomThemePresets)
        }
        onDeleteCustomThemePreset={(name) => {
          void deleteThemePreset(name)
          setCustomThemePresets((prev) => prev.filter((p) => p.name !== name))
        }}
      />

      <LibraryDrawer
        isOpen={isLibraryOpen}
        onClose={() => setIsLibraryOpen(false)}
        novels={novels}
        onSelectNovel={(novel) => void handleSelectNovel(novel)}
        onLoadNovelDetail={getNovelDetail}
        onUpdateNovel={(novelId, title, coverUrl, systemPrompt, translationNote) =>
          void handleUpdateNovel(novelId, title, coverUrl, systemPrompt, translationNote)
        }
        onDeleteNovel={(novelId) => void handleDeleteNovel(novelId)}
        onReorderNovels={(orderedIds) => void handleReorderNovels(orderedIds)}
        onDownloadNovel={handleDownloadNovel}
      />
    </AppShell>
  )
}

export default App
