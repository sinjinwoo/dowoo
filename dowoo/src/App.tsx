import { useEffect, useState } from 'react'
import { useLiveQuery } from 'dexie-react-hooks'
import AppShell from './components/layout/AppShell'
import TopBar from './components/layout/TopBar'
import ChapterNavBar from './components/layout/ChapterNavBar'
import NovelViewer from './components/viewer/NovelViewer'
import SettingsDrawer from './components/settings/SettingsDrawer'
import LibraryDrawer from './components/library/LibraryDrawer'
import { db } from './db/db'
import { seedNovelsIfEmpty, patchNovel } from './db/novelsRepo'
import { getApiSettings, saveApiSettings, saveThemePreset, deleteThemePreset } from './db/settingsRepo'
import { loadTheme, saveTheme, loadLastSession, saveLastSession } from './storage/localSettings'
import { defaultApiSettings, defaultTheme } from './data/defaults'
import { useHideOnScroll } from './hooks/useHideOnScroll'
import type { ThemeSettings } from './types/settings'

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

  const novels = novelsQuery ?? []
  const apiSettings = apiSettingsQuery ?? defaultApiSettings
  const customThemePresets = customThemePresetsQuery ?? []

  const [lastSession] = useState(() => loadLastSession())
  const [activeNovelId, setActiveNovelId] = useState(lastSession?.activeNovelId ?? '')
  const [currentChapterIndex, setCurrentChapterIndex] = useState(lastSession?.currentChapterIndex ?? 0)
  const [urlInput, setUrlInput] = useState('')

  const [isSettingsOpen, setIsSettingsOpen] = useState(false)
  const [isLibraryOpen, setIsLibraryOpen] = useState(false)

  const [theme, setTheme] = useState<ThemeSettings>(() => loadTheme() ?? defaultTheme)
  const [translationProgress] = useState(0)

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

  const [topBarHidden, , toggleTopBar] = useHideOnScroll(10, urlInput)

  if (!activeNovel || !chapter) {
    return <div className="flex min-h-screen items-center justify-center text-gray-500">불러오는 중...</div>
  }

  return (
    <AppShell
      topBar={
        <TopBar
          urlInput={urlInput}
          onUrlInputChange={setUrlInput}
          onSubmit={() => {}}
          onOpenSettings={() => setIsSettingsOpen(true)}
          onOpenLibrary={() => setIsLibraryOpen(true)}
          hidden={topBarHidden}
        />
      }
      topBarHidden={topBarHidden}
      mainStyle={{ backgroundColor: theme.bgColor, color: theme.fontColor }}
      onMainClick={toggleTopBar}
    >
      <NovelViewer chapter={chapter} theme={theme} progress={translationProgress} />

      <ChapterNavBar
        onPrevChapter={() => setCurrentChapterIndex((i) => Math.max(0, i - 1))}
        onNextChapter={() => setCurrentChapterIndex((i) => Math.min(activeNovel.chapters.length - 1, i + 1))}
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
        onDownloadNovel={() => {}}
      />
    </AppShell>
  )
}

export default App
