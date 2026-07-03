import { useState } from 'react'
import type { MaskedApiKey } from '../../api/settings'
import type { ThemePreset, ThemeSettings } from '../../types/settings'
import Drawer from '../ui/Drawer'
import Tabs from '../ui/Tabs'
import ApiSettingsPanel from './ApiSettingsPanel'
import ThemeSettingsPanel from './ThemeSettingsPanel'

export interface SettingsDrawerProps {
  isOpen: boolean
  onClose: () => void
  model: string
  apiKeys: MaskedApiKey[]
  onModelChange: (model: string) => void
  onAddApiKey: (key: string) => void
  onDeleteApiKey: (keyId: string) => void
  theme: ThemeSettings
  onThemeChange: (theme: ThemeSettings) => void
  customThemePresets: ThemePreset[]
  onSaveCustomThemePreset: (name: string) => void
  onDeleteCustomThemePreset: (name: string) => void
}

export default function SettingsDrawer({
  isOpen,
  onClose,
  model,
  apiKeys,
  onModelChange,
  onAddApiKey,
  onDeleteApiKey,
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
            content: (
              <ApiSettingsPanel
                model={model}
                apiKeys={apiKeys}
                onModelChange={onModelChange}
                onAddKey={onAddApiKey}
                onDeleteKey={onDeleteApiKey}
              />
            ),
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
