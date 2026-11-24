package expo.modules.appblocker

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import org.json.JSONObject

/** SharedPreferences keys shared by the module and the accessibility service. */
internal object Keys {
  const val PREFS = "AppBlockerPrefs"
  const val IS_BLOCKING = "isBlocking"
  const val MODE = "mode"
  const val APPS = "selectedApps"
  const val APP_NAMES = "selectedAppNames"
  const val MESSAGE = "message"
  const val END_TIME = "endTime"
  const val REMAINING = "remainingSeconds"
  const val SHIELD_OPTIONS = "shieldOptions"
}

internal fun Context.blockerPrefs(): SharedPreferences =
  getSharedPreferences(Keys.PREFS, Context.MODE_PRIVATE)

/** True while a session runs; clears the flag once `endTime` has passed. */
internal fun SharedPreferences.blockingActive(): Boolean {
  if (!getBoolean(Keys.IS_BLOCKING, false)) return false
  val endTime = getLong(Keys.END_TIME, 0)
  if (endTime > 0 && System.currentTimeMillis() > endTime) {
    edit().putBoolean(Keys.IS_BLOCKING, false).apply()
    return false
  }
  return true
}

internal fun SharedPreferences.jsonObject(key: String): JSONObject =
  getString(key, null)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()

/** Blocks apps with an Accessibility Service that draws an overlay over blocked apps. */
class AppBlockerModule : Module() {

  companion object {
    @Volatile private var instance: AppBlockerModule? = null

    /** Called by the service; no-op when the JS side is not running. */
    internal fun emitBlockedAttempt(packageName: String, appName: String) {
      instance?.sendEvent(
        "onBlockedAppAttempt",
        mapOf(
          "bundleId" to packageName,
          "appName" to appName,
          "timestamp" to System.currentTimeMillis().toDouble()
        )
      )
    }
  }

  private val context: Context
    get() = requireNotNull(appContext.reactContext)

  private val prefs: SharedPreferences by lazy { context.blockerPrefs() }

  override fun definition() = ModuleDefinition {
    Name("AppBlocker")

    Events("onBlockedAppAttempt")

    OnCreate { instance = this@AppBlockerModule }
    OnDestroy { if (instance === this@AppBlockerModule) instance = null }

    AsyncFunction("requestPermission") {
      if (isServiceEnabled()) return@AsyncFunction true
      context.startActivity(
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      )
      false
    }

    AsyncFunction("hasPermission") {
      isServiceEnabled()
    }

    AsyncFunction("setSelectedApps") { apps: List<Map<String, String>> ->
      val names = JSONObject()
      val ids = apps.mapNotNull { app ->
        app["bundleId"]?.takeIf { it.isNotEmpty() }?.also { names.put(it, app["name"] ?: "") }
      }.toSet()
      prefs.edit()
        .putStringSet(Keys.APPS, ids)
        .putString(Keys.APP_NAMES, names.toString())
        .apply()
    }

    AsyncFunction("getSelectedApps") {
      val names = prefs.jsonObject(Keys.APP_NAMES)
      (prefs.getStringSet(Keys.APPS, emptySet()) ?: emptySet()).map { id ->
        mapOf("bundleId" to id, "name" to (names.optString(id).takeIf { it.isNotEmpty() } ?: id))
      }
    }

    AsyncFunction("getInstalledApps") {
      installedApps()
    }

    // No permission check on purpose: the service may start later and will pick these up.
    AsyncFunction("startBlocking") { mode: String, endTime: Double, message: String ->
      prefs.edit()
        .putBoolean(Keys.IS_BLOCKING, true)
        .putString(Keys.MODE, mode)
        .putLong(Keys.END_TIME, endTime.toLong())
        .putString(Keys.MESSAGE, message)
        .putLong(Keys.REMAINING, -1L) // derive from endTime until the app pushes a value
        .commit() // synchronous, so the service never reads stale state
    }

    AsyncFunction("stopBlocking") {
      prefs.edit().putBoolean(Keys.IS_BLOCKING, false).commit()
    }

    AsyncFunction("isBlocking") {
      prefs.blockingActive()
    }

    AsyncFunction("updateRemainingTime") { seconds: Double ->
      prefs.edit().putLong(Keys.REMAINING, seconds.toLong()).apply()
    }

    AsyncFunction("setShieldOptions") { options: Map<String, String> ->
      val merged = prefs.jsonObject(Keys.SHIELD_OPTIONS)
      options.forEach { (key, value) -> merged.put(key, value) }
      prefs.edit().putString(Keys.SHIELD_OPTIONS, merged.toString()).apply()
    }
  }

  private fun isServiceEnabled(): Boolean {
    val enabled = Settings.Secure.getString(
      context.contentResolver,
      Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    val expected = ComponentName(context, AppBlockerAccessibilityService::class.java)
    return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
  }

  private fun installedApps(): List<Map<String, String>> {
    val pm = context.packageManager
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val activities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      pm.queryIntentActivities(launcher, PackageManager.ResolveInfoFlags.of(0))
    } else {
      @Suppress("DEPRECATION")
      pm.queryIntentActivities(launcher, 0)
    }
    return activities
      .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
      .filter { (pkg, _) -> pkg != context.packageName }
      .distinctBy { (pkg, _) -> pkg }
      .sortedBy { (_, name) -> name.lowercase() }
      .map { (pkg, name) -> mapOf("bundleId" to pkg, "name" to name) }
  }
}
