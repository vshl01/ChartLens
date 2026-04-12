import {NativeEventEmitter, NativeModules} from 'react-native';

export type BubbleState =
  | 'idle'
  | 'expanded'
  | 'capturing'
  | 'processing'
  | 'result'
  | 'error';

export type BubbleConfig = {
  size: number;
  opacity: number;
  x: number;
  y: number;
  brokerIconBase64?: string;
  brokerColor?: string;
  glyph?: string;
};

type OverlayNative = {
  requestOverlayPermission(): Promise<boolean>;
  hasOverlayPermission(): Promise<boolean>;
  showBubble(config: BubbleConfig): Promise<void>;
  hideBubble(): Promise<void>;
  setBubbleState(state: BubbleState): Promise<void>;
  setBubbleVisible(visible: boolean): Promise<void>;
  setResultText(text: string): Promise<void>;
  showResultProgress(label: string, accentHex: string | null): Promise<void>;
  showResultMessage(
    title: string | null,
    content: string,
    timing: string | null,
  ): Promise<void>;
  showResultError(message: string): Promise<void>;
  hideResult(): Promise<void>;
};

const native = (NativeModules.OverlayModule ?? null) as OverlayNative | null;

const reject = () => Promise.reject(new Error('OverlayModule unavailable'));

export const Overlay: OverlayNative = {
  requestOverlayPermission: () => (native ? native.requestOverlayPermission() : reject()),
  hasOverlayPermission: () =>
    native ? native.hasOverlayPermission() : Promise.resolve(false),
  showBubble: c => (native ? native.showBubble(c) : reject()),
  hideBubble: () => (native ? native.hideBubble() : reject()),
  setBubbleState: s => (native ? native.setBubbleState(s) : reject()),
  setBubbleVisible: v => (native ? native.setBubbleVisible(v) : reject()),
  setResultText: t => (native ? native.setResultText(t) : reject()),
  showResultProgress: (l, a) =>
    native ? native.showResultProgress(l, a ?? null) : reject(),
  showResultMessage: (t, c, tm) =>
    native ? native.showResultMessage(t ?? null, c, tm ?? null) : reject(),
  showResultError: m => (native ? native.showResultError(m) : reject()),
  hideResult: () => (native ? native.hideResult() : reject()),
};

export const OverlayEvents = native
  ? new NativeEventEmitter(NativeModules.OverlayModule)
  : null;

export type OverlayEventName =
  | 'OverlayBubbleTap'
  | 'OverlayLongPress'
  | 'OverlayPositionChanged';
