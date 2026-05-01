import {create} from 'zustand';
import type {AppSettings} from '@/types';
import {readJSON, writeJSON, StorageKeys} from '@services/storage';

const DEFAULTS: AppSettings = {
  model: 'gemini-2.5-flash',
  defaultPatternId: 'bullish_engulfing',
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
  boxAutoDismissSec: 20,
  minConfidence: 0.6,
  maxRenderedBoxes: 30,
};

type SettingsStore = {
  settings: AppSettings;
  set<K extends keyof AppSettings>(key: K, value: AppSettings[K]): void;
  reset(): void;
};

function loadSettings(): AppSettings {
  const stored = readJSON<Partial<AppSettings>>(StorageKeys.settings, {});
  return {...DEFAULTS, ...stored} as AppSettings;
}

export const useSettings = create<SettingsStore>((set, get) => ({
  settings: loadSettings(),
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
