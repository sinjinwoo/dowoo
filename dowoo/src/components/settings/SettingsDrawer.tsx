import { useState } from 'react'
import type { ApiSettings, ThemePreset, ThemeSettings } from '../../types/settings'
import Drawer from '../ui/Drawer'
import Tabs from '../ui/Tabs'
import ApiSettingsPanel from './ApiSettingsPanel'
import ThemeSettingsPanel from './ThemeSettingsPanel'

export interface SettingsDrawerProps {
  isOpen: boolean
  onClose: () => void
  apiSettings: ApiSettings
  onApiSettingsChange: (settings: ApiSettings) => void
  theme: ThemeSettings
  onThemeChange: (theme: ThemeSettings) => void
  customThemePresets: ThemePreset[]
  onSaveCustomThemePreset: (name: string) => void
  onDeleteCustomThemePreset: (name: string) => void
}

export default function SettingsDrawer({
  isOpen,
  onClose,
  apiSettings,
  onApiSettingsChange,
  theme,
  onThemeChange,
  customThemePresets,
  onSaveCustomThemePreset,
  onDeleteCustomThemePreset,
}: SettingsDrawerProps) {
  const [activeTab, setActiveTab] = useState('api')

  return (
    <Drawer isOpen={isOpen} onClose={onClose} title="설정" widthClassName="w-96">
      <Tabs
        activeTabId={activeTab}
        onChange={setActiveTab}
        tabs={[
          {
            id: 'api',
            label: 'API',
            content: <ApiSettingsPanel apiSettings={apiSettings} onChange={onApiSettingsChange} />,
          },
          {
            id: 'theme',
            label: '테마',
            content: (
              <ThemeSettingsPanel
                theme={theme}
                onChange={onThemeChange}
                customPresets={customThemePresets}
                onSaveCustomPreset={onSaveCustomThemePreset}
                onDeleteCustomPreset={onDeleteCustomThemePreset}
              />
            ),
          },
        ]}
      />
    </Drawer>
  )
}
