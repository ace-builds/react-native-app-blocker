# react-native-app-blocker

Block distracting apps during focus sessions in React Native and Expo apps.

- **iOS**: Apple's Screen Time APIs (FamilyControls + ManagedSettings), with a customisable Shield screen.
- **Android**: an Accessibility Service that covers blocked apps with a customisable full-screen overlay.
- **Expo config plugin** sets up entitlements, the App Group, the iOS Shield extensions and the Android service for you.
- **Allowlist or blocklist**: block everything except chosen apps, or only the chosen apps.

> Needs a development build. It does not work in Expo Go, and the iOS side only works on a real device (the Screen Time prompt and shields do not run in the simulator).

## Installation

```sh
npx expo install react-native-app-blocker
```

Add the plugin to `app.json` (or `app.config.js`):

```json
{
  "expo": {
    "ios": { "bundleIdentifier": "com.yourcompany.yourapp", "appleTeamId": "YOURTEAMID" },
    "android": { "package": "com.yourcompany.yourapp" },
    "plugins": ["react-native-app-blocker"]
  }
}
```

Then rebuild the native app:

```sh
npx expo prebuild
npx expo run:ios --device   # or: npx expo run:android
```

### Plugin options

```json
["react-native-app-blocker", {
  "appGroup": "group.com.yourcompany.yourapp",
  "androidAccessibilityServiceDescription": "YourApp uses this to show a reminder when you open a blocked app during a focus session. It does not read or send screen content."
}]
```

| Option | Default | What it does |
| --- | --- | --- |
| `appGroup` | `group.<ios.bundleIdentifier>` | App Group shared between your app and the Shield extensions. |
| `iosExtensions` | `true` | Adds the two Shield extensions. Set `false` to bring your own. |
| `iosExtensionsDir` | `targets/app-blocker` | Where the editable extension sources are copied. |
| `androidAccessibilityServiceDescription` | Generic English text | Text shown on Android's Accessibility settings screen for your service. Write it for your app; Google Play reviews it. |

## Quick start

```tsx
import { Platform } from 'react-native';
import * as AppBlocker from 'react-native-app-blocker';

// 1. Permission
await AppBlocker.requestPermission();
// iOS: system prompt. Android: opens Accessibility settings; check
// hasPermission() again when your app returns to the foreground.

// 2. Choose apps
if (Platform.OS === 'ios') {
  await AppBlocker.presentAppPicker(); // Apple's picker; selection is saved
} else {
  const apps = await AppBlocker.getInstalledApps(); // [{ bundleId, name }]
  await AppBlocker.setSelectedApps(apps.filter((a) => myChoices.includes(a.bundleId)));
}

// 3. Block
await AppBlocker.startBlocking({
  mode: 'blocklist', // or 'allowlist' (default): block everything except the selection
  endTime: Date.now() + 25 * 60 * 1000,
  message: 'Back to work.',
});

// 4. Stop early (otherwise it ends at endTime)
await AppBlocker.stopBlocking();
```

A runnable version lives in [`example/App.tsx`](example/App.tsx).

## API

| Function | iOS | Android | Notes |
| --- | :-: | :-: | --- |
| `requestPermission(): Promise<boolean>` | ✓ | ✓ | Android opens settings and resolves `false`. |
| `hasPermission(): Promise<boolean>` | ✓ | ✓ | |
| `presentAppPicker(): Promise<AppPickerSummary>` | ✓ | | Apple's picker. Resolves with counts; Apple hides which apps were picked. |
| `getAppPickerSummary(): Promise<AppPickerSummary>` | ✓ | | Counts for the saved selection. |
| `getInstalledApps(): Promise<AppInfo[]>` | `[]` | ✓ | Launcher apps, sorted by name. Apple does not allow listing apps. |
| `setSelectedApps(apps: AppInfo[])` | | ✓ | |
| `getSelectedApps(): Promise<AppInfo[]>` | | ✓ | |
| `startBlocking({ mode?, endTime?, message? })` | ✓ | ✓ | `endTime` is ms since epoch; omit to block until `stopBlocking()`. |
| `stopBlocking()` | ✓ | ✓ | |
| `isBlocking(): Promise<boolean>` | ✓ | ✓ | `false` once `endTime` has passed. |
| `setShieldOptions(options: ShieldOptions)` | ✓ | ✓ | See [Customising the block screen](#customising-the-block-screen). |
| `updateRemainingTime(seconds)` | ✓ | ✓ | Optional. Pin the time shown on the block screen, e.g. while your timer is paused. |
| `addBlockedAppListener(cb)` | | ✓ | `cb({ bundleId, appName, timestamp })` when a blocked app is opened. Returns a subscription with `.remove()`. |
| `DEFAULT_ALLOWED_APPS` | | ✓ | Messages, Contacts and Clock, handy to merge into an allowlist. |

Android-only and iOS-only functions throw on the other platform, so mistakes surface during development instead of silently not blocking.

In allowlist mode on Android, system UI, the launcher, the keyboard, phone, settings and emergency apps are never blocked, so a user cannot lock themselves out of their phone.

## Customising the block screen

### Text and colours (both platforms)

```ts
await AppBlocker.setShieldOptions({
  title: 'Not now',
  subtitle: '{app} is blocked for another {time}.',
  primaryButtonText: 'Back to MyApp', // Android: opens your app. iOS: closes the shield.
  secondaryButtonText: 'Go home',     // optional second button
  backgroundColor: '#0B1020',
  textColor: '#FFFFFF',
  accentColor: '#7C3AED',
  timerLabel: 'FOCUS SESSION',        // Android only
  timerCaption: 'remaining',          // Android only
  iosIcon: 'hourglass',               // iOS only, any SF Symbol name
});
```

`{app}` becomes the blocked app's name and `{time}` the time left. Options persist across launches and merge with earlier calls. Pass translated strings to localise the screen, since the native screens cannot read your JS i18n setup.

### iOS: edit the Shield extensions

On the first prebuild the plugin copies two Swift extensions into your project:

```
targets/app-blocker/
  shield-config/ShieldConfigurationExtension.swift   ← the block screen
  shield-action/ShieldActionExtension.swift          ← what the buttons do
```

They are yours: commit them and change anything. The plugin never overwrites them; delete a folder to get the original back on the next prebuild. Only `AppBlockerConfig.swift` (the App Group constant) is regenerated each time.

Apple limits what a shield can do: its buttons can close the shield (`.close`) or leave it up (`.defer`), but cannot open your app.

### Android: overlay and service description

The overlay is built in [`AppBlockerAccessibilityService.kt`](android/src/main/java/expo/modules/appblocker/AppBlockerAccessibilityService.kt). For layout changes beyond `setShieldOptions`, use [`patch-package`](https://github.com/ds300/patch-package) or fork.

To translate the Accessibility settings text, define `app_blocker_accessibility_service_description` in your app's own `res/values-xx/strings.xml` (for example, with a small config plugin).

## Shipping to the stores

### iOS

1. **Family Controls entitlement.** Development builds work straight away. For TestFlight and the App Store, [request the Family Controls (Distribution) entitlement](https://developer.apple.com/contact/request/family-controls-distribution) for **three** bundle IDs: your app, `<bundleId>.app-blocker-shield-config` and `<bundleId>.app-blocker-shield-action`.
2. **App Group.** Register the App Group (default `group.<bundleId>`) in your Apple Developer account and enable it for all three IDs. EAS Build does this for you when it manages credentials.
3. Set `ios.appleTeamId` in your app config so the extension targets are signed.

### Android

1. **Google Play's Accessibility policy.** Apps that use the Accessibility API must show an in-app disclosure explaining the use and get consent *before* sending the user to settings, and fill in the Accessibility declaration in Play Console. Blocking distracting apps is an accepted use; describe it plainly.
2. **Sideloaded builds on Android 13+.** Android greys out the service for apps installed outside an app store ("Restricted setting"). Testers can enable it under *App info → ⋮ → Allow restricted settings*. Play Store installs are not affected.

## Limitations

- **iOS does not unblock exactly at `endTime`.** Apple only lets code run at a scheduled time from a DeviceActivity monitor extension. Shields are removed when your app calls `stopBlocking()`, when the app next starts after `endTime`, or when the user taps a shield button after `endTime`. Call `stopBlocking()` from your timer's completion handler.
- **iOS hides app identities.** You get opaque tokens from Apple's picker, never bundle IDs or names, so `setSelectedApps` is Android-only.
- **iOS allowlist mode** shields every app category except the apps picked. Categories picked in the picker are ignored in this mode; pick individual apps.
- `addBlockedAppListener` is Android-only, because iOS shields run in a separate process.

## Already using `@bacons/apple-targets`?

This package uses it internally to add the Shield extensions, from `targets/app-blocker/*`. That is one level below the `targets/*` folders `@bacons/apple-targets` scans by default, so the two do not pick up each other's targets. If you already have your own Shield extensions, set `iosExtensions: false` and read the shared state from your extensions (keys below).

### Shared state (for custom extensions)

The module writes these keys to `UserDefaults(suiteName: <appGroup>)`:

| Key | Type | Meaning |
| --- | --- | --- |
| `AppBlocker_IsBlocking` | Bool | A session is running |
| `AppBlocker_EndTime` | Double | Session end, ms since epoch (`0` = no end) |
| `AppBlocker_Message` | String | Per-session `message` |
| `AppBlocker_RemainingTime` | Double | Seconds from `updateRemainingTime()`, `-1` if not set |
| `AppBlocker_ShieldOptions` | [String: String] | `setShieldOptions()` values |

## Bare React Native

The package is an Expo module, so a bare app needs [`expo-modules-core`](https://docs.expo.dev/bare/installing-expo-modules/) (`npx install-expo-modules@latest`). Config plugins do not run in bare projects, so do by hand what the plugin does: add the Family Controls and App Group entitlements, set `AppBlockerAppGroup` in `Info.plist`, and add the two Shield extension targets from `ios-extensions/` in Xcode. Android needs no extra setup.

## Contributing

```sh
npm install
npm run build              # watches src/ and plugin/src/
cd example && npm install
npx expo prebuild && npx expo run:android   # or run:ios --device
```

The example app uses the package from the parent folder, so native and JS changes show up after a rebuild.

## License

MIT
