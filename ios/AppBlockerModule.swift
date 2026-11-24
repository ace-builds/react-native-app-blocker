import ExpoModulesCore
import FamilyControls
import ManagedSettings
import SwiftUI

/// Blocks apps with Apple's Screen Time APIs (FamilyControls + ManagedSettings).
///
/// State lives in the App Group so the Shield extensions (separate processes) can read
/// it. The key names below are shared with ios-extensions/*/ — keep them in sync.
public class AppBlockerModule: Module {
  private let store = ManagedSettingsStore()

  private enum Key {
    static let isBlocking = "AppBlocker_IsBlocking"
    static let mode = "AppBlocker_Mode"
    static let endTime = "AppBlocker_EndTime"
    static let message = "AppBlocker_Message"
    static let remainingTime = "AppBlocker_RemainingTime"
    static let shieldOptions = "AppBlocker_ShieldOptions"
    static let selection = "AppBlocker_Selection"
  }

  /// `AppBlockerAppGroup` is written to Info.plist by the config plugin.
  private lazy var defaults: UserDefaults = {
    if let group = Bundle.main.object(forInfoDictionaryKey: "AppBlockerAppGroup") as? String,
       let suite = UserDefaults(suiteName: group) {
      return suite
    }
    log.warn("AppBlocker: no App Group configured; the Shield extensions will show default text. Add the react-native-app-blocker config plugin.")
    return .standard
  }()

  public func definition() -> ModuleDefinition {
    Name("AppBlocker")

    // Android-only event; declared so addListener works on both platforms.
    Events("onBlockedAppAttempt")

    OnCreate {
      _ = self.clearIfExpired()
    }

    AsyncFunction("requestPermission") { () async -> Bool in
      guard #available(iOS 16.0, *) else { return false }
      do {
        try await AuthorizationCenter.shared.requestAuthorization(for: .individual)
      } catch {
        return false
      }
      return AuthorizationCenter.shared.authorizationStatus == .approved
    }

    AsyncFunction("hasPermission") { () -> Bool in
      AuthorizationCenter.shared.authorizationStatus == .approved
    }

    AsyncFunction("presentAppPicker") { (promise: Promise) in
      guard let presenter = self.appContext?.utilities?.currentViewController() else {
        promise.reject("ERR_NO_VIEW_CONTROLLER", "No view controller to present the app picker from")
        return
      }
      let saved = self.loadSelection()
      let controller = UIHostingController(rootView: AppPickerView(selection: saved) { [weak presenter] picked in
        presenter?.dismiss(animated: true)
        if let picked {
          self.saveSelection(picked)
        }
        promise.resolve(Self.summary(picked ?? saved))
      })
      // Swipe-to-dismiss would leave the promise pending; force Cancel/Done.
      controller.isModalInPresentation = true
      presenter.present(controller, animated: true)
    }.runOnQueue(.main)

    AsyncFunction("getAppPickerSummary") { () -> [String: Int] in
      Self.summary(self.loadSelection())
    }

    AsyncFunction("startBlocking") { (mode: String, endTime: Double, message: String) in
      guard AuthorizationCenter.shared.authorizationStatus == .approved else {
        throw NotAuthorizedException()
      }
      self.defaults.set(true, forKey: Key.isBlocking)
      self.defaults.set(mode, forKey: Key.mode)
      self.defaults.set(endTime, forKey: Key.endTime)
      self.defaults.set(message, forKey: Key.message)
      // -1 tells the shield to derive the time left from endTime.
      self.defaults.set(-1, forKey: Key.remainingTime)
      self.applyShield(mode: mode)
    }

    AsyncFunction("stopBlocking") {
      self.stop()
    }

    AsyncFunction("isBlocking") { () -> Bool in
      self.clearIfExpired()
    }

    AsyncFunction("updateRemainingTime") { (seconds: Double) in
      self.defaults.set(seconds, forKey: Key.remainingTime)
    }

    AsyncFunction("setShieldOptions") { (options: [String: String]) in
      var merged = self.defaults.dictionary(forKey: Key.shieldOptions) as? [String: String] ?? [:]
      merged.merge(options) { _, new in new }
      self.defaults.set(merged, forKey: Key.shieldOptions)
    }
  }

  // MARK: - Shielding

  private func applyShield(mode: String) {
    let selection = loadSelection()
    store.clearAllSettingsCompat()
    if mode == "blocklist" {
      store.shield.applications = selection.applicationTokens.isEmpty ? nil : selection.applicationTokens
      store.shield.applicationCategories = selection.categoryTokens.isEmpty ? nil : .specific(selection.categoryTokens)
      store.shield.webDomains = selection.webDomainTokens.isEmpty ? nil : selection.webDomainTokens
    } else {
      store.shield.applicationCategories = .all(except: selection.applicationTokens)
    }
  }

  private func stop() {
    store.clearAllSettingsCompat()
    defaults.set(false, forKey: Key.isBlocking)
    defaults.set(0, forKey: Key.endTime)
  }

  /// Returns whether a session is still running. iOS cannot run code at `endTime`
  /// without a DeviceActivity extension, so an expired session is cleared here,
  /// on the next launch or call, and by the shield-action extension on tap.
  @discardableResult
  private func clearIfExpired() -> Bool {
    guard defaults.bool(forKey: Key.isBlocking) else { return false }
    let endTime = defaults.double(forKey: Key.endTime)
    if endTime > 0 && Date().timeIntervalSince1970 * 1000 > endTime {
      stop()
      return false
    }
    return true
  }

  // MARK: - Picker selection

  private func loadSelection() -> FamilyActivitySelection {
    guard let data = defaults.data(forKey: Key.selection),
          let selection = try? JSONDecoder().decode(FamilyActivitySelection.self, from: data) else {
      return FamilyActivitySelection()
    }
    return selection
  }

  private func saveSelection(_ selection: FamilyActivitySelection) {
    if let data = try? JSONEncoder().encode(selection) {
      defaults.set(data, forKey: Key.selection)
    }
  }

  private static func summary(_ selection: FamilyActivitySelection) -> [String: Int] {
    [
      "applicationCount": selection.applicationTokens.count,
      "categoryCount": selection.categoryTokens.count,
      "webDomainCount": selection.webDomainTokens.count,
    ]
  }
}

private extension ManagedSettingsStore {
  func clearAllSettingsCompat() {
    if #available(iOS 16.0, *) {
      clearAllSettings()
    } else {
      shield.applications = nil
      shield.applicationCategories = nil
      shield.webDomains = nil
      shield.webDomainCategories = nil
    }
  }
}

final class NotAuthorizedException: Exception {
  override var reason: String {
    "Screen Time permission has not been granted. Call requestPermission() first."
  }
}
