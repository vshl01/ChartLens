type Level = 'debug' | 'info' | 'warn' | 'error';

const enabled = __DEV__;

function emit(level: Level, scope: string, args: unknown[]): void {
  if (!enabled && level !== 'error') return;
  const tag = `[${scope}]`;
  switch (level) {
    case 'debug':
    case 'info':
      // eslint-disable-next-line no-console
      console.log(tag, ...args);
      break;
    case 'warn':
      // eslint-disable-next-line no-console
      console.warn(tag, ...args);
      break;
    case 'error':
      // eslint-disable-next-line no-console
      console.error(tag, ...args);
      break;
  }
}

export const createLogger = (scope: string) => ({
  debug: (...args: unknown[]) => emit('debug', scope, args),
  info: (...args: unknown[]) => emit('info', scope, args),
  warn: (...args: unknown[]) => emit('warn', scope, args),
  error: (...args: unknown[]) => emit('error', scope, args),
});

export type Logger = ReturnType<typeof createLogger>;
