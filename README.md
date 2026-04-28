# ChartLens

Personal-use Android app (React Native CLI, sideloaded) that lets you tap a broker app, float a draggable bubble, capture the candlestick chart on screen, and ask Google Gemini Vision to analyse it.

## Status

This is the **Step 1 scaffold** from the build plan: project init, TypeScript strict mode with path aliases, ESLint + Prettier + Husky, Material 3 (`react-native-paper`) theming, navigation skeleton (Onboarding → Tabs: Home / Presets / History / Settings, plus Diagnostics), zustand stores, MMKV + Keychain storage, Gemini service, and TypeScript wrappers for the four native modules (`MediaProjectionModule`, `OverlayModule`, `AppLauncherModule`, `SystemModule`).

The Kotlin native modules themselves are scoped for **Step 2 onward** — the JS layer falls back gracefully when they are not yet registered, so the app builds and runs today.

## Requirements

- Android SDK 36, build-tools 36.0.0
- Node 22.11+
- JDK 17

## Setup

```bash
npm install
npm run android
```

## Project layout

```
ChartLens/
├── android/
├── App.tsx
├── index.js
└── src/
    ├── components/   # BrokerCard, …
    ├── screens/      # Onboarding, Home, Presets, History, Settings, Diagnostics
    ├── navigation/   # Root stack + bottom tabs
    ├── native/       # TS wrappers around Kotlin modules
    ├── services/     # gemini, storage (MMKV), secureStorage (Keychain), permissions
    ├── registry/     # brokers.ts, presets.ts
    ├── store/        # zustand stores
    ├── theme/        # palette, paperTheme, spacing, typography
    ├── types/
    └── utils/
```

Path aliases (`@/`, `@screens/`, `@components/`, `@native/`, `@services/`, `@registry/`, `@store/`, `@theme/`, `@utils/`) are configured in both `tsconfig.json` and `babel.config.js`.

## Scripts

| script | purpose |
| --- | --- |
| `npm run android` | run debug build |
| `npm run start` | Metro |
| `npm run lint` / `lint:fix` | ESLint |
| `npm run format` | Prettier |
| `npm run typecheck` | `tsc --noEmit` |
| `npm run build:android` | `./gradlew assembleDebug` |
| `npm test` | jest |

Husky + lint-staged run ESLint + Prettier on staged files at commit time.

## FLAG_SECURE / black-frame caveat

Most Indian broker apps (Zerodha Kite, Groww, etc.) set `FLAG_SECURE`, which makes `MediaProjection` return all-black frames. ChartLens does **not** attempt to defeat that flag in user space. To use it against those apps, install **LSPosed + DisableFlagSecure** on a rooted device:

1. Install Magisk + Zygisk.
2. Install LSPosed (Zygisk flavor).
3. Install the **DisableFlagSecure** module from its GitHub releases.
4. Enable the module against the broker package(s) you want to capture, then reboot.

If frames still come back essentially black, the app surfaces a toast with that exact remediation.

## Adding a new broker

Edit [`src/registry/brokers.ts`](src/registry/brokers.ts):

```ts
{
  id: 'my_broker',
  name: 'My Broker',
  packageName: 'com.example.broker',
  brand: { primary: '#3F51B5' },
}
```

## Native modules (planned, Step 5–6)

Registered in `ChartLensPackage`. TS contracts already exist in [`src/native/`](src/native/):

| module | purpose |
| --- | --- |
| `MediaProjectionModule` | foreground service + `VirtualDisplay` + `ImageReader` capture |
| `OverlayModule` | floating bubble (`WindowManager`) + drag/snap/trash/events |
| `AppLauncherModule` | enumerate / launch broker apps, fetch icons |
| `SystemModule` | deep-link to overlay / battery / notification settings |
