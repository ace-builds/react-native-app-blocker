package expo.modules.appblocker

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject

/**
 * Watches which app comes to the foreground and covers blocked apps with a full-screen
 * overlay.
 *
 * The overlay is a TYPE_ACCESSIBILITY_OVERLAY window added through WindowManager, not an
 * Activity: Android 10+ silently drops startActivity() from a background service, and this
 * window type needs no SYSTEM_ALERT_WINDOW permission.
 */
class AppBlockerAccessibilityService : AccessibilityService() {

  companion object {
    /** Never blocked: system UI, phone, settings and emergency, plus common launchers. */
    private val ALWAYS_ALLOWED = setOf(
      "com.android.systemui",
      "com.android.settings",
      "com.android.dialer",
      "com.google.android.dialer",
      "com.android.incallui",
      "com.android.phone",
      "com.android.server.telecom",
      "com.android.emergency",
      "com.google.android.apps.safetyhub",
      "com.android.launcher",
      "com.android.launcher3",
      "com.google.android.apps.nexuslauncher",
      "com.sec.android.app.launcher",
      "com.miui.home",
      "com.huawei.android.launcher",
      "com.oppo.launcher",
    )
  }

  private val prefs by lazy { applicationContext.blockerPrefs() }
  private val mainHandler = Handler(Looper.getMainLooper())

  private var lastBlockedPackage: String? = null
  private var lastBlockTime = 0L

  /** While the overlay is up every event is ignored, so only its buttons dismiss it. */
  @Volatile private var isOverlayActive = false
  private var overlayView: View? = null
  private val autoClose = Runnable {
    prefs.edit().putBoolean(Keys.IS_BLOCKING, false).apply()
    removeOverlay()
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
    if (isOverlayActive) return
    val pkg = event.packageName?.toString() ?: return

    if (!prefs.blockingActive() || pkg == packageName) {
      removeOverlay()
      return
    }
    if (isAlwaysAllowed(pkg)) return

    val selected = prefs.getStringSet(Keys.APPS, emptySet()) ?: emptySet()
    val blocked = if (prefs.getString(Keys.MODE, "allowlist") == "blocklist") pkg in selected
                  else pkg !in selected
    if (!blocked) {
      removeOverlay()
      return
    }

    // One app launch fires several window events; react once.
    val now = System.currentTimeMillis()
    if (pkg == lastBlockedPackage && now - lastBlockTime < 2000) return
    lastBlockedPackage = pkg
    lastBlockTime = now

    showOverlay(pkg)
  }

  override fun onInterrupt() {}

  override fun onDestroy() {
    removeOverlay()
    super.onDestroy()
  }

  private fun isAlwaysAllowed(pkg: String): Boolean {
    if (pkg in ALWAYS_ALLOWED) return true
    // The keyboard opens its own window inside allowed apps.
    val ime = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
    if (ime?.substringBefore('/') == pkg) return true
    // Whatever launcher this device uses, including OEM ones not listed above.
    val home = packageManager.resolveActivity(
      Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
      PackageManager.MATCH_DEFAULT_ONLY
    )
    return home?.activityInfo?.packageName == pkg
  }

  // ─── Overlay ───────────────────────────────────────────────────────────────

  private fun showOverlay(blockedPackage: String) {
    mainHandler.post {
      removeOverlay()
      val appName = appLabel(blockedPackage) ?: "This app"
      val view = buildOverlay(appName)
      val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
          WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
          WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
      ).apply { gravity = Gravity.TOP or Gravity.START }

      (getSystemService(WINDOW_SERVICE) as WindowManager).addView(view, params)
      overlayView = view
      isOverlayActive = true

      val endTime = prefs.getLong(Keys.END_TIME, 0)
      if (endTime > 0) mainHandler.postDelayed(autoClose, maxOf(0L, endTime - System.currentTimeMillis()))

      AppBlockerModule.emitBlockedAttempt(blockedPackage, appName)
    }
  }

  private fun removeOverlay() {
    isOverlayActive = false
    mainHandler.removeCallbacks(autoClose)
    overlayView?.let {
      runCatching { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) }
      overlayView = null
    }
  }

  /** Time left in seconds: the value pushed by updateRemainingTime(), else derived from endTime. */
  private fun remainingSeconds(): Long {
    val pushed = prefs.getLong(Keys.REMAINING, -1L)
    if (pushed >= 0) return pushed
    val endTime = prefs.getLong(Keys.END_TIME, 0)
    return if (endTime > 0) maxOf(0L, (endTime - System.currentTimeMillis()) / 1000) else 0L
  }

  private fun buildOverlay(appName: String): View {
    val options = prefs.jsonObject(Keys.SHIELD_OPTIONS)
    val remaining = remainingSeconds()
    val time = formatTime(remaining)
    fun str(key: String, fallback: String) =
      (options.optString(key).takeIf { it.isNotEmpty() } ?: fallback)
        .replace("{app}", appName)
        .replace("{time}", time)

    val bgColor = options.color("backgroundColor", "#040C1E")
    val textColor = options.color("textColor", "#FFFFFF")
    val accent = options.color("accentColor", "#3A6AFF")
    val hostName = appLabel(packageName) ?: "app"

    val density = resources.displayMetrics.density
    fun Int.dp() = (this * density).toInt()
    fun matchWidth(bottomMargin: Int) = LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.MATCH_PARENT,
      LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { setMargins(0, 0, 0, bottomMargin.dp()) }

    val root = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      gravity = Gravity.CENTER_HORIZONTAL
      setPadding(48.dp(), 80.dp(), 48.dp(), 80.dp())
    }

    // Host app icon inside two rings
    val ring = FrameLayout(this).apply {
      layoutParams = LinearLayout.LayoutParams(140.dp(), 140.dp()).apply {
        gravity = Gravity.CENTER_HORIZONTAL
        setMargins(0, 0, 0, 32.dp())
      }
      background = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setStroke(1.dp(), accent.withAlpha(0.3f))
      }
    }
    val inner = FrameLayout(this).apply {
      layoutParams = FrameLayout.LayoutParams(100.dp(), 100.dp(), Gravity.CENTER)
      background = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(textColor.withAlpha(0.05f))
        setStroke(1.dp(), accent.withAlpha(0.4f))
      }
    }
    inner.addView(ImageView(this).apply {
      layoutParams = FrameLayout.LayoutParams(64.dp(), 64.dp(), Gravity.CENTER)
      runCatching { setImageDrawable(packageManager.getApplicationIcon(packageName)) }
      scaleType = ImageView.ScaleType.FIT_CENTER
    })
    ring.addView(inner)
    root.addView(ring)

    root.addView(TextView(this).apply {
      text = str("title", "Stay focused")
      textSize = 28f
      setTextColor(textColor)
      gravity = Gravity.CENTER
      typeface = Typeface.create("sans-serif", Typeface.BOLD)
      layoutParams = matchWidth(12)
    })

    val message = prefs.getString(Keys.MESSAGE, "") ?: ""
    root.addView(TextView(this).apply {
      text = str("subtitle", "{app} is blocked during your focus session.") +
        if (message.isNotBlank()) "\n\n$message" else ""
      textSize = 15f
      setTextColor(textColor.withAlpha(0.65f))
      gravity = Gravity.CENTER
      setLineSpacing(4f * density, 1f)
      layoutParams = matchWidth(40)
    })

    // Countdown card: a snapshot of the time left, not a ticking clock.
    if (remaining > 0) {
      val card = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(32.dp(), 32.dp(), 32.dp(), 32.dp())
        background = GradientDrawable().apply {
          setColor(textColor.withAlpha(0.04f))
          cornerRadius = 20f * density
          setStroke(1.dp(), textColor.withAlpha(0.1f))
        }
        layoutParams = matchWidth(36)
      }
      card.addView(TextView(this).apply {
        text = str("timerLabel", "FOCUS SESSION")
        textSize = 11f
        letterSpacing = 0.12f
        setTextColor(accent)
        gravity = Gravity.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setPadding(0, 0, 0, 10.dp())
      })
      card.addView(TextView(this).apply {
        text = time
        textSize = 58f
        setTextColor(textColor)
        gravity = Gravity.CENTER
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        setPadding(0, 0, 0, 6.dp())
      })
      card.addView(TextView(this).apply {
        text = str("timerCaption", "remaining")
        textSize = 13f
        setTextColor(textColor.withAlpha(0.4f))
        gravity = Gravity.CENTER
      })
      root.addView(card)
    }

    root.addView(TextView(this).apply {
      text = str("primaryButtonText", "Return to $hostName")
      textSize = 16f
      setTextColor(Color.WHITE)
      gravity = Gravity.CENTER
      typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
      setPadding(0, 18.dp(), 0, 18.dp())
      background = GradientDrawable().apply {
        setColor(accent)
        cornerRadius = 50f * density
      }
      layoutParams = matchWidth(16)
      setOnClickListener { openHostApp() }
    })

    options.optString("secondaryButtonText").takeIf { it.isNotEmpty() }?.let { label ->
      root.addView(TextView(this).apply {
        text = label
        textSize = 15f
        setTextColor(textColor.withAlpha(0.5f))
        gravity = Gravity.CENTER
        setPadding(0, 8.dp(), 0, 8.dp())
        layoutParams = matchWidth(0)
        setOnClickListener { goHome() }
      })
    }

    return ScrollView(this).apply {
      setBackgroundColor(bgColor)
      isFillViewport = true
      addView(root)
    }
  }

  // ─── Actions ───────────────────────────────────────────────────────────────

  private fun openHostApp() {
    removeOverlay()
    packageManager.getLaunchIntentForPackage(packageName)?.let {
      startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
  }

  private fun goHome() {
    removeOverlay()
    startActivity(
      Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_HOME)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
  }

  // ─── Helpers ───────────────────────────────────────────────────────────────

  private fun appLabel(pkg: String): String? = try {
    packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
  } catch (_: PackageManager.NameNotFoundException) {
    null
  }

  private fun formatTime(seconds: Long): String {
    val h = seconds / 3600
    val m = seconds % 3600 / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
  }

  private fun JSONObject.color(key: String, fallback: String): Int =
    runCatching { Color.parseColor(optString(key)) }.getOrElse { Color.parseColor(fallback) }

  private fun Int.withAlpha(alpha: Float): Int =
    Color.argb((alpha * 255).toInt(), Color.red(this), Color.green(this), Color.blue(this))
}
