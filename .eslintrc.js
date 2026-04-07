module.exports = {
  root: true,
  extends: ['@react-native', 'plugin:@typescript-eslint/recommended'],
  parser: '@typescript-eslint/parser',
  plugins: ['@typescript-eslint'],
  rules: {
    'react-native/no-inline-styles': 'off',
    '@typescript-eslint/no-unused-vars': ['warn', {argsIgnorePattern: '^_'}],
    '@typescript-eslint/no-explicit-any': 'error',
    'no-console': ['warn', {allow: ['warn', 'error']}],
  },
  ignorePatterns: ['android/', 'node_modules/', 'build/', 'coverage/'],
};
