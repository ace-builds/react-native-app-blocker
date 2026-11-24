import { NativeModule, requireNativeModule } from 'expo';

import type {
  AppBlockerModuleEvents,
  AppInfo,
  AppPickerSummary,
  BlockingMode,
} from './AppBlocker.types';

declare class AppBlockerNativeModule extends NativeModule<AppBlockerModuleEvents> {
  requestPermission(): Promise<boolean>;
  hasPermission(): Promise<boolean>;
  startBlocking(mode: BlockingMode, endTime: number, message: string): Promise<void>;
  stopBlocking(): Promise<void>;
  isBlocking(): Promise<boolean>;
  updateRemainingTime(seconds: number): Promise<void>;
  setShieldOptions(options: Record<string, string>): Promise<void>;
  // Android only
  setSelectedApps(apps: AppInfo[]): Promise<void>;
  getSelectedApps(): Promise<AppInfo[]>;
  getInstalledApps(): Promise<AppInfo[]>;
  // iOS only
  presentAppPicker(): Promise<AppPickerSummary>;
  getAppPickerSummary(): Promise<AppPickerSummary>;
}

export default requireNativeModule<AppBlockerNativeModule>('AppBlocker');
