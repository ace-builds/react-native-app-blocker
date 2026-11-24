// Copied into your project by react-native-app-blocker. Yours to edit; it is not overwritten.
//
// Handles taps on the shield's buttons. Apple only allows `.close` (dismiss the shield
// and the blocked app) or `.defer` (keep the shield up); a shield cannot open your app.
// `appBlockerAppGroup` is defined in AppBlockerConfig.swift (regenerated on prebuild).

import Foundation
import ManagedSettings
import ManagedSettingsUI

class ShieldActionExtension: ShieldActionDelegate {
  override func handle(action: ShieldAction, for application: ApplicationToken, completionHandler: @escaping (ShieldActionResponse) -> Void) {
    completionHandler(respond(to: action))
  }

  override func handle(action: ShieldAction, for webDomain: WebDomainToken, completionHandler: @escaping (ShieldActionResponse) -> Void) {
    completionHandler(respond(to: action))
  }

  override func handle(action: ShieldAction, for category: ActivityCategoryToken, completionHandler: @escaping (ShieldActionResponse) -> Void) {
    completionHandler(respond(to: action))
  }

  private func respond(to action: ShieldAction) -> ShieldActionResponse {
    liftShieldIfSessionEnded()
    return .close
  }

  /// iOS cannot run your app's code when a session ends, so the first tap after
  /// `endTime` removes the shields.
  private func liftShieldIfSessionEnded() {
    guard let defaults = UserDefaults(suiteName: appBlockerAppGroup),
          defaults.bool(forKey: "AppBlocker_IsBlocking") else { return }
    let endTime = defaults.double(forKey: "AppBlocker_EndTime")
    guard endTime > 0, Date().timeIntervalSince1970 * 1000 > endTime else { return }
    ManagedSettingsStore().clearAllSettings()
    defaults.set(false, forKey: "AppBlocker_IsBlocking")
  }
}
