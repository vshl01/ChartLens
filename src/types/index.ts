export type CaptureSession = {
  brokerId: string;
  startedAt: number;
  active: boolean;
};

export type CandleBBox = {x: number; y: number; w: number; h: number};

export type CandleMatch = {
  idx: number;
  pattern: string;
  confidence: number;
  bbox: CandleBBox;
  note?: string;
};

export type GeminiAnalysis = {
  imageWidth: number;
  imageHeight: number;
  matches: CandleMatch[];
  error?: string;
};

export type HistoryEntry = {
  id: string;
  brokerId: string;
  patternId: string;
  patternName: string;
  prompt: string;
  imageUri: string;
  thumbnailUri?: string;
  responseText: string;
  analysis?: GeminiAnalysis;
  matches: CandleMatch[];
  capturedAt: number;
  captureMs: number;
  geminiMs: number;
  frameWidth: number;
  frameHeight: number;
  model: string;
  error?: string;
};

export type GeminiModel = 'gemini-2.5-flash' | 'gemini-2.5-pro';

export type CaptureQuality = 'full' | '1080p' | '720p';

export type ThemeMode = 'system' | 'light' | 'dark';

export type BoxAutoDismiss = 5 | 10 | 20 | 30 | 0; // 0 = never
export type MinConfidence = 0.4 | 0.6 | 0.8;

export type AppSettings = {
  model: GeminiModel;
  defaultPatternId: string;
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
  boxAutoDismissSec: BoxAutoDismiss;
  minConfidence: MinConfidence;
  maxRenderedBoxes: number;
};

export type PatternPickerItem = {
  id: string;
  name: string;
  hint: string;
  emoji: string;
  color: string;
};

export type HighlightBox = {
  x: number;
  y: number;
  w: number;
  h: number;
  label: string;
  color: string;
  confidence: number;
};

export type NativeError = {
  code: string;
  message: string;
};
