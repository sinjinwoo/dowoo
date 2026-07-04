import { useEffect, useRef, useState } from 'react'
import AppShell from './components/layout/AppShell'
import TopBar from './components/layout/TopBar'
import ChapterNavBar from './components/layout/ChapterNavBar'
import NovelViewer from './components/viewer/NovelViewer'
import SettingsDrawer from './components/settings/SettingsDrawer'
import LibraryDrawer from './components/library/LibraryDrawer'
import ErrorModal from './components/ui/ErrorModal'
import AuthScreen from './components/auth/AuthScreen'
import { useAuth } from './auth/AuthContext'
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
  crawlUrl,
} from './api/novels'
import { getChapter, createChapter } from './api/chapters'
import {
  getApiSettings,
  saveModelSettings,
  addApiKey,
  addApiKeys,
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

type TranslationError = { type: 'crawling' | 'gemini' | 'boundary'; message: string }

// 스킴(https://)이 빠진 채로 도메인만 붙여넣는 경우(오타로 스킴을 빼먹거나, 그냥 주소만 복사한 경우)도
// URL로 인식해서 자동으로 https://를 붙여준다 - 공백이 없고 "도메인.도메인(/경로)" 형태일 때만 해당.
// 실제 소설 본문(붙여넣기)은 문장이라 공백/문장부호가 있어서 이 패턴에 걸릴 일이 없다.
const BARE_DOMAIN_PATTERN = /^[a-z0-9-]+(\.[a-z0-9-]+)+(\/\S*)?$/i

function normalizeAsUrlIfPossible(input: string): string | null {
  if (/^https?:\/\//i.test(input)) return input
  if (!input.includes(' ') && BARE_DOMAIN_PATTERN.test(input)) return `https://${input}`
  return null
}

function App() {
  const { status: authStatus, logout } = useAuth()
  const [isLoading, setIsLoading] = useState(true)
  const [novels, setNovels] = useState<Novel[]>([])
  const [activeNovelDetail, setActiveNovelDetail] = useState<NovelDetail | null>(null)
  const [currentChapterIndex, setCurrentChapterIndex] = useState(0)
  const [activeChapter, setActiveChapter] = useState<Chapter | null>(null)

  const [apiSettings, setApiSettings] = useState<ApiSettingsResponse>({ model: '', thinkingBudget: null, apiKeys: [] })
  const [theme, setTheme] = useState<ThemeSettings>(defaultTheme)
  const [customThemePresets, setCustomThemePresets] = useState<{ name: string; theme: Partial<ThemeSettings> }[]>([])

  const [urlInput, setUrlInput] = useState('')
  const [syncedUrlChapterId, setSyncedUrlChapterId] = useState<string | null>(null)
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
  // 로그인이 완료된 뒤에만(재로그인 포함) 실행한다.
  useEffect(() => {
    if (authStatus !== 'authenticated') return
    void (async () => {
      setIsLoading(true)
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
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [authStatus])

  // 활성 소설/챕터 인덱스가 바뀌면 이전 챕터 본문을 즉시 비운다(렌더링 도중 상태 조정 패턴).
  const chapterKey = `${activeNovelDetail?.id ?? ''}:${currentChapterIndex}`
  const [syncedChapterKey, setSyncedChapterKey] = useState<string | null>(null)
  if (chapterKey !== syncedChapterKey) {
    setSyncedChapterKey(chapterKey)
    setActiveChapter(null)
  }

  // 활성 소설/챕터 인덱스가 바뀌면 해당 챕터 본문을 불러온다.
  useEffect(() => {
    const chapterMeta = activeNovelDetail?.chapters[currentChapterIndex]
    if (!activeNovelDetail || !chapterMeta) return

    let cancelled = false
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

  // activeChapter가 바뀔 때만 urlInput을 동기화한다(같은 챕터의 번역 진행 중 갱신에는 반응하지 않음).
  // 렌더링 도중 상태를 조정하는 React의 권장 패턴이라 useEffect 대신 여기서 직접 처리한다.
  if (activeChapter && activeChapter.id !== syncedUrlChapterId) {
    setSyncedUrlChapterId(activeChapter.id)
    setUrlInput(activeChapter.sourceUrl)
  }

  useEffect(() => {
    if (activeNovelDetail) {
      saveLastSession({ activeNovelId: activeNovelDetail.id, currentChapterIndex })
      void updateLastRead(activeNovelDetail.id, currentChapterIndex)
    }
  }, [activeNovelDetail?.id, currentChapterIndex])

  // urlInput을 resetKey로 쓰면 타이핑할 때마다(글자마다) 상단바가 숨겨지므로,
  // 챕터가 실제로 바뀔 때만 다시 보여지도록 activeChapter?.id를 기준으로 삼는다.
  const [topBarHidden, toggleTopBar] = useHideOnScroll(10, activeChapter?.id)

  const handleCancelTranslation = () => {
    abortControllerRef.current?.abort()
  }

  const handleTranslate = async (novelId: string, chapterId: string) => {
    // 이미 진행 중인 스트림이 있으면(예: 자동 번역 트리거와 "다시 번역" 제출이 겹친 경우)
    // 취소하고 이 요청이 이어받는다 - 두 스트림이 동시에 activeChapter를 덮어쓰는 걸 방지.
    abortControllerRef.current?.abort()
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

  // 챕터에 번역 결과가 아예 없을 때만(최초 진입 또는 이전 시도가 끊겨 저장 전이었던 경우) 자동으로
  // 번역을 시작한다. 이미 번역된 챕터를 다시 번역하려면 반드시 "불러오기" 버튼(forceRecrawl)을 눌러야 한다.
  useEffect(() => {
    if (activeChapter && !activeChapter.translatedText && translationStatus === 'idle') {
      const novelId = activeChapter.novelId
      const chapterId = activeChapter.id
      queueMicrotask(() => {
        void handleTranslate(novelId, chapterId)
      })
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeChapter?.id])

  const handleSubmit = async () => {
    const input = urlInput.trim()
    if (!input || translationStatus === 'translating') return
    setTranslationError(null)

    const normalizedUrl = normalizeAsUrlIfPossible(input)

    try {
      // URL 재제출은 "다시 번역"으로 취급한다 - 이미 불러온 소설이라도 forceRecrawl로
      // 원문을 다시 가져오고, 캐시된 번역 여부와 무관하게 재번역한다.
      const result = await readSource(
        normalizedUrl ? { sourceUrl: normalizedUrl, forceRecrawl: true } : { pastedText: input }
      )
      await refreshNovels()
      const detail = await getNovelDetail(result.novelId)
      setActiveNovelDetail(detail)
      const index = detail.chapters.findIndex((c) => c.id === result.chapterId)
      setCurrentChapterIndex(index >= 0 ? index : 0)

      const chapter = await getChapter(result.novelId, result.chapterId)
      setActiveChapter(chapter)
      void handleTranslate(result.novelId, result.chapterId)
    } catch (error) {
      setTranslationError({
        type: 'crawling',
        message: error instanceof Error ? error.message : String(error),
      })
    }
  }

  // 이전/다음편: 이미 알고 있는(캐시된) 챕터면 바로 이동하고, 없으면 chapter.prevUrl/nextUrl을
  // 직접 크롤링(6.1) + 챕터 생성(3.2)한다. /read(6.2)는 캐시 미스 시 새 소설을 만들어버리므로 쓰지 않는다.
  // 번역 진행 중에도 이동 가능 - 떠나는 챕터의 스트림은 중지 버튼과 동일하게 취소한다(서버가 그때까지
  // 번역된 부분을 저장해두므로 나중에 이 챕터로 돌아오면 중단 지점까지는 그대로 남아 있다).
  const handleNavigateChapter = async (direction: 'prev' | 'next') => {
    if (!activeNovelDetail || !activeChapter) return
    abortControllerRef.current?.abort()
    let targetUrl = direction === 'prev' ? activeChapter.prevUrl : activeChapter.nextUrl

    // 우리가 마지막으로 알고 있는 챕터라도, 그 사이 원문 사이트에 새 챕터가 올라왔을 수 있다.
    // "다음 편 없음"으로 단정하기 전에 현재 챕터 주소를 가볍게 재크롤링(번역 없이 크롤링만)해서
    // nextUrl이 새로 생겼는지 한 번 더 확인한다. 실패하면 그냥 기존 "다음 편 없음" 흐름으로 넘어간다.
    // 이렇게 얻은 targetUrl은 아직 검증되지 않은 추정값이라, 실제로 크롤링해봤을 때 실패하더라도
    // "크롤링 오류"가 아니라 "다음 편이 없습니다"로 처리한다(원문 사이트가 아직 존재하지 않는
    // 다음 회차 URL을 미리 채번해두는 경우가 있어 그대로 보여주면 사용자가 오류로 오해한다).
    let isSpeculativeNext = false
    if (!targetUrl && direction === 'next') {
      try {
        const recrawled = await crawlUrl(activeChapter.sourceUrl)
        targetUrl = recrawled.nextUrl ?? null
        isSpeculativeNext = targetUrl !== null
      } catch {
        // 재확인 실패는 무시 - 아래에서 기존과 동일하게 "다음 편이 없습니다" 처리
      }
    }

    if (!targetUrl) {
      setTranslationError({
        type: 'boundary',
        message: direction === 'prev' ? '이전 편이 없습니다.' : '다음 편이 없습니다.',
      })
      return
    }

    const cachedIndex = activeNovelDetail.chapters.findIndex((c) => c.sourceUrl === targetUrl)
    if (cachedIndex !== -1) {
      setCurrentChapterIndex(cachedIndex)
      return
    }

    setTranslationError(null)
    try {
      const crawled = await crawlUrl(targetUrl)
      const chapter = await createChapter(activeNovelDetail.id, {
        sourceUrl: targetUrl,
        title: crawled.title,
        originalText: crawled.content,
        prevUrl: crawled.prevUrl,
        nextUrl: crawled.nextUrl,
      })
      const detail = await getNovelDetail(activeNovelDetail.id)
      setActiveNovelDetail(detail)
      const index = detail.chapters.findIndex((c) => c.id === chapter.id)
      setCurrentChapterIndex(index >= 0 ? index : currentChapterIndex)
      setActiveChapter(chapter)
      void handleTranslate(activeNovelDetail.id, chapter.id)
    } catch (error) {
      if (isSpeculativeNext) {
        setTranslationError({ type: 'boundary', message: '다음 편이 없습니다.' })
      } else {
        setTranslationError({
          type: 'crawling',
          message: error instanceof Error ? error.message : String(error),
        })
      }
    }
  }

  const handleSelectChapter = async (novelId: string, chapterIndex: number) => {
    await openNovelDetail(novelId, chapterIndex)
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

  if (authStatus === 'loading') {
    return <div className="flex min-h-screen items-center justify-center text-gray-500">불러오는 중...</div>
  }

  if (authStatus === 'unauthenticated') {
    return <AuthScreen />
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
          isTranslating={translationStatus === 'translating'}
          onCancelTranslation={handleCancelTranslation}
          onLogout={() => void logout()}
        />
      }
      topBarHidden={topBarHidden}
      onContentClick={toggleTopBar}
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
            onPrevChapter={() => void handleNavigateChapter('prev')}
            onNextChapter={() => void handleNavigateChapter('next')}
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
        title={translationError?.type === 'boundary' ? '알림' : undefined}
        message={translationError?.message ?? ''}
        onClose={() => setTranslationError(null)}
      />

      <SettingsDrawer
        isOpen={isSettingsOpen}
        onClose={() => setIsSettingsOpen(false)}
        model={apiSettings.model ?? ''}
        apiKeys={apiSettings.apiKeys}
        onModelChange={(model) => void saveModelSettings(model, apiSettings.thinkingBudget ?? undefined).then(setApiSettings)}
        onAddApiKey={(key) => void addApiKey(key).then(setApiSettings)}
        onAddApiKeys={(keys) => void addApiKeys(keys).then(setApiSettings)}
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
        onSelectChapter={(novelId, chapterIndex) => void handleSelectChapter(novelId, chapterIndex)}
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
