import axios, {AxiosError} from 'axios';
import type {GeminiModel} from '@/types';
import {createLogger} from '@utils/logger';

const log = createLogger('gemini');

const ENDPOINT = (model: string, key: string) =>
  `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${encodeURIComponent(key)}`;

export const DEFAULT_SYSTEM_PROMPT =
  'You are a stock chart analyst. The image is a candlestick chart from a trading app. ' +
  'Index candles from left to right starting at 1 for the leftmost fully visible candle. ' +
  'Return ONLY valid JSON in the schema requested by the user prompt — no prose, no markdown fences. ' +
  'If you cannot identify the chart, return {"error": "no_chart_detected"}.';

export type GeminiAnalyzeArgs = {
  apiKey: string;
  model: GeminiModel;
  systemPrompt?: string;
  userPrompt: string;
  imageBase64: string;
  mimeType?: string;
  signal?: AbortSignal;
};

export type GeminiResult = {
  text: string;
  parsed?: unknown;
  durationMs: number;
};

export class GeminiError extends Error {
  constructor(
    public readonly code:
      | 'invalid_key'
      | 'rate_limited'
      | 'bad_request'
      | 'network'
      | 'unknown',
    message: string,
    public readonly retryAfterMs?: number,
  ) {
    super(message);
  }
}

function stripFences(s: string): string {
  return s
    .replace(/^\s*```(?:json)?\s*/i, '')
    .replace(/```\s*$/i, '')
    .trim();
}

export async function analyzeChart(args: GeminiAnalyzeArgs): Promise<GeminiResult> {
  const start = Date.now();
  const url = ENDPOINT(args.model, args.apiKey);

  const body = {
    systemInstruction: {parts: [{text: args.systemPrompt ?? DEFAULT_SYSTEM_PROMPT}]},
    contents: [
      {
        role: 'user',
        parts: [
          {text: args.userPrompt},
          {inline_data: {mime_type: args.mimeType ?? 'image/jpeg', data: args.imageBase64}},
        ],
      },
    ],
  };

  const maxAttempts = 4;
  let attempt = 0;
  let lastErr: unknown;

  while (attempt < maxAttempts) {
    try {
      const res = await axios.post(url, body, {
        timeout: 60_000,
        signal: args.signal,
        headers: {'Content-Type': 'application/json'},
      });
      const candidate = res.data?.candidates?.[0];
      const text: string =
        candidate?.content?.parts?.map((p: {text?: string}) => p.text ?? '').join('') ?? '';
      const cleaned = stripFences(text);
      let parsed: unknown;
      try {
        parsed = JSON.parse(cleaned);
      } catch {
        parsed = undefined;
      }
      return {text: cleaned, parsed, durationMs: Date.now() - start};
    } catch (e) {
      lastErr = e;
      const ax = e as AxiosError;
      const status = ax.response?.status;
      if (status === 400) throw new GeminiError('bad_request', 'Bad request');
      if (status === 401 || status === 403) {
        throw new GeminiError('invalid_key', 'Invalid Gemini API key');
      }
      if (status === 429) {
        const wait = Math.min(4000, 1000 * 2 ** attempt);
        log.warn('rate limited, retrying in', wait, 'ms');
        await new Promise<void>(r => setTimeout(() => r(), wait));
        attempt++;
        continue;
      }
      if (!ax.response) {
        throw new GeminiError('network', 'Network error');
      }
      throw new GeminiError('unknown', ax.message);
    }
  }

  throw new GeminiError(
    'rate_limited',
    'Rate limited after retries',
    8000,
  );
}

export async function pingApiKey(apiKey: string, model: GeminiModel): Promise<boolean> {
  try {
    const res = await axios.post(
      ENDPOINT(model, apiKey),
      {contents: [{role: 'user', parts: [{text: 'ping'}]}]},
      {timeout: 15_000, headers: {'Content-Type': 'application/json'}},
    );
    return res.status === 200;
  } catch (e) {
    log.warn('ping failed', (e as Error).message);
    return false;
  }
}
