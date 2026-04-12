/**
 * Render a Gemini response into something readable in the result panel.
 * - If parsed is a known shape (matches/support/etc.), render bullet points.
 * - Otherwise, pretty-print the JSON.
 * - Falls back to the raw text.
 */
export function formatResponse(text: string, parsed: unknown): string {
  if (parsed && typeof parsed === 'object') {
    const obj = parsed as Record<string, unknown>;

    if ('error' in obj && typeof obj.error === 'string') {
      return `⚠️  ${obj.error}`;
    }

    if (Array.isArray(obj.matches)) {
      const matches = obj.matches as Array<Record<string, unknown>>;
      if (matches.length === 0) return 'No matches found in the visible chart.';
      const lines = matches.map((m, i) => {
        const idx = m.index ?? m.startIndex ?? i + 1;
        const conf =
          typeof m.confidence === 'number'
            ? ` · ${(m.confidence * 100).toFixed(0)}%`
            : '';
        const note = typeof m.note === 'string' ? ` — ${m.note}` : '';
        const type = typeof m.type === 'string' ? ` [${m.type}]` : '';
        return `• Candle ${idx}${type}${conf}${note}`;
      });
      return `Found ${matches.length} match${matches.length === 1 ? '' : 'es'}:\n\n${lines.join('\n')}`;
    }

    if (
      typeof obj.green === 'number' &&
      typeof obj.red === 'number' &&
      typeof obj.total === 'number'
    ) {
      const doji = typeof obj.doji === 'number' ? obj.doji : 0;
      return [
        `🟢 Green: ${obj.green}`,
        `🔴 Red:   ${obj.red}`,
        doji ? `➕ Doji:  ${doji}` : null,
        `Total:    ${obj.total}`,
      ]
        .filter(Boolean)
        .join('\n');
    }

    if (Array.isArray(obj.support) || Array.isArray(obj.resistance)) {
      const support = (obj.support as number[] | undefined) ?? [];
      const resistance = (obj.resistance as number[] | undefined) ?? [];
      const notes = typeof obj.notes === 'string' ? `\n\n${obj.notes}` : '';
      return [
        resistance.length ? `Resistance:\n  ${resistance.join('\n  ')}` : '',
        support.length ? `Support:\n  ${support.join('\n  ')}` : '',
      ]
        .filter(Boolean)
        .join('\n\n')
        .concat(notes);
    }

    if (typeof obj.summary === 'string') return obj.summary;

    try {
      return JSON.stringify(obj, null, 2);
    } catch {
      /* fallthrough */
    }
  }

  return text.trim();
}
