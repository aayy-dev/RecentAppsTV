package com.websnake.recentstv.trigger

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings as Secure
import android.util.Log
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.websnake.recentstv.RecentsActivity
import com.websnake.recentstv.ShortcutActivity
import com.websnake.recentstv.data.SettingsStore
import com.websnake.recentstv.logic.Settings
import com.websnake.recentstv.logic.SettingsCodec
import com.websnake.recentstv.logic.TrigOut
import com.websnake.recentstv.logic.Trigger
import com.websnake.recentstv.logic.TriggerEngine

/**
 * Opens the overlay from the remote: Hold Home (default), Double Home, a button of the user's choice, or the Recents
 * key (Settings → Open with). An accessibility service with key filtering sees every key before the system and the
 * app in front do, so it can take a key for itself — which a getevent watcher can't (it only listens).
 *
 * It runs in its own process (`:trigger`, see the manifest). Android waits only 500 ms for a key filter's answer and
 * then lets the key through: in the app's main process the overlay's start-up (about a second on the main thread)
 * made the Home release time out, reach the system, and send the user Home right after the overlay opened. Here
 * nothing else ever runs on the main thread. The app talks to it with broadcasts that stay inside the package.
 *
 * The Magisk module turns the service on once (its service.sh); the app does it again when the user picks a trigger
 * in Customize ([ensureEnabled], the app holds WRITE_SECURE_SETTINGS as a system app). Neither runs at every start, so
 * the user can switch the service off in Accessibility. Note it sees every key to decide which are its own; it keeps
 * none of them.
 */
class TriggerService : AccessibilityService() {

    private val engine = TriggerEngine()
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { engine.timeout().forEach(::run) }
    /** Our own Home (performGlobalAction) comes back through this filter as a virtual-device key: let it pass. */
    private var homeSentAt = 0L
    /** Settings → Open with → Button is waiting for a press until this uptime. */
    private var learnUntil = 0L
    /** The key whose press started inside the learn window (taken, its release comes next). */
    private var learnKey = 0

    private val commands = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                ACTION_CONFIG -> configure(
                    Trigger.entries.firstOrNull { it.name == i.getStringExtra(EXTRA_TRIGGER) } ?: Trigger.HOLD_HOME,
                    i.getIntExtra(EXTRA_KEY, 0),
                )
                ACTION_LEARN -> { learnUntil = SystemClock.uptimeMillis() + LEARN_MS; learnKey = 0 }
                ACTION_LEARN_CANCEL -> learnUntil = 0L
            }
        }
    }

    override fun onServiceConnected() {
        // this process only starts with the service: the file is read fresh here; later changes arrive as ACTION_CONFIG
        val prefs = getSharedPreferences(SettingsStore.FILE, Context.MODE_PRIVATE)
        val s = SettingsCodec.decode { k -> prefs.all[k] }
        configure(s.trigger, s.triggerKey)
        ContextCompat.registerReceiver(
            this, commands,
            IntentFilter().apply { addAction(ACTION_CONFIG); addAction(ACTION_LEARN); addAction(ACTION_LEARN_CANCEL) },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(commands) }
        handler.removeCallbacks(timeout)
        super.onDestroy()
    }

    private fun configure(trigger: Trigger, button: Int) {
        handler.removeCallbacks(timeout)
        engine.reset(trigger, button)
        Log.i(TAG, "trigger: $trigger button=$button")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val down = event.action == KeyEvent.ACTION_DOWN
        // waiting for the button to use: the next learnable key (released) is handed to Settings instead of acting
        // only a key pressed inside the window counts, and its release is taken with it: a key held from before the
        // window, or released after it ends, is never half-delivered to the app
        if (learnUntil != 0L && SystemClock.uptimeMillis() > learnUntil) learnUntil = 0L
        if (learnUntil != 0L && TriggerEngine.learnable(event.keyCode)) {
            if (down && event.repeatCount == 0 && learnKey == 0) learnKey = event.keyCode
            if (learnKey == event.keyCode) {
                if (!down) {
                    learnUntil = 0L; learnKey = 0
                    sendBroadcast(Intent(ACTION_LEARNED).setPackage(packageName).putExtra(EXTRA_KEY, event.keyCode))
                }
                return true
            }
        } else if (learnKey != 0 && event.keyCode == learnKey) {   // the window ended with this key still down
            if (!down) learnKey = 0
            return true
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
                        .setAction(ShortcutActivity.ACTION_OPEN)
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
        private const val P = "com.websnake.recentstv.trigger"
        const val ACTION_CONFIG = "$P.CONFIG"
        const val ACTION_LEARN = "$P.LEARN"
        const val ACTION_LEARN_CANCEL = "$P.LEARN_CANCEL"
        /** Sent back to the app: the button the user pressed ([EXTRA_KEY]). */
        const val ACTION_LEARNED = "$P.LEARNED"
        const val EXTRA_TRIGGER = "trigger"
        const val EXTRA_KEY = "key"
        const val LEARN_MS = 10_000L
        const val PROCESS_SUFFIX = ":trigger"

        private fun component(context: Context) = ComponentName(context, TriggerService::class.java).flattenToString()

        private fun enabledList(context: Context) =
            Secure.Secure.getString(context.contentResolver, Secure.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty().split(':')

        /** Is the service turned on in the system's accessibility settings (the module, or the user, did it)? */
        fun isEnabled(context: Context): Boolean =
            runCatching { enabledList(context).any { it.equals(component(context), ignoreCase = true) } }.getOrDefault(false)

        /** The app changed Settings → Open with: tell the service (it lives in another process). */
        fun sendConfig(context: Context, s: Settings) {
            context.sendBroadcast(
                Intent(ACTION_CONFIG).setPackage(context.packageName)
                    .putExtra(EXTRA_TRIGGER, s.trigger.name).putExtra(EXTRA_KEY, s.triggerKey),
            )
        }

        /** Settings → Open with → Button: the next remote button comes back as [ACTION_LEARNED]. */
        fun startLearning(context: Context) { context.sendBroadcast(Intent(ACTION_LEARN).setPackage(context.packageName)) }

        fun cancelLearning(context: Context) { context.sendBroadcast(Intent(ACTION_LEARN_CANCEL).setPackage(context.packageName)) }

        /**
         * As a system app (Magisk module) the app may write secure settings: turn this service on if it isn't. The
         * module's service.sh does the same at boot, for a first boot before the app ever ran.
         */
        fun ensureEnabled(context: Context) {
            val me = component(context)
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
