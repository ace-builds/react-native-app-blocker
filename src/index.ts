import type { EventSubscription } from 'expo';
import { Platform } from 'react-native';

import type {
  AppInfo,
  AppPickerSummary,
  BlockedAppEvent,
  ShieldOptions,
  StartBlockingOptions,
} from './AppBlocker.types';
import AppBlocker from './AppBlockerModule';

export * from './AppBlocker.types';

function only(platform: 'ios' | 'android', fn: string, instead: string): void {
  if (Platform.OS !== platform) {
    throw new Error(`react-native-app-blocker: ${fn}() is ${platform}-only. ${instead}`);
  }
}

/**
 * iOS: shows the Screen Time authorization prompt.
 * Android: opens Accessibility settings and resolves `false`; check again with
 * `hasPermission()` when your app returns to the foreground.
 */
export function requestPermission(): Promise<boolean> {
  return AppBlocker.requestPermission();
}

export function hasPermission(): Promise<boolean> {
  return AppBlocker.hasPermission();
}

/** Android only. Sets the apps the blocking mode applies to. On iOS, use `presentAppPicker()`. */
export async function setSelectedApps(apps: AppInfo[]): Promise<void> {
  only('android', 'setSelectedApps', 'On iOS, use presentAppPicker().');
  return AppBlocker.setSelectedApps(apps);
}

/** Android only. On iOS, use `getAppPickerSummary()`. */
export async function getSelectedApps(): Promise<AppInfo[]> {
  only('android', 'getSelectedApps', 'On iOS, use getAppPickerSummary().');
  return AppBlocker.getSelectedApps();
}

/** Android: launcher apps on the device, sorted by name. iOS: always `[]` (Apple does not allow listing apps). */
export async function getInstalledApps(): Promise<AppInfo[]> {
  return Platform.OS === 'android' ? AppBlocker.getInstalledApps() : [];
}

/**
 * iOS only. Opens Apple's app picker and saves the selection, which the blocking mode
 * then applies to. Resolves with the new counts, or the old ones if the user cancels.
 * Requires permission first.
 */
export async function presentAppPicker(): Promise<AppPickerSummary> {
  only('ios', 'presentAppPicker', 'On Android, use getInstalledApps() and setSelectedApps().');
  return AppBlocker.presentAppPicker();
}

/** iOS only. Counts for the saved picker selection. */
export async function getAppPickerSummary(): Promise<AppPickerSummary> {
  only('ios', 'getAppPickerSummary', 'On Android, use getSelectedApps().');
  return AppBlocker.getAppPickerSummary();
}

/** Starts blocking. iOS rejects if permission has not been granted. */
export function startBlocking(options: StartBlockingOptions = {}): Promise<void> {
  const { mode = 'allowlist', endTime = 0, message = '' } = options;
  return AppBlocker.startBlocking(mode, endTime, message);
}

export function stopBlocking(): Promise<void> {
  return AppBlocker.stopBlocking();
}

/** True while a session is running and its `endTime` (if any) has not passed. */
export function isBlocking(): Promise<boolean> {
  return AppBlocker.isBlocking();
}

/**
 * Optional. Sets the time left (seconds) that the block screen shows. Without it, the
 * block screen works it out from `endTime`. Use it if your timer can pause.
 */
export function updateRemainingTime(seconds: number): Promise<void> {
  return AppBlocker.updateRemainingTime(seconds);
}

/** Customises the block screen. Merges with options set earlier; persists across launches. */
export function setShieldOptions(options: ShieldOptions): Promise<void> {
  const values: Record<string, string> = {};
  for (const [key, value] of Object.entries(options)) {
    if (value != null) values[key] = String(value);
  }
  return AppBlocker.setShieldOptions(values);
}

/** Android only (iOS shields run in a separate process and cannot notify your app). */
export function addBlockedAppListener(
  listener: (event: BlockedAppEvent) => void
): EventSubscription {
  return AppBlocker.addListener('onBlockedAppAttempt', listener);
}

/**
 * Android apps worth keeping available in allowlist mode. The launcher, dialer,
 * settings and emergency apps are always allowed natively, whatever you pass.
 */
export const DEFAULT_ALLOWED_APPS: AppInfo[] = [
  { bundleId: 'com.google.android.apps.messaging', name: 'Messages' },
  { bundleId: 'com.android.contacts', name: 'Contacts' },
  { bundleId: 'com.google.android.contacts', name: 'Contacts' },
  { bundleId: 'com.google.android.deskclock', name: 'Clock' },
];
