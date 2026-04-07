module.exports = {
  presets: ['module:@react-native/babel-preset'],
  plugins: [
    [
      'module-resolver',
      {
        root: ['./'],
        extensions: ['.ts', '.tsx', '.js', '.jsx', '.json'],
        alias: {
          '@': './src',
          '@screens': './src/screens',
          '@components': './src/components',
          '@native': './src/native',
          '@services': './src/services',
          '@registry': './src/registry',
          '@store': './src/store',
          '@theme': './src/theme',
          '@utils': './src/utils',
        },
      },
    ],
    // Worklets plugin must be listed last (Reanimated v4 uses react-native-worklets).
    'react-native-worklets/plugin',
  ],
};
