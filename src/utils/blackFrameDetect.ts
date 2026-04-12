/**
 * Approximate "is this PNG essentially black/blank?" check.
 * The native side should ideally compute this on the raw bitmap.
 * As a JS fallback we sample bytes from the base64 payload.
 */
export function isLikelyBlackBase64(base64Png: string, threshold = 0.985): boolean {
  if (!base64Png) return true;
  if (base64Png.length < 256) return true;

  const sampleLen = Math.min(4096, base64Png.length);
  let lowChars = 0;
  for (let i = 0; i < sampleLen; i++) {
    const c = base64Png.charCodeAt(i);
    if (c === 65 /* A */ || c === 66 /* B */) lowChars++;
  }
  return lowChars / sampleLen > threshold;
}
