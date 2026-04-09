export type CaptureSession = {
  brokerId: string;
  startedAt: number;
  active: boolean;
};

export type HistoryEntry = {
  id: string;
  brokerId: string;
  presetId: string;
  presetName: string;
  prompt: string;
  imageUri: string;
  thumbnailUri?: string;
  responseText: string;
  responseJson?: unknown;
  capturedAt: number;
  captureMs: number;
  geminiMs: number;
  model: string;
  error?: string;
};

export type GeminiModel = 'gemini-2.5-flash' | 'gemini-2.5-pro';

export type CaptureQuality = 'full' | '1080p' | '720p';

export type ThemeMode = 'system' | 'light' | 'dark';

export type AppSettings = {
  model: GeminiModel;
  defaultPresetId: string;
  bubbleSize: number;
  bubbleOpacity: number;
  bubbleX: number;
  bubbleY: number;
  captureQuality: CaptureQuality;
  autoClearDays: 0 | 7 | 30 | 90;
  themeMode: ThemeMode;
  hiddenBrokerIds: string[];
  customBrokerIds: string[];
  defaultBrokerId?: string;
  lastBrokerId?: string;
  onboardingComplete: boolean;
};

export type NativeError = {
  code: string;
  message: string;
};
