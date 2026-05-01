import {create} from 'zustand';
import type {HistoryEntry} from '@/types';
import {readJSON, writeJSON, storage, StorageKeys} from '@services/storage';

const MAX_ENTRIES = 200;
const HISTORY_VERSION_KEY = 'history.version';
const HISTORY_VERSION = 2; // bumped when entry shape changes

function maybeMigrate(): HistoryEntry[] {
  const current = storage.getString(HISTORY_VERSION_KEY);
  const v = current ? Number(current) : 0;
  if (v >= HISTORY_VERSION) {
    return readJSON<HistoryEntry[]>(StorageKeys.history, []);
  }
  // Schema changed (presetId/name/responseJson → patternId/name/matches[]).
  // Personal-use app: wipe rather than write a fragile migrator.
  writeJSON(StorageKeys.history, []);
  storage.set(HISTORY_VERSION_KEY, String(HISTORY_VERSION));
  return [];
}

type HistoryStore = {
  entries: HistoryEntry[];
  add(entry: HistoryEntry): void;
  remove(id: string): void;
  clear(): void;
  bulkRemove(ids: string[]): void;
};

export const useHistory = create<HistoryStore>((set, get) => ({
  entries: maybeMigrate(),
  add: entry => {
    const next = [entry, ...get().entries].slice(0, MAX_ENTRIES);
    writeJSON(StorageKeys.history, next);
    set({entries: next});
  },
  remove: id => {
    const next = get().entries.filter(e => e.id !== id);
    writeJSON(StorageKeys.history, next);
    set({entries: next});
  },
  bulkRemove: ids => {
    const idSet = new Set(ids);
    const next = get().entries.filter(e => !idSet.has(e.id));
    writeJSON(StorageKeys.history, next);
    set({entries: next});
  },
  clear: () => {
    writeJSON(StorageKeys.history, []);
    set({entries: []});
  },
}));
