import {NativeEventEmitter, NativeModules} from 'react-native';
import type {HighlightBox, PatternPickerItem} from '@/types';

export type BubbleState =
  | 'idle'
  | 'expanded'
  | 'capturing'
  | 'analyzing'
  | 'highlighting'
  | 'error';

export type BadgeTone = 'neutral' | 'success' | 'warning';

export type BubbleConfig = {
  size: number;
  opacity: number;
  x: number;
  y: number;
  brokerIconBase64?: string;
  brokerColor?: string;
  glyph?: string;
};

export type AnalyzeArgs = {
  apiKey: string;
  model: string;
  patternId: string;
  patternName: string;
  patternDef: string;
  color: string;
  minConfidence: number;
  maxBoxes: number;
  autoDismissMs: number;
};

export type AnalyzeMatch = {
  idx: number;
  confidence: number;
  x: number;
  y: number;
  w: number;
  h: number;
  note?: string;
};

export type AnalyzeSummary = {
  matchCount: number;
  frameWidth: number;
  frameHeight: number;
  gotChart: boolean;
  durationMs: number;
  captureMs: number;
  geminiMs: number;
  rawText: string;
  matches: AnalyzeMatch[];
};

type OverlayNative = {
  requestOverlayPermission(): Promise<boolean>;
  hasOverlayPermission(): Promise<boolean>;
  showBubble(config: BubbleConfig): Promise<void>;
  hideBubble(): Promise<void>;
  setBubbleState(state: BubbleState): Promise<void>;
  setBubbleVisible(visible: boolean): Promise<void>;
  setBubbleBadge(text: string | null, tone: BadgeTone): Promise<void>;
  showPatternPicker(patterns: PatternPickerItem[]): Promise<void>;
  hidePatternPicker(): Promise<void>;
  showHighlightOverlay(
    boxes: HighlightBox[],
    frameWidth: number,
    frameHeight: number,
    autoDismissMs: number,
  ): Promise<void>;
  clearHighlightOverlay(): Promise<void>;
  showToast(text: string, tone: BadgeTone): Promise<void>;
  runAnalyzeAndDraw(args: AnalyzeArgs): Promise<AnalyzeSummary>;
};

const native = (NativeModules.OverlayModule ?? null) as OverlayNative | null;

const reject = () => Promise.reject(new Error('OverlayModule unavailable'));

export const Overlay: OverlayNative = {
  requestOverlayPermission: () =>
    native ? native.requestOverlayPermission() : reject(),
  hasOverlayPermission: () =>
    native ? native.hasOverlayPermission() : Promise.resolve(false),
  showBubble: c => (native ? native.showBubble(c) : reject()),
  hideBubble: () => (native ? native.hideBubble() : reject()),
  setBubbleState: s => (native ? native.setBubbleState(s) : reject()),
  setBubbleVisible: v => (native ? native.setBubbleVisible(v) : reject()),
  setBubbleBadge: (t, tone) =>
    native ? native.setBubbleBadge(t, tone) : Promise.resolve(),
  showPatternPicker: items =>
    native ? native.showPatternPicker(items) : reject(),
  hidePatternPicker: () => (native ? native.hidePatternPicker() : Promise.resolve()),
  showHighlightOverlay: (boxes, w, h, autoDismissMs) =>
    native ? native.showHighlightOverlay(boxes, w, h, autoDismissMs) : reject(),
  clearHighlightOverlay: () =>
    native ? native.clearHighlightOverlay() : Promise.resolve(),
  showToast: (text, tone) =>
    native ? native.showToast(text, tone) : Promise.resolve(),
  runAnalyzeAndDraw: args =>
    native ? native.runAnalyzeAndDraw(args) : reject(),
};

export const OverlayEvents = native
  ? new NativeEventEmitter(NativeModules.OverlayModule)
  : null;

export type OverlayEventName =
  | 'OverlayBubbleTap'
  | 'OverlayLongPress'
  | 'OverlayPositionChanged'
  | 'OverlayPatternPicked'
  | 'OverlayPickerDismissed'
  | 'OverlayHighlightTapped'
  | 'OverlayHighlightCleared'
  | 'OverlayClearTapped'
  | 'OverlayStaleFrame';
