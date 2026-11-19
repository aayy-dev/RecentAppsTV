package com.websnake.recentstv.logic

/* The overlay's focus engine and key handling as a pure state machine:
   (state, input) → (new state, effects). The Activity runs the effects
   (task calls, intents, saving, closing); the composables only draw the state.

   Two rows (magisk-root): the pinned apps on their own row above, and the recents row below it
   (Kill all, then every recent app, pinned or not, newest first). Each row is right-to-left like
   the design: index 0 is the rightmost place. The ⚙ Settings button sits above both, at the top. */

enum class Scene { LOADING, OVERLAY, EMPTY, SHIZUKU }

enum class Btn { LEFT, RIGHT, UP, DOWN, OK, BACK, MENU, OTHER }

/** One remote key event. [repeat] > 0 while a key is held: a real remote repeats every ~50 ms. */
data class KeyIn(val btn: Btn, val down: Boolean, val repeat: Int = 0)

const val PEEK_NONE = 0
const val PEEK_UP = -1
const val PEEK_DOWN = 1

/** The two rows of cards. */
enum class RowId { TASKS, PINS }

/** One focusable thing. */
sealed interface Item {
    val key: String
    val taskId: Int?
    data object KillAll : Item { override val key = "#killall"; override val taskId: Int? = null }
    /** The ⚙ Settings button above the rows (↑ from the top row). */
    data object Settings : Item { override val key = "#settings"; override val taskId: Int? = null }
    data class Task(val ref: TaskRef) : Item { override val key get() = ref.pkg; override val taskId: Int? get() = ref.taskId }
    /** A pinned app on the pins row; [taskId] is its running task, null when it isn't running. */
    data class Pin(val pkg: String, override val taskId: Int?) : Item {
        override val key get() = pkg
        val running get() = taskId != null
    }
}

/** Hold-OK menu rows, from the card up (drawn bottom-up — PIN sits nearest the card and is focused first). */
enum class CtxOption { PIN, EXCEPTION, APP_INFO, CLOSE }

enum class FlyReason { DISMISS, EXCEPTION }

data class OvState(
    val scene: Scene = Scene.LOADING,
    val all: List<TaskRef> = emptyList(),     // recent apps shown (exceptions already removed), newest first; pins included
    val pinned: List<String> = emptyList(),   // pinned apps shown, in pin order
    val showKillAll: Boolean = true,
    val row: RowId = RowId.TASKS,             // the row holding the focus
    val focus: Int = 0,                       // index into [items] (the recents row): 0 = rightmost
    val pinFocus: Int = 0,                    // index into [pins]: 0 = rightmost
    val peek: Int = PEEK_NONE,                // stage-1 remove on the focused recent app (direction)
    val flying: String? = null,               // package whose card is flying off
    val flyRow: RowId = RowId.TASKS,
    val flyDir: Int = PEEK_UP,
    val flyReason: FlyReason = FlyReason.DISMISS,
    val killing: Boolean = false,             // kill-all animation running
    val ctxPkg: String? = null,               // hold-OK menu target
    val ctxRow: RowId = RowId.TASKS,
    val ctxSel: Int = 0,
    val longPress: Boolean = false,           // this OK press already did its job (opened the menu / ran a menu row)
    val pressing: Boolean = false,            // OK is down on a card (press-in feedback)
    val gear: Boolean = false,                // focus is on the ⚙ Settings button
    val busy: Boolean = false,                // switching / clearing: ignore input
    val selecting: String? = null,            // package chosen with OK: the select zoom is playing
    val selectRow: RowId = RowId.TASKS,
    val shzFocus: Int = 0,                    // gate: 0 = open & grant, 1 = retry
    val closing: Boolean = false,             // exit animation running
    val current: String? = null,              // the app under the overlay (null = the launcher / unknown)
) {
    /** Every recent app, newest first (a pinned app that is running is here too). */
    val tasks: List<TaskRef> get() = all

    val pins: List<Item.Pin> by lazy { pinned.map { p -> Item.Pin(p, all.firstOrNull { it.pkg == p }?.taskId) } }

    val hasKillAll get() = showKillAll && tasks.isNotEmpty()

    /** The recents row, right → left: [Kill all] [newest … oldest]. */
    val items: List<Item> by lazy {
        buildList {
            if (hasKillAll) add(Item.KillAll)
            tasks.forEach { add(Item.Task(it)) }
        }
    }

    /** No recent apps but pins: a placeholder takes the recents row's place (not focusable). */
    val emptySlot get() = tasks.isEmpty() && pins.isNotEmpty()

    val itemCount get() = items.size
    fun itemAt(i: Int): Item? = items.getOrNull(i)
    val gearFocused get() = gear && scene == Scene.OVERLAY
    val focusedItem: Item?
        get() = when {
            scene != Scene.OVERLAY -> null
            gear -> Item.Settings
            row == RowId.PINS -> pins.getOrNull(pinFocus)
            else -> itemAt(focus)
        }
    fun indexOf(key: String) = items.indexOfFirst { it.key == key }
    fun pinIndexOf(pkg: String) = pins.indexOfFirst { it.pkg == pkg }
    val ctxItem: Item?
        get() = ctxPkg?.let { k -> if (ctxRow == RowId.PINS) pins.firstOrNull { it.pkg == k } else items.firstOrNull { it.key == k } }
    /** Is [row] the one with the focus (and not the ⚙)? */
    fun focusIn(row: RowId) = scene == Scene.OVERLAY && !gear && this.row == row
}

sealed interface Effect {
    /** Open the app: bring [taskId] to front, or launch [pkg] when it isn't running. */
    data class Switch(val pkg: String, val taskId: Int?) : Effect
    data class Dismiss(val task: TaskRef) : Effect
    data class KillAll(val tasks: List<TaskRef>) : Effect
    data class AddException(val pkg: String) : Effect
    data class SetPin(val pkg: String, val pinned: Boolean) : Effect
    data class AppInfo(val pkg: String) : Effect
    data object OpenSettings : Effect
    /** OK went down on a card: call [Overlay.holdElapsed] after [Overlay.HOLD_MS] unless it is released first. */
    data object ArmHold : Effect
    data object Close : Effect
    data object GateAction : Effect          // open Shizuku or ask for permission, then reload
}

object Overlay {

    /** Holding OK this long on a card opens its menu (design: ≥ 550 ms). Timed, not counted in key repeats. */
    const val HOLD_MS = 550L

    /**
     * Where the focus lands on open. Over an app (`current`), the app before it — so the trigger then OK switches back,
     * as TV Recents does; otherwise the newest app. With no recent apps, the first pin.
     */
    fun defaultFocus(s: OvState): Int {
        val first = s.items.indexOfFirst { it is Item.Task }
        if (first < 0) return 0
        val next = s.itemAt(first + 1)
        return if (s.current != null && s.itemAt(first)?.key == s.current && next is Item.Task) first + 1 else first
    }

    /** A fresh list arrived: route the scene and clear every transient guard. */
    fun loaded(s: OvState, all: List<TaskRef>, pinned: List<String>, showKillAll: Boolean, current: String? = null): OvState =
        settle(OvState(scene = Scene.OVERLAY, all = all, pinned = pinned, showKillAll = showKillAll, current = current), null, fresh = true)

    fun needShizuku(s: OvState) = OvState(scene = Scene.SHIZUKU, showKillAll = s.showKillAll)

    /**
     * The overlay was opened again while on screen — the same trigger pressed a second time. That closes it, as Back
     * would; nothing while it is already closing or opening an app.
     */
    fun relaunched(s: OvState): Pair<OvState, List<Effect>> =
        if (s.closing || s.selecting != null) s to emptyList()
        else s.copy(closing = true, pressing = false, ctxPkg = null) to listOf(Effect.Close)

    /** Route to Empty when nothing is left; otherwise keep focus on [follow] (in [s].row) if it is still there. */
    private fun settle(s: OvState, follow: String?, at: Int = -1, fresh: Boolean = false): OvState {
        if (s.tasks.isEmpty() && s.pins.isEmpty()) return s.copy(scene = Scene.EMPTY, focus = 0, pinFocus = 0, peek = PEEK_NONE, gear = false)
        val n = s.copy(scene = Scene.OVERLAY)
        val pinFocus = (follow?.takeIf { s.row == RowId.PINS }?.let { n.pinIndexOf(it) }?.takeIf { it >= 0 }
            ?: n.pinFocus).coerceIn(0, (n.pins.size - 1).coerceAtLeast(0))
        val i = follow?.takeIf { s.row == RowId.TASKS }?.let { n.indexOf(it) } ?: -1
        val focus = when {
            fresh -> defaultFocus(n)
            i >= 0 -> i
            at >= 0 -> at.coerceIn(0, (n.itemCount - 1).coerceAtLeast(0))
            else -> defaultFocus(n)
        }
        // a row that has nothing left hands the focus to the other one
        val row = when {
            fresh -> if (n.tasks.isEmpty()) RowId.PINS else RowId.TASKS
            n.row == RowId.PINS && n.pins.isEmpty() -> RowId.TASKS
            n.row == RowId.TASKS && n.tasks.isEmpty() -> RowId.PINS
            else -> n.row
        }
        return n.copy(focus = focus, pinFocus = pinFocus, row = row, gear = n.gear && !fresh)
    }

    fun key(s: OvState, k: KeyIn): Pair<OvState, List<Effect>> {
        if (s.closing || s.selecting != null) return s to emptyList()
        if (k.down) when (k.btn) {
            Btn.BACK -> return when {
                s.ctxPkg != null -> s.copy(ctxPkg = null) to emptyList()
                s.peek != PEEK_NONE -> s.copy(peek = PEEK_NONE) to emptyList()       // Back cancels a pending remove first
                else -> s.copy(closing = true, pressing = false) to listOf(Effect.Close)
            }
            Btn.MENU -> return s to listOf(Effect.OpenSettings)
            else -> {}
        }
        if (s.ctxPkg != null) {
            // The OK release that follows the long-press lands here, with the menu open:
            // it ends the hold, or the next real OK tap would be eaten.
            if (!k.down) return (if (k.btn == Btn.OK && s.longPress) s.copy(longPress = false) else s) to emptyList()
            // OK still held from the press that opened the menu: its repeats must not run a row
            // (a real remote sends one every ~50 ms — they used to pin the app at once).
            if (k.btn == Btn.OK && (k.repeat > 0 || s.longPress)) return s to emptyList()
            return ctxKey(s, k.btn)
        }

        // Overlay OK: hold = the menu; tap (released) = activate.
        if (s.scene == Scene.OVERLAY && k.btn == Btn.OK) {
            // ignored while changing — but a release still ends a hold, or it would eat the next tap
            if (s.busy || s.killing || s.flying != null) return s.copy(pressing = false, longPress = s.longPress && k.down) to emptyList()
            val item = s.focusedItem
            val card = item is Item.Task || item is Item.Pin
            if (k.down) {
                // repeats of a held OK do nothing: the hold is timed (ArmHold → holdElapsed)
                if (k.repeat > 0) return s to emptyList()
                // a card marked for removal: OK confirms it (released), no menu
                val arm = card && s.peek == PEEK_NONE
                return s.copy(pressing = arm) to (if (arm) listOf(Effect.ArmHold) else emptyList())
            }
            val up = s.copy(pressing = false)
            return if (s.longPress) up.copy(longPress = false) to emptyList() else activate(up)
        }
        if (!k.down) return s to emptyList()
        return when (s.scene) {
            Scene.LOADING -> s to emptyList()
            Scene.SHIZUKU -> gateKey(s, k.btn)
            Scene.EMPTY -> if (k.btn == Btn.OK || k.btn == Btn.UP) s to listOf(Effect.OpenSettings) else s to emptyList()
            Scene.OVERLAY -> overlayKey(s, k.btn)
        }
    }

    /** The hold timer fired: OK is still down on the same card → open its menu above it. */
    fun holdElapsed(s: OvState): OvState {
        if (!s.pressing || s.ctxPkg != null || s.closing || s.selecting != null) return s
        val item = s.focusedItem
        if (item !is Item.Task && item !is Item.Pin) return s
        return s.copy(longPress = true, pressing = false, ctxSel = 0, ctxPkg = item.key, ctxRow = s.row, peek = PEEK_NONE)
    }

    private fun overlayKey(s0: OvState, b: Btn): Pair<OvState, List<Effect>> {
        // another key while OK is held: the hold no longer counts (it would open the menu on the next card)
        val s = s0.copy(pressing = false)
        // busy/killing: switching or clearing. flying: the removed card is still on its way out —
        // a key now would act on the app being removed.
        if (s.busy || s.killing || s.flying != null) return s to emptyList()
        if (s.gear) return when (b) {
            // ↓ from the ⚙ goes back to the row below it
            Btn.DOWN -> s.copy(gear = false) to emptyList()
            else -> s to emptyList()
        }
        return when (s.row) {
            RowId.TASKS -> when (b) {
                Btn.RIGHT -> s.copy(peek = PEEK_NONE, focus = (s.focus - 1).coerceAtLeast(0)) to emptyList()     // RTL: toward Kill all
                Btn.LEFT -> s.copy(peek = PEEK_NONE, focus = (s.focus + 1).coerceAtMost((s.itemCount - 1).coerceAtLeast(0))) to emptyList()
                // ↑ puts back a card marked for removal; else up to the pins, or the ⚙ without pins
                Btn.UP -> when {
                    s.peek != PEEK_NONE -> s.copy(peek = PEEK_NONE)
                    s.pins.isNotEmpty() -> s.copy(row = RowId.PINS)
                    else -> s.copy(gear = true)
                } to emptyList()
                Btn.DOWN -> dismissStep(s) to emptyList()
                else -> s to emptyList()
            }
            RowId.PINS -> when (b) {
                Btn.RIGHT -> s.copy(pinFocus = (s.pinFocus - 1).coerceAtLeast(0)) to emptyList()
                Btn.LEFT -> s.copy(pinFocus = (s.pinFocus + 1).coerceAtMost((s.pins.size - 1).coerceAtLeast(0))) to emptyList()
                Btn.UP -> s.copy(gear = true) to emptyList()
                Btn.DOWN -> (if (s.itemCount > 0) s.copy(row = RowId.TASKS) else s) to emptyList()
                else -> s to emptyList()
            }
        }
    }

    /** ↓ marks the focused recent app for removal; a second ↓ (or OK) sends it off. Only recent apps can be removed. */
    private fun dismissStep(s: OvState): OvState {
        val item = s.focusedItem as? Item.Task ?: return s
        return if (s.peek == PEEK_DOWN) fly(s, item.key, RowId.TASKS, PEEK_DOWN, FlyReason.DISMISS)
        else s.copy(peek = PEEK_DOWN)
    }

    private fun fly(s: OvState, pkg: String, row: RowId, dir: Int, why: FlyReason) =
        s.copy(peek = PEEK_NONE, flying = pkg, flyRow = row, flyDir = dir, flyReason = why)

    private fun activate(s: OvState): Pair<OvState, List<Effect>> {
        val item = s.focusedItem ?: return s to emptyList()
        if (item == Item.Settings) return s to listOf(Effect.OpenSettings)
        if (item == Item.KillAll) return s.copy(busy = true, killing = true, peek = PEEK_NONE) to listOf(Effect.KillAll(s.tasks))
        // marked for removal: OK confirms it (TV Recents' ↓ then OK)
        if (s.peek == PEEK_DOWN && item is Item.Task) return fly(s, item.key, RowId.TASKS, PEEK_DOWN, FlyReason.DISMISS) to emptyList()
        // the app already under the overlay — nothing to open: the overlay just goes, no zoom, no switch
        if (item.key == s.current) return s.copy(closing = true, peek = PEEK_NONE) to listOf(Effect.Close)
        // a recent app, or a pin (its running task, or a launch when it isn't running)
        return s.copy(busy = true, selecting = item.key, selectRow = s.row, peek = PEEK_NONE) to listOf(Effect.Switch(item.key, item.taskId))
    }

    /** The fly-off of [pkg] finished: drop it (and remove the task for real when dismissed). */
    fun flyComplete(s: OvState, pkg: String): Pair<OvState, List<Effect>> {
        if (s.flying != pkg) return s to emptyList()
        val base = s.copy(flying = null, peek = PEEK_NONE)
        return when (s.flyReason) {
            FlyReason.DISMISS -> {
                val task = s.all.firstOrNull { it.pkg == pkg } ?: return settle(base, null, s.focus) to emptyList()
                settle(base.copy(all = s.all - task), null, s.focus) to listOf(Effect.Dismiss(task))
            }
            FlyReason.EXCEPTION ->
                settle(base.copy(all = s.all.filter { it.pkg != pkg }, pinned = s.pinned - pkg), null, s.focus) to emptyList()
        }
    }

    /** The kill-all animation finished: the recent apps are gone, pins stay (now not running). */
    fun killAllDone(s: OvState): OvState =
        settle(s.copy(all = emptyList(), killing = false, busy = false, peek = PEEK_NONE), null)

    private fun ctxKey(s: OvState, b: Btn): Pair<OvState, List<Effect>> {
        val item = s.ctxItem ?: return s.copy(ctxPkg = null) to emptyList()
        val pkg = item.key
        val last = CtxOption.entries.size - 1
        return when (b) {
            // drawn bottom-up (row 0 = Pin, nearest the card): UP climbs it; DOWN on the bottom row closes it
            Btn.UP -> s.copy(ctxSel = (s.ctxSel + 1).coerceAtMost(last)) to emptyList()
            Btn.DOWN -> (if (s.ctxSel <= 0) s.copy(ctxPkg = null, ctxSel = 0) else s.copy(ctxSel = s.ctxSel - 1)) to emptyList()
            Btn.OK -> {
                // longPress = true: this OK's release must not also open the app now under focus
                val closed = s.copy(ctxPkg = null, longPress = true)
                when (CtxOption.entries[s.ctxSel.coerceIn(0, last)]) {
                    CtxOption.PIN -> {
                        val on = pkg !in s.pinned
                        val pinned = if (on) s.pinned + pkg else s.pinned - pkg
                        // unpinned from the pins row: the focus stays on that row, on the neighbour
                        settle(closed.copy(pinned = pinned), pkg, s.focus) to listOf(Effect.SetPin(pkg, on))
                    }
                    CtxOption.EXCEPTION -> fly(closed, pkg, s.ctxRow, PEEK_UP, FlyReason.EXCEPTION) to listOf(Effect.AddException(pkg))
                    CtxOption.APP_INFO -> closed.copy(closing = true) to listOf(Effect.AppInfo(pkg), Effect.Close)
                    // Close = stop that app, as ↓ ↓ does: a recent app flies off; on the pins row the pin stays, its task goes
                    CtxOption.CLOSE -> if (s.ctxRow == RowId.PINS) {
                        val t = s.all.firstOrNull { it.pkg == pkg }
                        if (t == null) closed to emptyList()
                        else settle(closed.copy(all = s.all - t), pkg, s.focus) to listOf(Effect.Dismiss(t))
                    } else fly(closed, pkg, RowId.TASKS, PEEK_UP, FlyReason.DISMISS) to emptyList()
                }
            }
            else -> s to emptyList()      // LEFT / RIGHT do nothing while the menu is open (design)
        }
    }

    private fun gateKey(s: OvState, b: Btn): Pair<OvState, List<Effect>> = when (b) {
        Btn.LEFT -> s.copy(shzFocus = (s.shzFocus + 1).coerceAtMost(1)) to emptyList()   // RTL: Retry sits left
        Btn.RIGHT -> s.copy(shzFocus = (s.shzFocus - 1).coerceAtLeast(0)) to emptyList()
        Btn.OK -> s to listOf(Effect.GateAction)      // either button advances the flow
        else -> s to emptyList()
    }
}
