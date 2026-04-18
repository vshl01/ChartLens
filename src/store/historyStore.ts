import {create} from 'zustand';
import type {HistoryEntry} from '@/types';
import {readJSON, writeJSON, StorageKeys} from '@services/storage';

const MAX_ENTRIES = 200;

type HistoryStore = {
  entries: HistoryEntry[];
  add(entry: HistoryEntry): void;
  remove(id: string): void;
  clear(): void;
  bulkRemove(ids: string[]): void;
};

export const useHistory = create<HistoryStore>((set, get) => ({
  entries: readJSON<HistoryEntry[]>(StorageKeys.history, []),
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
    const set2 = new Set(ids);
    const next = get().entries.filter(e => !set2.has(e.id));
    writeJSON(StorageKeys.history, next);
    set({entries: next});
  },
  clear: () => {
    writeJSON(StorageKeys.history, []);
    set({entries: []});
  },
}));
