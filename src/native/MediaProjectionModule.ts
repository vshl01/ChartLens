import {NativeEventEmitter, NativeModules} from 'react-native';

export type ProjectionToken = {resultCode: number; data: unknown};

export type CaptureFrameResult = {
  base64: string;
  width: number;
  height: number;
  rotation: 0 | 90 | 180 | 270;
  capturedAtMs: number;
};

type MediaProjectionNative = {
  requestProjection(): Promise<ProjectionToken>;
  startService(resultCode: number): Promise<void>;
  captureFrame(): Promise<CaptureFrameResult>;
  stopService(): Promise<void>;
  isServiceRunning(): Promise<boolean>;
};

const native = (NativeModules.MediaProjectionModule ?? null) as MediaProjectionNative | null;

export const MediaProjection: MediaProjectionNative = {
  requestProjection: () =>
    native ? native.requestProjection() : Promise.reject(new Error('native unavailable')),
  startService: rc =>
    native ? native.startService(rc) : Promise.reject(new Error('native unavailable')),
  captureFrame: () =>
    native ? native.captureFrame() : Promise.reject(new Error('native unavailable')),
  stopService: () =>
    native ? native.stopService() : Promise.reject(new Error('native unavailable')),
  isServiceRunning: () =>
    native ? native.isServiceRunning() : Promise.resolve(false),
};

export const MediaProjectionEvents = native
  ? new NativeEventEmitter(NativeModules.MediaProjectionModule)
  : null;
