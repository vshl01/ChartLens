import type {CandleMatch, GeminiAnalysis} from '@/types';

export type ParseResult =
  | {ok: true; analysis: GeminiAnalysis}
  | {ok: false; error: string};

function isFiniteNum(v: unknown): v is number {
  return typeof v === 'number' && Number.isFinite(v);
}

function parseBBox(raw: unknown): {x: number; y: number; w: number; h: number} | null {
  if (!raw || typeof raw !== 'object') return null;
  const o = raw as Record<string, unknown>;
  if (!isFiniteNum(o.x) || !isFiniteNum(o.y) || !isFiniteNum(o.w) || !isFiniteNum(o.h)) {
    return null;
  }
  if (o.w <= 0 || o.h <= 0) return null;
  return {x: Math.round(o.x), y: Math.round(o.y), w: Math.round(o.w), h: Math.round(o.h)};
}

function parseMatch(raw: unknown): CandleMatch | null {
  if (!raw || typeof raw !== 'object') return null;
  const o = raw as Record<string, unknown>;
  if (!isFiniteNum(o.idx)) return null;
  const pattern = typeof o.pattern === 'string' ? o.pattern : '';
  if (!pattern) return null;
  const confidence = isFiniteNum(o.confidence)
    ? Math.max(0, Math.min(1, o.confidence))
    : 0;
  const bbox = parseBBox(o.bbox);
  if (!bbox) return null;
  const note = typeof o.note === 'string' ? o.note : undefined;
  return {idx: Math.round(o.idx), pattern, confidence, bbox, note};
}

function stripFences(s: string): string {
  return s
    .replace(/^\s*```(?:json)?\s*/i, '')
    .replace(/```\s*$/i, '')
    .trim();
}

export function parseGeminiResponse(
  raw: string,
  fallbackParsed?: unknown,
): ParseResult {
  let parsed: unknown = fallbackParsed;
  if (parsed === undefined) {
    try {
      parsed = JSON.parse(stripFences(raw));
    } catch {
      return {ok: false, error: 'parse_failed'};
    }
  }
  if (!parsed || typeof parsed !== 'object') {
    return {ok: false, error: 'parse_failed'};
  }
  const obj = parsed as Record<string, unknown>;
  const imageWidth = isFiniteNum(obj.imageWidth) ? Math.round(obj.imageWidth) : 0;
  const imageHeight = isFiniteNum(obj.imageHeight) ? Math.round(obj.imageHeight) : 0;
  const errorVal = typeof obj.error === 'string' ? obj.error : undefined;
  const arr = Array.isArray(obj.matches) ? obj.matches : [];
  const matches: CandleMatch[] = [];
  for (const m of arr) {
    const parsedMatch = parseMatch(m);
    if (parsedMatch) matches.push(parsedMatch);
  }
  if (imageWidth <= 0 || imageHeight <= 0) {
    return {
      ok: true,
      analysis: {imageWidth, imageHeight, matches, error: errorVal ?? 'parse_failed'},
    };
  }
  return {ok: true, analysis: {imageWidth, imageHeight, matches, error: errorVal}};
}

export function clampMatchesToFrame(
  matches: CandleMatch[],
  width: number,
  height: number,
): {kept: CandleMatch[]; droppedOutOfBounds: number} {
  const kept: CandleMatch[] = [];
  let droppedOutOfBounds = 0;
  for (const m of matches) {
    const b = m.bbox;
    const x1 = Math.max(0, b.x);
    const y1 = Math.max(0, b.y);
    const x2 = Math.min(width, b.x + b.w);
    const y2 = Math.min(height, b.y + b.h);
    const w = x2 - x1;
    const h = y2 - y1;
    if (w <= 1 || h <= 1) {
      droppedOutOfBounds++;
      continue;
    }
    kept.push({...m, bbox: {x: x1, y: y1, w, h}});
  }
  return {kept, droppedOutOfBounds};
}
