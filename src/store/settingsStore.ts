import {create} from 'zustand';
import type {AppSettings} from '@/types';
import {readJSON, writeJSON, StorageKeys} from '@services/storage';

const DEFAULTS: AppSettings = {
  model: 'gemini-2.5-flash',
  defaultPresetId: 'bullish_engulfing',
  bubbleSize: 56,
  bubbleOpacity: 0.95,
  bubbleX: -1,
  bubbleY: 320,
  captureQuality: '1080p',
  autoClearDays: 30,
  themeMode: 'system',
  hiddenBrokerIds: [],
  customBrokerIds: [],
  onboardingComplete: false,
};

type SettingsStore = {
  settings: AppSettings;
  set<K extends keyof AppSettings>(key: K, value: AppSettings[K]): void;
  reset(): void;
};

export const useSettings = create<SettingsStore>((set, get) => ({
  settings: readJSON<AppSettings>(StorageKeys.settings, DEFAULTS),
  set: (key, value) => {
    const next = {...get().settings, [key]: value};
    writeJSON(StorageKeys.settings, next);
    set({settings: next});
  },
  reset: () => {
    writeJSON(StorageKeys.settings, DEFAULTS);
    set({settings: DEFAULTS});
  },
}));
