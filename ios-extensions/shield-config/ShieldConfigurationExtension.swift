// Copied into your project by react-native-app-blocker. Yours to edit; it is not overwritten.
//
// Draws the screen shown when a blocked app is opened. Text and colours come from
// setShieldOptions() in JS; change this file for anything beyond that.
// `appBlockerAppGroup` is defined in AppBlockerConfig.swift (regenerated on prebuild).

import ManagedSettings
import ManagedSettingsUI
import UIKit

class ShieldConfigurationExtension: ShieldConfigurationDataSource {
  override func configuration(shielding application: Application) -> ShieldConfiguration {
    makeConfiguration(appName: application.localizedDisplayName)
  }

  override func configuration(shielding application: Application, in category: ActivityCategory) -> ShieldConfiguration {
    makeConfiguration(appName: application.localizedDisplayName)
  }

  override func configuration(shielding webDomain: WebDomain) -> ShieldConfiguration {
    makeConfiguration(appName: webDomain.domain)
  }

  override func configuration(shielding webDomain: WebDomain, in category: ActivityCategory) -> ShieldConfiguration {
    makeConfiguration(appName: webDomain.domain)
  }

  private func makeConfiguration(appName: String?) -> ShieldConfiguration {
    let defaults = UserDefaults(suiteName: appBlockerAppGroup)
    let options = defaults?.dictionary(forKey: "AppBlocker_ShieldOptions") as? [String: String] ?? [:]
    let message = defaults?.string(forKey: "AppBlocker_Message") ?? ""

    let fill: (String) -> String = { text in
      text
        .replacingOccurrences(of: "{app}", with: appName ?? "This app")
        .replacingOccurrences(of: "{time}", with: Self.timeLeft(defaults))
    }
    var subtitle = fill(options["subtitle"] ?? "{app} is blocked during your focus session.")
    if !message.isEmpty {
      subtitle += "\n\n" + message
    }

    let text = Self.color(options["textColor"]) ?? .white
    return ShieldConfiguration(
      backgroundBlurStyle: .systemUltraThinMaterialDark,
      backgroundColor: Self.color(options["backgroundColor"]) ?? UIColor(red: 0.02, green: 0.05, blue: 0.12, alpha: 1),
      icon: UIImage(systemName: options["iosIcon"] ?? "hourglass"),
      title: .init(text: fill(options["title"] ?? "Stay focused"), color: text),
      subtitle: .init(text: subtitle, color: text.withAlphaComponent(0.8)),
      primaryButtonLabel: .init(text: options["primaryButtonText"] ?? "Close", color: .white),
      primaryButtonBackgroundColor: Self.color(options["accentColor"]) ?? UIColor(red: 0.23, green: 0.42, blue: 1, alpha: 1),
      secondaryButtonLabel: options["secondaryButtonText"].map { ShieldConfiguration.Label(text: $0, color: text.withAlphaComponent(0.6)) }
    )
  }

  /// Uses the value pushed by updateRemainingTime() if any, else derives it from endTime.
  private static func timeLeft(_ defaults: UserDefaults?) -> String {
    var seconds = defaults?.double(forKey: "AppBlocker_RemainingTime") ?? -1
    if seconds < 0 {
      let endTime = defaults?.double(forKey: "AppBlocker_EndTime") ?? 0
      seconds = endTime > 0 ? max(0, endTime / 1000 - Date().timeIntervalSince1970) : 0
    }
    let total = Int(seconds)
    let (h, m, s) = (total / 3600, total % 3600 / 60, total % 60)
    return h > 0 ? String(format: "%d:%02d:%02d", h, m, s) : String(format: "%d:%02d", m, s)
  }

  /// Parses `#RRGGBB`.
  private static func color(_ hex: String?) -> UIColor? {
    guard let hex, hex.hasPrefix("#"), hex.count == 7, let value = UInt32(hex.dropFirst(), radix: 16) else {
      return nil
    }
    return UIColor(
      red: CGFloat((value >> 16) & 0xFF) / 255,
      green: CGFloat((value >> 8) & 0xFF) / 255,
      blue: CGFloat(value & 0xFF) / 255,
      alpha: 1
    )
  }
}
