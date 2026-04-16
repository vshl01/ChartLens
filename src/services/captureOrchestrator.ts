import {Alert, NativeEventEmitter, NativeModules} from 'react-native';
import {Overlay, OverlayEvents} from '@native/OverlayModule';
import {MediaProjection} from '@native/MediaProjectionModule';
import {AppLauncher} from '@native/AppLauncherModule';
import {findBroker} from '@registry/brokers';
import type {Preset} from '@registry/presets';

const HARD_FALLBACK_PRESET: Preset = {
  id: 'fallback',
  name: 'Describe chart',
  emoji: '📈',
  category: 'custom',
  prompt: 'Describe this candlestick chart briefly. Return JSON: {"summary":string}.',
  format: 'json',
  builtin: true,
};
import {analyzeChart, GeminiError} from '@services/gemini';
import {formatResponse} from '@utils/formatResponse';
import {getGeminiKey} from '@services/secureStorage';
import {useSettings} from '@store/settingsStore';
import {useHistory} from '@store/historyStore';
import {useSession} from '@store/sessionStore';
import {usePresets} from '@store/presetStore';
import {createLogger} from '@utils/logger';

const log = createLogger('orchestrator');

type Subscription = {remove(): void};

let bubbleTapSub: Subscription | null = null;
let longPressSub: Subscription | null = null;
let inFlight = false;
let lastTapAt = 0;

function pickPreset(): Preset {
  const {settings} = useSettings.getState();
  const presets = usePresets.getState().presets;
  const fromUser = presets.find(p => p.id === settings.defaultPresetId);
  return fromUser ?? presets[0] ?? HARD_FALLBACK_PRESET;
}

function fillPrompt(template: string): string {
  // Replace {n} with 50 (default lookback) for now.
  return template.replace(/\{n\}/g, '50');
}

export async function startSession(brokerId: string): Promise<void> {
  const broker = findBroker(brokerId);
  if (!broker) return;

  // 1. overlay permission
  const hasOverlay = await Overlay.hasOverlayPermission();
  if (!hasOverlay) {
    await Overlay.requestOverlayPermission();
    Alert.alert(
      'Overlay permission required',
      'Grant "Display over other apps" for ChartLens, then come back and tap the broker again.',
    );
    return;
  }

  // 2. MediaProjection consent
  try {
    const token = await MediaProjection.requestProjection();
    await MediaProjection.startService(token.resultCode);
  } catch (e) {
    log.warn('projection cancelled', (e as Error).message);
    return;
  }

  // 3. Capture broker icon for bubble
  let iconB64: string | null = null;
  try {
    iconB64 = await AppLauncher.getAppIcon(broker.packageName);
  } catch {
    iconB64 = null;
  }

  // 4. show bubble
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

  attachListeners();

  useSession.getState().start(brokerId);

  // 5. launch broker
  await AppLauncher.launchApp(broker.packageName, broker.deepLink ?? null);
}

export async function stopSession(): Promise<void> {
  detachListeners();
  try {
    await Overlay.hideBubble();
  } catch {}
  try {
    await MediaProjection.stopService();
  } catch {}
  useSession.getState().stop();
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
}

function detachListeners(): void {
  bubbleTapSub?.remove();
  longPressSub?.remove();
  bubbleTapSub = null;
  longPressSub = null;
}

async function onBubbleTap(): Promise<void> {
  const now = Date.now();
  if (now - lastTapAt < 500) return;
  lastTapAt = now;
  if (inFlight) return;
  inFlight = true;

  const {settings} = useSettings.getState();
  const session = useSession.getState();
  const preset = pickPreset();

  const showError = async (msg: string) => {
    log.error('capture failed:', msg);
    await Overlay.setBubbleState('error').catch(() => undefined);
    await Overlay.showResultError(msg).catch(() => undefined);
  };

  try {
    const apiKey = await getGeminiKey();
    if (!apiKey) {
      await showError('No Gemini API key. Open ChartLens → Settings to add one.');
      return;
    }

    await Overlay.setBubbleState('capturing');
    await Overlay.showResultProgress('Capturing chart…', '#5B6CFF').catch(() => undefined);

    const captureStart = Date.now();
    let frame: Awaited<ReturnType<typeof MediaProjection.captureFrame>>;
    try {
      frame = await MediaProjection.captureFrame();
    } catch (e) {
      await showError(`Capture failed: ${(e as Error).message}`);
      return;
    }
    const captureMs = Date.now() - captureStart;

    if ((frame as unknown as {isBlack?: boolean}).isBlack) {
      await showError(
        'Screen capture blocked by this app (FLAG_SECURE). Install LSPosed + DisableFlagSecure on a rooted device.',
      );
      return;
    }

    await Overlay.setBubbleState('processing');
    await Overlay.showResultProgress(`Analyzing — ${preset.name}…`, '#10B981').catch(
      () => undefined,
    );
    const prompt = preset.prompt
      ? fillPrompt(preset.prompt)
      : 'Describe this candlestick chart briefly.';
    const geminiStart = Date.now();
    let result;
    try {
      result = await analyzeChart({
        apiKey,
        model: settings.model,
        userPrompt: prompt,
        imageBase64: frame.base64,
      });
    } catch (e) {
      const msg =
        e instanceof GeminiError
          ? `Gemini ${e.code}: ${e.message}`
          : (e as Error).message ?? 'Unknown error';
      await showError(msg);
      return;
    }
    const geminiMs = Date.now() - geminiStart;

    await Overlay.setBubbleState('result');
    const pretty = formatResponse(result.text, result.parsed);
    const timing = `${preset.emoji} ${preset.name} · capture ${captureMs}ms · gemini ${geminiMs}ms`;
    await Overlay.showResultMessage(`ChartLens · ${preset.name}`, pretty || '(empty response)', timing);

    useHistory.getState().add({
      id: `${Date.now()}`,
      brokerId: session.brokerId ?? 'unknown',
      presetId: preset.id,
      presetName: preset.name,
      prompt,
      imageUri: '',
      responseText: result.text,
      responseJson: result.parsed,
      capturedAt: Date.now(),
      captureMs,
      geminiMs,
      model: settings.model,
    });

    setTimeout(() => {
      void Overlay.setBubbleState('idle');
    }, 1500);
  } catch (e) {
    await showError((e as Error).message ?? 'Unknown error');
  } finally {
    inFlight = false;
  }
}

// no-op to silence unused emitter import if events module is null
export const _unused = NativeEventEmitter || NativeModules;
