import {Platform} from 'react-native';

const family = Platform.select({android: 'sans-serif', default: 'System'});

export const typography = {
  displayLarge: {fontFamily: family, fontSize: 32, fontWeight: '700' as const, lineHeight: 40},
  titleLarge: {fontFamily: family, fontSize: 22, fontWeight: '600' as const, lineHeight: 28},
  titleMedium: {fontFamily: family, fontSize: 18, fontWeight: '600' as const, lineHeight: 24},
  bodyLarge: {fontFamily: family, fontSize: 16, fontWeight: '400' as const, lineHeight: 24},
  bodyMedium: {fontFamily: family, fontSize: 14, fontWeight: '400' as const, lineHeight: 20},
  labelLarge: {fontFamily: family, fontSize: 14, fontWeight: '600' as const, lineHeight: 20},
  labelSmall: {fontFamily: family, fontSize: 12, fontWeight: '500' as const, lineHeight: 16},
} as const;
