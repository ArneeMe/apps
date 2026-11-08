package me.arnee.verktoy.rodfilter

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

class FilterTjeneste : AccessibilityService() {
    private var lag: View? = null

    companion object {
        @Volatile var aktiv: FilterTjeneste? = null

        fun komponent(context: Context) = ComponentName(context, FilterTjeneste::class.java).flattenToString()

        fun slaPa(context: Context): Boolean = runCatching {
            val cr = context.contentResolver
            val na = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            val komp = komponent(context)
            if (!na.split(':').contains(komp)) {
                Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, listOf(na, komp).filter { it.isNotEmpty() }.joinToString(":"))
            }
            Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            true
        }.getOrDefault(false)
    }

    override fun onServiceConnected() {
        aktiv = this
        oppdater()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        fjern()
        aktiv = null
        super.onDestroy()
    }

    private fun fjern() {
        lag?.let { runCatching { getSystemService(WindowManager::class.java).removeView(it) } }
        lag = null
    }

    fun oppdater() {
        if (!Rodfilter.pa(this) || Rodfilter.styrke(this) == 0) {
            fjern()
            return
        }
        val vinduer = getSystemService(WindowManager::class.java)
        val (b, h) = if (Build.VERSION.SDK_INT >= 30) {
            vinduer.maximumWindowMetrics.bounds.let { it.width() to it.height() }
        } else {
            resources.displayMetrics.let { it.widthPixels to it.heightPixels * 2 }
        }
        val side = maxOf(b, h)
        val p = WindowManager.LayoutParams(
            side, side,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            alpha = Rodfilter.styrke(this@FilterTjeneste) / 100f
            if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            if (Build.VERSION.SDK_INT >= 30) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                setFitInsetsTypes(0)
            }
        }
        val eksisterende = lag
        if (eksisterende != null) {
            vinduer.updateViewLayout(eksisterende, p)
        } else {
            val v = View(this).apply { setBackgroundColor(Color.RED) }
            vinduer.addView(v, p)
            lag = v
        }
    }
}
