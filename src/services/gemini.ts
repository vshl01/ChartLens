import type {GeminiAnalysis, GeminiModel} from '@/types';
import {createLogger} from '@utils/logger';
import {parseGeminiResponse} from '@utils/formatResponse';
import type {PatternEntry} from '@registry/patterns';

const log = createLogger('gemini');

async function fetchWithTimeout(
  url: string,
  init: RequestInit,
  timeoutMs: number,
  externalSignal?: AbortSignal,
): Promise<Response> {
  const controller = new AbortController();
  const t = setTimeout(() => controller.abort(), timeoutMs);
  const onExternal = () => controller.abort();
  if (externalSignal) {
    if (externalSignal.aborted) controller.abort();
    else externalSignal.addEventListener('abort', onExternal, {once: true});
  }
  try {
    return await fetch(url, {...init, signal: controller.signal});
  } finally {
    clearTimeout(t);
    if (externalSignal) externalSignal.removeEventListener('abort', onExternal);
  }
}

const ENDPOINT = (model: string, key: string) =>
  `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${encodeURIComponent(key)}`;

export const DEFAULT_SYSTEM_PROMPT = (frameWidth: number, frameHeight: number): string =>
  [
    'You are a candlestick chart analyst. The image is a screenshot of a stock trading app. The user wants every candle that matches a specific pattern.',
    `Coordinate system: top-left origin, pixel units. The image dimensions are ${frameWidth} x ${frameHeight}. Echo them as imageWidth and imageHeight in your response.`,
    'IGNORE any small circular floating button overlay in the image (it is a UI element from the analysis app, not part of the chart). Do not include it in any bounding box.',
    'For each matching candle (or candle group, when the pattern requires multiple candles like Engulfing or Three Soldiers), return a tight axis-aligned bounding box {x, y, w, h} that encloses the relevant candle bodies and wicks. Boxes must stay strictly inside the chart area — do not include axis labels, headers, or sidebars.',
    'If the pattern is multi-candle (Engulfing, Harami, Three Green Soldiers, Morning Star, Evening Star, Three Black Crows), return ONE box around the full group, and set idx to the index of the LAST candle in the group.',
    'Return ONLY valid JSON in this exact schema, no markdown fences, no prose:',
    '{"imageWidth": <int>, "imageHeight": <int>, "matches": [{"idx": <int>, "pattern": "<snake_case>", "confidence": <0..1>, "bbox": {"x": <int>, "y": <int>, "w": <int>, "h": <int>}, "note": "<optional short string>"}]}',
    'If you cannot identify a chart in the image, return {"imageWidth": <int>, "imageHeight": <int>, "matches": [], "error": "no_chart_detected"}.',
  ].join('\n');

export function buildUserPrompt(pattern: PatternEntry, minConfidence: number): string {
  return [
    `Find every **${pattern.name}** pattern in this candlestick chart.`,
    `Pattern definition: ${pattern.definition}`,
    `Only include matches you are at least ${minConfidence.toFixed(2)} confident about.`,
    'Return JSON per the schema in the system instructions.',
  ].join(' ');
}

export type GeminiAnalyzeArgs = {
  apiKey: string;
  model: GeminiModel;
  systemPrompt?: string;
  userPrompt: string;
  imageBase64: string;
  frameWidth: number;
  frameHeight: number;
  mimeType?: string;
  signal?: AbortSignal;
};

export type GeminiResult = {
  text: string;
  analysis: GeminiAnalysis;
  durationMs: number;
};

export class GeminiError extends Error {
  constructor(
    public readonly code:
      | 'invalid_key'
      | 'rate_limited'
      | 'bad_request'
      | 'network'
      | 'aborted'
      | 'unknown',
    message: string,
    public readonly retryAfterMs?: number,
  ) {
    super(message);
  }
}

export async function analyzeChart(args: GeminiAnalyzeArgs): Promise<GeminiResult> {
  const start = Date.now();
  const url = ENDPOINT(args.model, args.apiKey);
  const sysPrompt = args.systemPrompt ?? DEFAULT_SYSTEM_PROMPT(args.frameWidth, args.frameHeight);

  const body = {
    systemInstruction: {parts: [{text: sysPrompt}]},
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

  const bodyJson = JSON.stringify(body);

  while (attempt < maxAttempts) {
    let res: Response;
    try {
      res = await fetchWithTimeout(
        url,
        {
          method: 'POST',
          headers: {'Content-Type': 'application/json'},
          body: bodyJson,
        },
        60_000,
        args.signal,
      );
    } catch (e) {
      lastErr = e;
      const err = e as Error & {name?: string};
      log.warn(
        'gemini fetch error',
        'name=', err.name,
        'message=', err.message,
        'attempt=', attempt,
      );
      if (err.name === 'AbortError') {
        if (args.signal?.aborted) throw new GeminiError('aborted', 'Aborted');
        // local timeout fired
        if (attempt < maxAttempts - 1) {
          const wait = Math.min(3000, 600 * 2 ** attempt);
          await new Promise<void>(r => setTimeout(() => r(), wait));
          attempt++;
          continue;
        }
        throw new GeminiError('network', 'Request timed out talking to Gemini');
      }
      // Network failures (DNS, TLS, no route): retry with backoff.
      if (attempt < maxAttempts - 1) {
        const wait = Math.min(3000, 600 * 2 ** attempt);
        log.warn('retrying after network error in', wait, 'ms');
        await new Promise<void>(r => setTimeout(() => r(), wait));
        attempt++;
        continue;
      }
      throw new GeminiError('network', `Network error: ${err.message ?? 'no response'}`);
    }

    if (!res.ok) {
      const status = res.status;
      let errBody = '';
      try {
        errBody = await res.text();
      } catch {}
      log.warn('gemini http error', 'status=', status, 'body=', errBody.slice(0, 200));
      if (status === 400) throw new GeminiError('bad_request', `Bad request: ${errBody.slice(0, 160)}`);
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
      throw new GeminiError('unknown', `HTTP ${status}: ${errBody.slice(0, 160)}`);
    }

    let data: {candidates?: Array<{content?: {parts?: Array<{text?: string}>}}>};
    try {
      data = await res.json();
    } catch (e) {
      lastErr = e;
      log.warn('gemini json parse failed', (e as Error).message);
      throw new GeminiError('unknown', 'Could not parse Gemini response');
    }
    const candidate = data.candidates?.[0];
    const text: string =
      candidate?.content?.parts?.map(p => p.text ?? '').join('') ?? '';
    const parseResult = parseGeminiResponse(text);
    const analysis: GeminiAnalysis = parseResult.ok
      ? parseResult.analysis
      : {
          imageWidth: args.frameWidth,
          imageHeight: args.frameHeight,
          matches: [],
          error: parseResult.error,
        };
    return {text, analysis, durationMs: Date.now() - start};
  }

  log.warn('gemini exhausted retries', lastErr);
  throw new GeminiError('rate_limited', 'Rate limited after retries', 8000);
}

export async function pingApiKey(apiKey: string, model: GeminiModel): Promise<boolean> {
  try {
    const res = await fetchWithTimeout(
      ENDPOINT(model, apiKey),
      {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({
          contents: [{role: 'user', parts: [{text: 'ping'}]}],
        }),
      },
      15_000,
    );
    return res.ok;
  } catch (e) {
    log.warn('ping failed', (e as Error).message);
    return false;
  }
}
