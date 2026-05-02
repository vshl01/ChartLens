import {Alert} from 'react-native';
import {create} from 'zustand';
import {Overlay, OverlayEvents} from '@native/OverlayModule';
import {MediaProjection} from '@native/MediaProjectionModule';
import {AppLauncher} from '@native/AppLauncherModule';
import {findBroker} from '@registry/brokers';
import {findPattern, PATTERNS, PATTERN_BY_ID} from '@registry/patterns';
import type {PatternEntry} from '@registry/patterns';
import {getGeminiKey} from '@services/secureStorage';
import {useSettings} from '@store/settingsStore';
import {useHistory} from '@store/historyStore';
import {useSession} from '@store/sessionStore';
import {createLogger} from '@utils/logger';
import type {CandleMatch, PatternPickerItem} from '@/types';

const log = createLogger('orchestrator');

type Subscription = {remove(): void};

export type FlowState =
  | 'IDLE'
  | 'PICKING'
  | 'CAPTURING'
  | 'ANALYZING'
  | 'HIGHLIGHTING';

type FlowStore = {
  state: FlowState;
  patternId?: string;
  matchCount: number;
  lastFrameWidth: number;
  lastFrameHeight: number;
  transitions: Array<{at: number; from: FlowState; to: FlowState; note?: string}>;
  set(next: Partial<FlowStore>): void;
  transition(to: FlowState, note?: string): void;
};

export const useFlow = create<FlowStore>((set, get) => ({
  state: 'IDLE',
  matchCount: 0,
  lastFrameWidth: 0,
  lastFrameHeight: 0,
  transitions: [],
  set: next => set(next),
  transition: (to, note) => {
    const from = get().state;
    if (from === to) return;
    log.info('flow', from, '→', to, note ?? '');
    set(prev => ({
      state: to,
      transitions: [...prev.transitions, {at: Date.now(), from, to, note}].slice(-100),
    }));
  },
}));

let bubbleTapSub: Subscription | null = null;
let longPressSub: Subscription | null = null;
let pickerSub: Subscription | null = null;
let pickerDismissSub: Subscription | null = null;
let clearSub: Subscription | null = null;
let highlightTapSub: Subscription | null = null;
let highlightClearedSub: Subscription | null = null;
let staleFrameSub: Subscription | null = null;

let inFlightAbort: AbortController | null = null;
let lastTapAt = 0;
let lastPickerOpenAt = 0;

function pickerItems(): PatternPickerItem[] {
  return PATTERNS.map(p => ({
    id: p.id,
    name: p.name,
    hint: p.hint,
    emoji: p.emoji,
    color: p.color,
  }));
}

export async function startSession(brokerId: string): Promise<void> {
  const broker = findBroker(brokerId);
  if (!broker) return;

  const hasOverlay = await Overlay.hasOverlayPermission();
  if (!hasOverlay) {
    await Overlay.requestOverlayPermission();
    Alert.alert(
      'Overlay permission required',
      'Grant "Display over other apps" for ChartLens, then come back and tap the broker again.',
    );
    return;
  }

  // If a session is already active and the projection service is still
  // running, just relaunch the broker — restarting the projection mid-flow
  // races the old MediaProjection.onStop with new display setup and can
  // leave the ImageReader torn down.
  const existing = useSession.getState();
  const projectionAlive = await MediaProjection.isServiceRunning().catch(() => false);
  if (existing.active && projectionAlive) {
    log.info('session already active, just relaunching broker');
    await AppLauncher.launchApp(broker.packageName, broker.deepLink ?? null);
    return;
  }

  try {
    const token = await MediaProjection.requestProjection();
    await MediaProjection.startService(token.resultCode);
  } catch (e) {
    log.warn('projection cancelled', (e as Error).message);
    return;
  }

  let iconB64: string | null = null;
  try {
    iconB64 = await AppLauncher.getAppIcon(broker.packageName);
  } catch {
    iconB64 = null;
  }

  const {settings} = useSettings.getState();
  await Overlay.showBubble({
    size: settings.bubbleSize,
    opacity: settings.bubbleOpacity,
    x: settings.bubbleX,
    y: settings.bubbleY,
    brokerColor: broker.brand.primary,
    brokerIconBase64: iconB64 ?? undefined,
    glyph: broker.name.charAt(0),
  });
  await Overlay.setBubbleBadge(null, 'neutral');

  attachListeners();
  useSession.getState().start(brokerId);
  useFlow.getState().transition('IDLE', 'session started');

  await AppLauncher.launchApp(broker.packageName, broker.deepLink ?? null);
}

export async function stopSession(): Promise<void> {
  detachListeners();
  inFlightAbort?.abort();
  inFlightAbort = null;
  try {
    await Overlay.clearHighlightOverlay();
  } catch {}
  try {
    await Overlay.hidePatternPicker();
  } catch {}
  try {
    await Overlay.hideBubble();
  } catch {}
  try {
    await MediaProjection.stopService();
  } catch {}
  useSession.getState().stop();
  useFlow.getState().transition('IDLE', 'session stopped');
}

function attachListeners(): void {
  detachListeners();
  if (!OverlayEvents) return;
  bubbleTapSub = OverlayEvents.addListener('OverlayBubbleTap', () => {
    void onBubbleTap();
  });
  longPressSub = OverlayEvents.addListener('OverlayLongPress', () => {
    void stopSession();
  });
  pickerSub = OverlayEvents.addListener('OverlayPatternPicked', (e: {id?: string}) => {
    if (e?.id) void onPatternPicked(e.id);
  });
  pickerDismissSub = OverlayEvents.addListener('OverlayPickerDismissed', () => {
    if (useFlow.getState().state === 'PICKING') {
      useFlow.getState().transition('IDLE', 'picker dismissed');
    }
  });
  clearSub = OverlayEvents.addListener('OverlayClearTapped', () => {
    void clearHighlights('clear button');
  });
  highlightTapSub = OverlayEvents.addListener('OverlayHighlightTapped', () => {
    // no-op for now: surface to UI later if useful
  });
  highlightClearedSub = OverlayEvents.addListener('OverlayHighlightCleared', () => {
    if (useFlow.getState().state === 'HIGHLIGHTING') {
      useFlow.getState().transition('IDLE', 'auto/manual cleared');
    }
  });
  staleFrameSub = OverlayEvents.addListener('OverlayStaleFrame', () => {
    void Overlay.showToast('Screen rotated, please re-capture', 'warning');
    void clearHighlights('stale frame');
  });
}

function detachListeners(): void {
  bubbleTapSub?.remove();
  longPressSub?.remove();
  pickerSub?.remove();
  pickerDismissSub?.remove();
  clearSub?.remove();
  highlightTapSub?.remove();
  highlightClearedSub?.remove();
  staleFrameSub?.remove();
  bubbleTapSub = null;
  longPressSub = null;
  pickerSub = null;
  pickerDismissSub = null;
  clearSub = null;
  highlightTapSub = null;
  highlightClearedSub = null;
  staleFrameSub = null;
}

async function onBubbleTap(): Promise<void> {
  const now = Date.now();
  if (now - lastTapAt < 250) return; // debounce
  lastTapAt = now;

  const flow = useFlow.getState();
  // re-tap during HIGHLIGHTING or CAPTURING/ANALYZING: clear and restart picker
  if (flow.state === 'HIGHLIGHTING') {
    await clearHighlights('re-tap');
  }
  if (flow.state === 'CAPTURING' || flow.state === 'ANALYZING') {
    inFlightAbort?.abort();
    inFlightAbort = null;
  }
  if (flow.state === 'PICKING') {
    if (now - lastPickerOpenAt < 250) return;
    return; // already open
  }
  await openPicker();
}

async function openPicker(): Promise<void> {
  lastPickerOpenAt = Date.now();
  useFlow.getState().transition('PICKING', 'opening picker');
  await Overlay.setBubbleState('expanded').catch(() => undefined);
  try {
    await Overlay.showPatternPicker(pickerItems());
  } catch (e) {
    log.warn('picker show failed', (e as Error).message);
    useFlow.getState().transition('IDLE', 'picker show failed');
    await Overlay.setBubbleState('idle').catch(() => undefined);
  }
}

async function onPatternPicked(patternId: string): Promise<void> {
  const pattern = findPattern(patternId);
  if (!pattern) {
    log.warn('unknown pattern picked', patternId);
    return;
  }
  await Overlay.hidePatternPicker().catch(() => undefined);
  useFlow.getState().set({patternId});
  await runCaptureAndAnalyze(pattern);
}

async function runCaptureAndAnalyze(pattern: PatternEntry): Promise<void> {
  try {
    await runCaptureAndAnalyzeInner(pattern);
  } catch (e) {
    // Outer safety net — any unhandled error here means the bubble could be
    // stuck invisible or in a bogus state. Restore it.
    log.error('runCaptureAndAnalyze unhandled', (e as Error).message, (e as Error).stack);
    await Overlay.setBubbleVisible(true).catch(() => undefined);
    await Overlay.setBubbleState('error').catch(() => undefined);
    await Overlay.setBubbleBadge(null, 'neutral').catch(() => undefined);
    await Overlay.showToast(
      `Internal error: ${(e as Error).message}`,
      'warning',
    ).catch(() => undefined);
    useFlow.getState().transition('IDLE', 'unhandled error');
    setTimeout(() => {
      void Overlay.setBubbleState('idle').catch(() => undefined);
    }, 1500);
  }
}

async function runCaptureAndAnalyzeInner(pattern: PatternEntry): Promise<void> {
  const flow = useFlow.getState();
  log.info('flow→CAPTURING for', pattern.id);
  flow.transition('CAPTURING', `pattern=${pattern.id}`);
  await Overlay.setBubbleState('capturing').catch(() => undefined);
  await Overlay.clearHighlightOverlay().catch(() => undefined);

  const {settings} = useSettings.getState();
  const session = useSession.getState();
  const apiKey = await getGeminiKey();
  if (!apiKey) {
    await Overlay.showToast(
      'No Gemini API key. Open ChartLens → Settings to add one.',
      'warning',
    ).catch(() => undefined);
    flow.transition('IDLE', 'no key');
    return;
  }

  // Run capture + Gemini call + draw entirely in native Kotlin so it
  // doesn't depend on the JS thread, which Realme/OnePlus/Xiaomi/etc.
  // throttle aggressively when the broker app is foregrounded. The native
  // method draws the highlight overlay on the main thread itself, so even
  // if JS is paused, the user sees the boxes immediately.
  flow.transition('ANALYZING', `pattern=${pattern.id}`);
  await Overlay.setBubbleState('analyzing').catch(() => undefined);

  let summary: Awaited<ReturnType<typeof Overlay.runAnalyzeAndDraw>>;
  try {
    summary = await Overlay.runAnalyzeAndDraw({
      apiKey,
      model: settings.model,
      patternId: pattern.id,
      patternName: pattern.name,
      patternDef: pattern.definition,
      color: pattern.color,
      minConfidence: settings.minConfidence,
      maxBoxes: settings.maxRenderedBoxes,
      autoDismissMs:
        settings.boxAutoDismissSec === 0 ? 0 : settings.boxAutoDismissSec * 1000,
    });
  } catch (e) {
    log.error('runAnalyzeAndDraw threw', (e as Error).message);
    flow.transition('IDLE', 'analyze error');
    return; // native already showed a toast and set bubble state
  }

  log.info(
    'analysis done: matches=', summary.matchCount,
    'frame=', summary.frameWidth, 'x', summary.frameHeight,
    'capture=', summary.captureMs, 'ms gemini=', summary.geminiMs, 'ms',
  );

  flow.set({
    lastFrameWidth: summary.frameWidth,
    lastFrameHeight: summary.frameHeight,
    matchCount: summary.matchCount,
  });

  // Persist to history (native already drew the boxes).
  const histMatches: CandleMatch[] = summary.matches.map(m => ({
    idx: m.idx,
    pattern: pattern.id,
    confidence: m.confidence,
    bbox: {x: m.x, y: m.y, w: m.w, h: m.h},
    note: m.note,
  }));
  useHistory.getState().add({
    id: `${Date.now()}`,
    brokerId: session.brokerId ?? 'unknown',
    patternId: pattern.id,
    patternName: pattern.name,
    prompt: pattern.definition,
    imageUri: '',
    responseText: summary.rawText,
    matches: histMatches,
    capturedAt: Date.now(),
    captureMs: summary.captureMs,
    geminiMs: summary.geminiMs,
    frameWidth: summary.frameWidth,
    frameHeight: summary.frameHeight,
    model: settings.model,
    error: summary.gotChart ? undefined : 'no_chart_detected',
  });

  if (summary.matchCount === 0) {
    flow.transition('IDLE', summary.gotChart ? 'zero matches' : 'no chart');
  } else {
    flow.transition('HIGHLIGHTING', `boxes=${summary.matchCount}`);
  }
}

async function clearHighlights(reason: string): Promise<void> {
  await Overlay.clearHighlightOverlay().catch(() => undefined);
  await Overlay.setBubbleBadge(null, 'neutral').catch(() => undefined);
  await Overlay.setBubbleState('idle').catch(() => undefined);
  if (useFlow.getState().state === 'HIGHLIGHTING') {
    useFlow.getState().transition('IDLE', `cleared: ${reason}`);
  }
}

// also exported for the Quick Capture path on Home — skips the picker and uses default pattern
export async function quickCapture(): Promise<void> {
  const {settings} = useSettings.getState();
  const pattern = PATTERN_BY_ID[settings.defaultPatternId] ?? PATTERNS[0];
  if (!pattern) return;
  await Overlay.hidePatternPicker().catch(() => undefined);
  await runCaptureAndAnalyze(pattern);
}
