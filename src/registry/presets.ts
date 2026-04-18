export type PresetCategory = 'pattern' | 'count' | 'levels' | 'custom';
export type ResponseFormat = 'json' | 'text';

export type Preset = {
  id: string;
  name: string;
  emoji: string;
  category: PresetCategory;
  prompt: string;
  format: ResponseFormat;
  builtin?: boolean;
};

export const DEFAULT_PRESETS: readonly Preset[] = [
  {
    id: 'bullish_engulfing',
    name: 'Bullish engulfing',
    emoji: '🟢',
    category: 'pattern',
    prompt:
      'Find every bullish engulfing pattern in the last {n} candles. Return JSON: {"matches":[{"index":number,"confidence":0..1,"note":string}]}.',
    format: 'json',
    builtin: true,
  },
  {
    id: 'bearish_engulfing',
    name: 'Bearish engulfing',
    emoji: '🔴',
    category: 'pattern',
    prompt:
      'Find every bearish engulfing pattern in the last {n} candles. Return JSON: {"matches":[{"index":number,"confidence":0..1,"note":string}]}.',
    format: 'json',
    builtin: true,
  },
  {
    id: 'dojis',
    name: 'Dojis',
    emoji: '➕',
    category: 'pattern',
    prompt:
      'Find every doji in the last {n} candles. Return JSON: {"matches":[{"index":number,"type":"standard|dragonfly|gravestone","confidence":0..1}]}.',
    format: 'json',
    builtin: true,
  },
  {
    id: 'hammers',
    name: 'Hammers',
    emoji: '🔨',
    category: 'pattern',
    prompt:
      'Find hammers/inverted hammers in the last {n} candles. Return JSON: {"matches":[{"index":number,"type":"hammer|inverted","confidence":0..1}]}.',
    format: 'json',
    builtin: true,
  },
  {
    id: 'three_green_soldiers',
    name: 'Three green soldiers',
    emoji: '🪖',
    category: 'pattern',
    prompt:
      'Find any three white/green soldiers sequence in the last {n} candles. Return JSON: {"matches":[{"startIndex":number,"endIndex":number,"confidence":0..1}]}.',
    format: 'json',
    builtin: true,
  },
  {
    id: 'three_black_crows',
    name: 'Three black crows',
    emoji: '🐦',
    category: 'pattern',
    prompt:
      'Find any three black crows sequence in the last {n} candles. Return JSON: {"matches":[{"startIndex":number,"endIndex":number,"confidence":0..1}]}.',
    format: 'json',
    builtin: true,
  },
  {
    id: 'count_green_red',
    name: 'Count green vs red',
    emoji: '🔢',
    category: 'count',
    prompt:
      'Count green vs red candles in the last {n} candles. Return JSON: {"green":number,"red":number,"doji":number,"total":number}.',
    format: 'json',
    builtin: true,
  },
  {
    id: 'support_resistance',
    name: 'Support / resistance',
    emoji: '📐',
    category: 'levels',
    prompt:
      'Identify the most prominent support and resistance price levels visible. Return JSON: {"support":[number],"resistance":[number],"notes":string}.',
    format: 'json',
    builtin: true,
  },
  {
    id: 'custom',
    name: 'Custom',
    emoji: '✏️',
    category: 'custom',
    prompt: '',
    format: 'text',
    builtin: true,
  },
];
