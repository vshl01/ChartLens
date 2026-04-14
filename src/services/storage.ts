import {createMMKV} from 'react-native-mmkv';

export const storage = createMMKV({id: 'chartlens.default'});

export const StorageKeys = {
  settings: 'settings.v1',
  presets: 'presets.v1',
  history: 'history.v1',
  customBrokers: 'customBrokers.v1',
} as const;

export function readJSON<T>(key: string, fallback: T): T {
  const raw = storage.getString(key);
  if (!raw) return fallback;
  try {
    return JSON.parse(raw) as T;
  } catch {
    return fallback;
  }
}

export function writeJSON(key: string, value: unknown): void {
  storage.set(key, JSON.stringify(value));
}
