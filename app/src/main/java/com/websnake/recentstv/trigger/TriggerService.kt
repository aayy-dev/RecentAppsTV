package com.websnake.recentstv.trigger

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings as Secure
import android.util.Log
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.websnake.recentstv.RecentsActivity
import com.websnake.recentstv.data.SettingsStore
import com.websnake.recentstv.logic.SettingsCodec
import com.websnake.recentstv.logic.SettingsKeys
import com.websnake.recentstv.logic.TrigOut
import com.websnake.recentstv.logic.TriggerEngine

/**
 * Opens the overlay from the remote: Hold Home (default), Double Home, a button of the user's choice, or the Recents
 * key (Settings → Open with). An accessibility service with key filtering sees every key before the system and the
 * app in front do, so it can take a key for itself — which a getevent watcher can't (it only listens).
 *
 * The Magisk module turns the service on (the app holds WRITE_SECURE_SETTINGS as a system app); [ensureEnabled].
 */
class TriggerService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {

    private val engine = TriggerEngine()
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { engine.timeout().forEach(::run) }
    private var prefs: SharedPreferences? = null
    /** Our own Home (performGlobalAction) comes back through this filter as a virtual-device key: let it pass. */
    private var homeSentAt = 0L

    override fun onServiceConnected() {
        instance = this
        prefs = getSharedPreferences(SettingsStore.FILE, Context.MODE_PRIVATE).also {
            it.registerOnSharedPreferenceChangeListener(this)
        }
        load()
        Log.i(TAG, "trigger service on: ${engine.mode} button=${engine.button}")
    }

    override fun onDestroy() {
        prefs?.unregisterOnSharedPreferenceChangeListener(this)
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onSharedPreferenceChanged(p: SharedPreferences?, key: String?) {
        if (key == SettingsKeys.TRIGGER || key == SettingsKeys.TRIGGER_KEY) load()
    }

    private fun load() {
        val s = SettingsCodec.decode { k -> prefs?.all?.get(k) }
        handler.removeCallbacks(timeout)
        engine.reset(s.trigger, s.triggerKey)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val down = event.action == KeyEvent.ACTION_DOWN
        // Settings → Open with → Button is waiting for a press: that button becomes the trigger
        learn?.let { cb ->
            if (TriggerEngine.learnable(event.keyCode)) {
                if (!down) { learn = null; handler.post { cb(event.keyCode) } }
                return true
            }
        }
        if (event.keyCode == KeyEvent.KEYCODE_HOME && event.deviceId == KeyCharacterMap.VIRTUAL_KEYBOARD &&
            SystemClock.uptimeMillis() - homeSentAt < 1000
        ) return false
        val (consume, outs) = engine.key(event.keyCode, down, event.repeatCount)
        outs.forEach(::run)
        return consume
    }

    private fun run(o: TrigOut) {
        when (o) {
            TrigOut.Toggle -> runCatching {
                startActivity(
                    Intent(this, RecentsActivity::class.java)
                        .setAction(com.websnake.recentstv.ShortcutActivity.ACTION_OPEN)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.onFailure { Log.w(TAG, "open failed: $it") }
            TrigOut.Home -> { homeSentAt = SystemClock.uptimeMillis(); performGlobalAction(GLOBAL_ACTION_HOME) }
            TrigOut.Dashboard -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            is TrigOut.Arm -> { handler.removeCallbacks(timeout); handler.postDelayed(timeout, o.ms) }
            TrigOut.Disarm -> handler.removeCallbacks(timeout)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    companion object {
        private const val TAG = "RTV"

        @Volatile var instance: TriggerService? = null
            private set

        /** Set by Settings: the next learnable key (released) is passed here instead of acting. */
        @Volatile var learn: ((Int) -> Unit)? = null

        val running get() = instance != null

        /**
         * As a system app (Magisk module) the app may write secure settings: turn this service on if it isn't. The
         * module's service.sh does the same at boot, for a first boot before the app ever ran.
         */
        fun ensureEnabled(context: Context) {
            val me = ComponentName(context, TriggerService::class.java).flattenToString()
            runCatching {
                val r = context.contentResolver
                val cur = Secure.Secure.getString(r, Secure.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
                if (cur.split(':').none { it.equals(me, ignoreCase = true) }) {
                    Secure.Secure.putString(r, Secure.Secure.ENABLED_ACCESSIBILITY_SERVICES, if (cur.isBlank()) me else "$cur:$me")
                }
                if (Secure.Secure.getInt(r, Secure.Secure.ACCESSIBILITY_ENABLED, 0) != 1) {
                    Secure.Secure.putInt(r, Secure.Secure.ACCESSIBILITY_ENABLED, 1)
                }
            }.onFailure { Log.w(TAG, "can't enable the trigger service: $it") }
        }
    }
}
