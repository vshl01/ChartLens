export const palette = {
  primary: '#5B6CFF',
  onPrimary: '#FFFFFF',
  secondary: '#10B981',
  onSecondary: '#FFFFFF',
  error: '#EF4444',
  warning: '#F59E0B',
  success: '#22C55E',
  surfaceLight: '#FFFFFF',
  surfaceDark: '#0B0F1A',
  backgroundLight: '#F7F8FB',
  backgroundDark: '#000408',
  textLight: '#0B0F1A',
  textDark: '#E6E8EE',
  mutedLight: '#5B6478',
  mutedDark: '#9AA3B7',
  outlineLight: '#E2E5EC',
  outlineDark: '#1E2333',
} as const;

export type Palette = typeof palette;
