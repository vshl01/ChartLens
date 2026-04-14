import {PermissionsAndroid, Platform} from 'react-native';

export type PermissionId =
  | 'overlay'
  | 'notifications'
  | 'mediaProjection'
  | 'batteryOptimization';

export type PermissionStatus = 'granted' | 'denied' | 'unavailable' | 'deferred';

export type PermissionState = {
  id: PermissionId;
  label: string;
  why: string;
  status: PermissionStatus;
};

export async function checkNotifications(): Promise<PermissionStatus> {
  if (Platform.OS !== 'android') return 'unavailable';
  if (Platform.Version < 33) return 'granted';
  const granted = await PermissionsAndroid.check(
    PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS,
  );
  return granted ? 'granted' : 'denied';
}

export async function requestNotifications(): Promise<PermissionStatus> {
  if (Platform.OS !== 'android') return 'unavailable';
  if (Platform.Version < 33) return 'granted';
  const result = await PermissionsAndroid.request(
    PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS,
  );
  return result === PermissionsAndroid.RESULTS.GRANTED ? 'granted' : 'denied';
}
