import {NativeModules} from 'react-native';

type SystemNative = {
  openOverlaySettings(): Promise<void>;
  openBatteryOptimizationSettings(): Promise<void>;
  openNotificationSettings(): Promise<void>;
  isIgnoringBatteryOptimizations(): Promise<boolean>;
};

const native = (NativeModules.SystemModule ?? null) as SystemNative | null;

const noop = () => Promise.resolve();

export const System: SystemNative = {
  openOverlaySettings: () => (native ? native.openOverlaySettings() : noop()),
  openBatteryOptimizationSettings: () =>
    native ? native.openBatteryOptimizationSettings() : noop(),
  openNotificationSettings: () =>
    native ? native.openNotificationSettings() : noop(),
  isIgnoringBatteryOptimizations: () =>
    native ? native.isIgnoringBatteryOptimizations() : Promise.resolve(false),
};
