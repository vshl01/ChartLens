import {create} from 'zustand';
import type {Preset} from '@registry/presets';
import {DEFAULT_PRESETS} from '@registry/presets';
import {readJSON, writeJSON, StorageKeys} from '@services/storage';

type PresetStore = {
  presets: Preset[];
  upsert(preset: Preset): void;
  remove(id: string): void;
  reorder(ids: string[]): void;
};

export const usePresets = create<PresetStore>((set, get) => ({
  presets: readJSON<Preset[]>(StorageKeys.presets, [...DEFAULT_PRESETS]),
  upsert: p => {
    const list = get().presets;
    const idx = list.findIndex(x => x.id === p.id);
    const next = idx >= 0 ? list.map((x, i) => (i === idx ? p : x)) : [...list, p];
    writeJSON(StorageKeys.presets, next);
    set({presets: next});
  },
  remove: id => {
    const next = get().presets.filter(p => p.id !== id || p.builtin);
    writeJSON(StorageKeys.presets, next);
    set({presets: next});
  },
  reorder: ids => {
    const map = new Map(get().presets.map(p => [p.id, p]));
    const next = ids.map(id => map.get(id)).filter((p): p is Preset => !!p);
    writeJSON(StorageKeys.presets, next);
    set({presets: next});
  },
}));
