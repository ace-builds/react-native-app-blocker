/** An app on the device. `bundleId` is the Android package name (e.g. `com.instagram.android`). */
export type AppInfo = {
  bundleId: string;
  name?: string;
};

/**
 * - `allowlist`: block every app except the selected ones (system apps such as the
 *   launcher, dialer and settings are never blocked on Android).
 * - `blocklist`: block only the selected apps.
 */
export type BlockingMode = 'allowlist' | 'blocklist';

export type StartBlockingOptions = {
  /** Defaults to `allowlist`. */
  mode?: BlockingMode;
  /**
   * When the session ends, as a Unix timestamp in milliseconds (`Date.now() + ms`).
   * Leave it out to block until you call `stopBlocking()`.
   */
  endTime?: number;
  /** Extra line shown under the subtitle on the block screen, for this session only. */
  message?: string;
};

/**
 * Text and colours for the block screen (Android overlay and iOS Shield).
 * Every field is optional; unset fields keep their defaults.
 * `title` and `subtitle` accept the placeholders `{app}` (blocked app name) and `{time}` (time left).
 */
export type ShieldOptions = {
  title?: string;
  subtitle?: string;
  /** Android: returns to your app. iOS: closes the shield (Apple does not let a shield open your app). */
  primaryButtonText?: string;
  /** Hidden unless set. Android: goes to the home screen. iOS: closes the shield. */
  secondaryButtonText?: string;
  /** `#RRGGBB` */
  backgroundColor?: string;
  /** `#RRGGBB` */
  textColor?: string;
  /** `#RRGGBB`, used for the primary button. */
  accentColor?: string;
  /** Android only: small label above the countdown. */
  timerLabel?: string;
  /** Android only: caption under the countdown. */
  timerCaption?: string;
  /** iOS only: SF Symbol name for the shield icon. Android shows your app icon. */
  iosIcon?: string;
};

/** iOS only. Screen Time hides which apps were picked; you get counts. */
export type AppPickerSummary = {
  applicationCount: number;
  categoryCount: number;
  webDomainCount: number;
};

/** Android only: fired when the user opens a blocked app. */
export type BlockedAppEvent = {
  bundleId: string;
  appName: string;
  timestamp: number;
};

export type AppBlockerModuleEvents = {
  onBlockedAppAttempt: (event: BlockedAppEvent) => void;
};
