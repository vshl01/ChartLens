import {NativeModules} from 'react-native';

export type InstalledApp = {
  packageName: string;
  name: string;
  iconBase64: string;
};

type AppLauncherNative = {
  getInstalledApps(): Promise<InstalledApp[]>;
  launchApp(packageName: string, deepLink?: string | null): Promise<boolean>;
  isAppInstalled(packageName: string): Promise<boolean>;
  openAppSettings(packageName: string): Promise<void>;
  getAppIcon(packageName: string): Promise<string | null>;
};

const native = (NativeModules.AppLauncherModule ?? null) as AppLauncherNative | null;

const reject = () => Promise.reject(new Error('native unavailable'));

export const AppLauncher: AppLauncherNative = {
  getInstalledApps: () => (native ? native.getInstalledApps() : Promise.resolve([])),
  launchApp: (p, d) => (native ? native.launchApp(p, d ?? null) : reject()),
  isAppInstalled: p => (native ? native.isAppInstalled(p) : Promise.resolve(false)),
  openAppSettings: p => (native ? native.openAppSettings(p) : reject()),
  getAppIcon: p => (native ? native.getAppIcon(p) : Promise.resolve(null)),
};
