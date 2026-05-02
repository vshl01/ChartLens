export type PatternEntry = {
  id: string;
  name: string;
  hint: string;
  emoji: string;
  multiCandle: boolean;
  groupSize?: number;
  definition: string;
  color: string;
};

export const PATTERNS: readonly PatternEntry[] = [
  {
    id: 'bullish_engulfing',
    name: 'Bullish Engulfing',
    hint: 'Big green body swallows prior red',
    emoji: '🟢',
    multiCandle: true,
    groupSize: 2,
    definition:
      'A two-candle reversal where a green/up candle whose real body fully engulfs the previous red/down candle\'s real body. Wicks may extend beyond.',
    color: '#10B981',
  },
  {
    id: 'bearish_engulfing',
    name: 'Bearish Engulfing',
    hint: 'Big red body swallows prior green',
    emoji: '🔴',
    multiCandle: true,
    groupSize: 2,
    definition:
      'A two-candle reversal where a red/down candle whose real body fully engulfs the previous green/up candle\'s real body. Wicks may extend beyond.',
    color: '#EF4444',
  },
  {
    id: 'doji',
    name: 'Doji',
    hint: 'Open ≈ close, indecision',
    emoji: '➕',
    multiCandle: false,
    definition:
      'A single candle whose open and close are essentially equal (real body length is negligibly small relative to total range). Includes standard, dragonfly, and gravestone variants.',
    color: '#F59E0B',
  },
  {
    id: 'hammer',
    name: 'Hammer',
    hint: 'Small body on top, long lower wick',
    emoji: '🔨',
    multiCandle: false,
    definition:
      'A single candle after a downtrend with a small real body near the top of the range and a lower wick at least 2x the body. Bullish reversal signal.',
    color: '#10B981',
  },
  {
    id: 'inverted_hammer',
    name: 'Inverted Hammer',
    hint: 'Small body bottom, long upper wick (after downtrend)',
    emoji: '🔼',
    multiCandle: false,
    definition:
      'A single candle after a downtrend with a small real body near the bottom of the range and an upper wick at least 2x the body. Bullish reversal signal.',
    color: '#10B981',
  },
  {
    id: 'shooting_star',
    name: 'Shooting Star',
    hint: 'Small body bottom, long upper wick (after uptrend)',
    emoji: '☄️',
    multiCandle: false,
    definition:
      'A single candle after an uptrend with a small real body near the bottom and an upper wick at least 2x the body. Bearish reversal signal.',
    color: '#EF4444',
  },
  {
    id: 'hanging_man',
    name: 'Hanging Man',
    hint: 'Hammer shape after uptrend (bearish)',
    emoji: '🪦',
    multiCandle: false,
    definition:
      'A single candle after an uptrend that looks like a hammer (small body up top, long lower wick) but signals bearish reversal because of context.',
    color: '#EF4444',
  },
  {
    id: 'three_white_soldiers',
    name: 'Three White Soldiers',
    hint: 'Three rising green candles',
    emoji: '🪖',
    multiCandle: true,
    groupSize: 3,
    definition:
      'Three consecutive long-bodied green/up candles, each opening within the prior body and closing near the high. Strong bullish continuation/reversal.',
    color: '#10B981',
  },
  {
    id: 'three_black_crows',
    name: 'Three Black Crows',
    hint: 'Three falling red candles',
    emoji: '🐦',
    multiCandle: true,
    groupSize: 3,
    definition:
      'Three consecutive long-bodied red/down candles, each opening within the prior body and closing near the low. Strong bearish continuation/reversal.',
    color: '#EF4444',
  },
  {
    id: 'morning_star',
    name: 'Morning Star',
    hint: 'Down · doji · up (bullish reversal)',
    emoji: '🌅',
    multiCandle: true,
    groupSize: 3,
    definition:
      'Three-candle pattern: a long red candle, a small-body candle (often doji) gapping down, then a long green candle closing well into the first body. Bullish reversal.',
    color: '#10B981',
  },
  {
    id: 'evening_star',
    name: 'Evening Star',
    hint: 'Up · doji · down (bearish reversal)',
    emoji: '🌇',
    multiCandle: true,
    groupSize: 3,
    definition:
      'Three-candle pattern: a long green candle, a small-body candle (often doji) gapping up, then a long red candle closing well into the first body. Bearish reversal.',
    color: '#EF4444',
  },
  {
    id: 'harami_bullish',
    name: 'Bullish Harami',
    hint: 'Small green inside prior big red',
    emoji: '🫄',
    multiCandle: true,
    groupSize: 2,
    definition:
      'A two-candle pattern where a small green/up body sits entirely inside the prior large red/down body. Bullish reversal.',
    color: '#10B981',
  },
  {
    id: 'harami_bearish',
    name: 'Bearish Harami',
    hint: 'Small red inside prior big green',
    emoji: '🩸',
    multiCandle: true,
    groupSize: 2,
    definition:
      'A two-candle pattern where a small red/down body sits entirely inside the prior large green/up body. Bearish reversal.',
    color: '#EF4444',
  },
  {
    id: 'marubozu_bullish',
    name: 'Bullish Marubozu',
    hint: 'Tall green, ~no wicks',
    emoji: '🟩',
    multiCandle: false,
    definition:
      'A single long green/up candle with no (or negligible) upper or lower wicks — open is the low, close is the high. Strong bullish momentum.',
    color: '#10B981',
  },
  {
    id: 'marubozu_bearish',
    name: 'Bearish Marubozu',
    hint: 'Tall red, ~no wicks',
    emoji: '🟥',
    multiCandle: false,
    definition:
      'A single long red/down candle with no (or negligible) upper or lower wicks — open is the high, close is the low. Strong bearish momentum.',
    color: '#EF4444',
  },
];

export const PATTERN_BY_ID: Record<string, PatternEntry> = Object.fromEntries(
  PATTERNS.map(p => [p.id, p]),
);

export function findPattern(id: string): PatternEntry | undefined {
  return PATTERN_BY_ID[id];
}
