import {MD3DarkTheme, MD3LightTheme, type MD3Theme} from 'react-native-paper';
import {palette} from './colors';

export const lightTheme: MD3Theme = {
  ...MD3LightTheme,
  colors: {
    ...MD3LightTheme.colors,
    primary: palette.primary,
    onPrimary: palette.onPrimary,
    secondary: palette.secondary,
    onSecondary: palette.onSecondary,
    error: palette.error,
    background: palette.backgroundLight,
    surface: palette.surfaceLight,
    onSurface: palette.textLight,
    onSurfaceVariant: palette.mutedLight,
    outline: palette.outlineLight,
  },
};

export const darkTheme: MD3Theme = {
  ...MD3DarkTheme,
  colors: {
    ...MD3DarkTheme.colors,
    primary: palette.primary,
    onPrimary: palette.onPrimary,
    secondary: palette.secondary,
    onSecondary: palette.onSecondary,
    error: palette.error,
    background: palette.backgroundDark,
    surface: palette.surfaceDark,
    onSurface: palette.textDark,
    onSurfaceVariant: palette.mutedDark,
    outline: palette.outlineDark,
  },
};
