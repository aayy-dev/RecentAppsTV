package com.websnake.recentstv.logic

/* What a remote key does to the overlay, decided before any app or the system sees it
   (trigger/TriggerService filters every key through this). Pure: no Android types, so the
   timing rules are tested on the JVM. Key codes are Android's (KeyEvent.KEYCODE_*). */

/** What the service must do after a key or a timer. */
sealed interface TrigOut {
    /** Open the overlay, or close it when it is already on screen. */
    data object Toggle : TrigOut
    /** Do what a plain Home press does (the key itself was held back). */
    data object Home : TrigOut
    /** Do what holding Home does on Google TV: the Dashboard (Android's notification panel). */
    data object Dashboard : TrigOut
    /** Call [TriggerEngine.timeout] after [ms], unless [Disarm] comes first. */
    data class Arm(val ms: Long) : TrigOut
    data object Disarm : TrigOut
}

/**
 * One instance per service. [key] returns whether the key is consumed (the system and apps never see it) and what to
 * do. Home presses are held back as a whole (down, repeats, up) and replayed as [TrigOut.Home] when they turn out to be
 * plain presses: a held key can't be half handed on.
 */
class TriggerEngine(var mode: Trigger = Trigger.HOLD_HOME, var button: Int = 0) {

    private enum class Wait { NONE, HOLD, SECOND }

    private var wait = Wait.NONE
    private var homeDown = false     // a Home press we consumed is in progress
    private var fired = false        // this press already did its job (opened the overlay / the Dashboard)
    private var second = false       // this press is the second of a double press
    private var buttonDown = false

    fun key(code: Int, down: Boolean, repeat: Int): Pair<Boolean, List<TrigOut>> {
        if (code == KEY_RECENTS || code == KEY_APP_SWITCH) {
            // a remote with a Recents key: always ours (TV builds do nothing with it)
            return true to (if (down && repeat == 0) listOf(TrigOut.Toggle) else emptyList())
        }
        if (mode == Trigger.BUTTON && button != 0 && code == button) {
            if (down && repeat == 0 && !buttonDown) { buttonDown = true; return true to listOf(TrigOut.Toggle) }
            if (!down) buttonDown = false
            return true to emptyList()
        }
        if (code != KEY_HOME) return false to emptyList()
        // a Home release whose press we let through (mode changed meanwhile): let it through too
        if (!down && !homeDown) return false to emptyList()
        return when (mode) {
            Trigger.HOLD_HOME -> holdHome(down, repeat)
            Trigger.DOUBLE_HOME -> doubleHome(down, repeat)
            Trigger.BUTTON, Trigger.OFF -> false to emptyList()
        }
    }

    private fun holdHome(down: Boolean, repeat: Int): Pair<Boolean, List<TrigOut>> {
        if (down) {
            if (repeat > 0 || homeDown) return true to emptyList()
            homeDown = true; fired = false; wait = Wait.HOLD
            return true to listOf(TrigOut.Arm(HOLD_MS))
        }
        homeDown = false
        if (fired) return true to emptyList()
        wait = Wait.NONE
        return true to listOf(TrigOut.Disarm, TrigOut.Home)
    }

    private fun doubleHome(down: Boolean, repeat: Int): Pair<Boolean, List<TrigOut>> {
        if (down) {
            if (repeat > 0 || homeDown) return true to emptyList()
            homeDown = true
            if (wait == Wait.SECOND) {
                wait = Wait.NONE; second = true; fired = true
                return true to listOf(TrigOut.Disarm, TrigOut.Toggle)
            }
            second = false; fired = false; wait = Wait.HOLD
            return true to listOf(TrigOut.Arm(HOLD_MS))
        }
        homeDown = false
        if (second || fired) { second = false; return true to emptyList() }
        wait = Wait.SECOND
        return true to listOf(TrigOut.Disarm, TrigOut.Arm(DOUBLE_MS))
    }

    /** The armed timer ran out. */
    fun timeout(): List<TrigOut> {
        val w = wait
        wait = Wait.NONE
        return when (w) {
            Wait.HOLD -> if (homeDown && !fired) {
                fired = true
                listOf(if (mode == Trigger.HOLD_HOME) TrigOut.Toggle else TrigOut.Dashboard)
            } else emptyList()
            Wait.SECOND -> listOf(TrigOut.Home)        // no second press came: it was a plain Home
            Wait.NONE -> emptyList()
        }
    }

    /** Settings changed: drop any press in progress (a held key is let through from now on). */
    fun reset(mode: Trigger, button: Int) {
        this.mode = mode; this.button = button
        wait = Wait.NONE; homeDown = false; fired = false; second = false; buttonDown = false
    }

    companion object {
        const val KEY_HOME = 3              // KeyEvent.KEYCODE_HOME
        const val KEY_APP_SWITCH = 187      // KeyEvent.KEYCODE_APP_SWITCH
        const val KEY_RECENTS = 312         // KeyEvent.KEYCODE_RECENT_APPS
        /** Android's long-press timeout. */
        const val HOLD_MS = 500L
        /** Android's double-tap timeout (ViewConfiguration), as TV Recents uses. */
        const val DOUBLE_MS = 300L

        /** Keys that can't be the trigger button: navigation, Back, Home, power (TV included), assistant, search and volume. */
        private val RESERVED = setOf(
            19, 20, 21, 22, 23, 66, 160,      // D-pad, centre, Enter, numpad Enter
            4, 111, KEY_HOME,                 // Back, Escape, Home
            26, 223, 224,                     // power, sleep, wake
            24, 25, 164,                      // volume, mute
            82,                               // Menu: opens Settings in the overlay
            62, 84, 96, 97,                   // space, search, gamepad A / B
            177, 179, 181, 276,               // TV / set-top / AVR power, soft sleep
            219, 231,                         // assistant, voice assistant
            KEY_APP_SWITCH, KEY_RECENTS,      // already open Recent apps
        )
        fun learnable(code: Int) = code > 0 && code !in RESERVED
    }
}
