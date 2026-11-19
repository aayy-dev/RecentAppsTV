package com.websnake.recentstv.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** magisk-root: the pins row above the recents row, hold-OK-only menu, the ⚙ at the top, quick switch, ↓ then OK. */
class TwoRowTest {

    private val a = TaskRef(11, "app.a")
    private val b = TaskRef(12, "app.b")
    private val c = TaskRef(13, "app.c")

    private fun ready(vararg t: TaskRef, pins: List<String> = emptyList(), killAll: Boolean = true, current: String? = null) =
        Overlay.loaded(OvState(), t.toList(), pins, killAll, current)
    private fun press(s: OvState, b: Btn, repeat: Int = 0) = Overlay.key(s, KeyIn(b, true, repeat))
    private fun release(s: OvState, b: Btn) = Overlay.key(s, KeyIn(b, false))
    private fun tap(s: OvState, b: Btn): Pair<OvState, List<Effect>> {
        val (s1, e1) = press(s, b); val (s2, e2) = release(s1, b); return s2 to (e1 + e2)
    }
    private fun go(s: OvState, vararg bs: Btn) = bs.fold(s) { st, x -> tap(st, x).first }
    private fun hold(s: OvState) = Overlay.holdElapsed(press(s, Btn.OK).first)
    /** Hold OK, then let go: the menu is open and the next OK runs its focused row. */
    private fun menu(s: OvState) = release(hold(s), Btn.OK).first

    // ── layout ──
    @Test fun `R-01 recents row keeps every recent app, a running pin too, the pins row has the pins`() {
        val s = ready(a, b, pins = listOf("app.b", "p.x"))
        assertEquals(listOf("#killall", "app.a", "app.b"), s.items.map { it.key })
        assertEquals(listOf(Item.Pin("app.b", 12), Item.Pin("p.x", null)), s.pins)
        assertEquals(RowId.TASKS, s.row); assertEquals(Item.Task(a), s.focusedItem)
        assertTrue(s.pins[0].running); assertFalse(s.pins[1].running)
    }

    @Test fun `R-02 no Kill All - the newest app is first, no gear in the row`() {
        val s = ready(a, b, killAll = false)
        assertEquals(listOf("app.a", "app.b"), s.items.map { it.key }); assertEquals(0, s.focus)
        assertFalse(s.items.contains(Item.Settings))
    }

    @Test fun `R-03 nothing at all is Empty, only pins puts focus on the pins row with the placeholder`() {
        assertEquals(Scene.EMPTY, ready().scene); assertNull(ready().focusedItem)
        val p = ready(pins = listOf("p.x", "p.y"))
        assertEquals(Scene.OVERLAY, p.scene); assertTrue(p.emptySlot); assertFalse(p.hasKillAll)
        assertEquals(RowId.PINS, p.row); assertEquals(Item.Pin("p.x", null), p.focusedItem)
        // DOWN has nowhere to go
        assertEquals(RowId.PINS, go(p, Btn.DOWN).row)
    }

    // ── quick switch ──
    @Test fun `R-04 opened over an app the focus is on the app before it, else the newest`() {
        assertEquals(Item.Task(b), ready(a, b, current = "app.a").focusedItem)
        assertEquals(Item.Task(a), ready(a, b, current = null).focusedItem)
        assertEquals(Item.Task(a), ready(a, current = "app.a").focusedItem)          // nothing before it
        assertEquals(Item.Task(a), ready(a, b, current = "app.z").focusedItem)       // current isn't the newest
        val (_, e) = tap(ready(a, b, current = "app.a"), Btn.OK)
        assertEquals(listOf(Effect.ArmHold, Effect.Switch("app.b", 12)), e)
    }

    @Test fun `R-05 OK on the app under the overlay just closes it`() {
        val s = go(ready(a, b, current = "app.a"), Btn.RIGHT)
        assertEquals(Item.Task(a), s.focusedItem)
        assertEquals(listOf(Effect.ArmHold, Effect.Close), tap(s, Btn.OK).second)
    }

    // ── moving between rows ──
    @Test fun `R-06 UP goes to the pins row, UP again to the gear, DOWN comes back row by row`() {
        val s = ready(a, b, pins = listOf("p.x"))
        val pins = go(s, Btn.UP)
        assertEquals(RowId.PINS, pins.row); assertEquals(Item.Pin("p.x", null), pins.focusedItem); assertTrue(pins.focusIn(RowId.PINS))
        val gear = go(pins, Btn.UP)
        assertTrue(gear.gearFocused); assertEquals(Item.Settings, gear.focusedItem); assertFalse(gear.focusIn(RowId.PINS))
        assertEquals(gear, go(gear, Btn.UP)); assertEquals(gear, go(gear, Btn.LEFT))
        assertEquals(RowId.PINS, go(gear, Btn.DOWN).row); assertFalse(go(gear, Btn.DOWN).gear)
        val back = go(gear, Btn.DOWN, Btn.DOWN)
        assertEquals(RowId.TASKS, back.row); assertEquals(Item.Task(a), back.focusedItem)
    }

    @Test fun `R-07 without pins UP from the recents row goes straight to the gear, OK there opens Settings`() {
        val g = go(ready(a), Btn.UP)
        assertTrue(g.gearFocused)
        assertEquals(listOf(Effect.OpenSettings), tap(g, Btn.OK).second)
        assertNull(Overlay.holdElapsed(press(g, Btn.OK).first).ctxPkg)                     // no menu on the gear
    }

    @Test fun `R-08 LEFT RIGHT move in their own row (RTL) and clamp, each row keeps its place`() {
        val s = ready(a, b, c, pins = listOf("p.x", "p.y"))
        assertEquals(Item.Task(b), go(s, Btn.LEFT).focusedItem)
        assertEquals(Item.KillAll, go(s, Btn.RIGHT).focusedItem)
        assertEquals(Item.KillAll, go(s, Btn.RIGHT, Btn.RIGHT).focusedItem)
        assertEquals(Item.Task(c), go(s, Btn.LEFT, Btn.LEFT, Btn.LEFT, Btn.LEFT).focusedItem)
        val p = go(s, Btn.LEFT, Btn.UP, Btn.LEFT)
        assertEquals(Item.Pin("p.y", null), p.focusedItem)
        assertEquals(Item.Pin("p.y", null), go(p, Btn.LEFT).focusedItem)
        assertEquals(Item.Pin("p.x", null), go(p, Btn.RIGHT, Btn.RIGHT).focusedItem)
        assertEquals(Item.Task(b), go(p, Btn.DOWN).focusedItem)                             // recents row kept its focus
    }

    // ── menu only on hold ──
    @Test fun `R-09 UP never opens the menu, holding OK does - on a recent app and on a pin`() {
        val s = ready(a, pins = listOf("p.x"))
        assertNull(go(s, Btn.UP).ctxPkg)
        val m = hold(s)
        assertEquals("app.a", m.ctxPkg); assertEquals(RowId.TASKS, m.ctxRow); assertEquals(Item.Task(a), m.ctxItem)
        val pm = hold(go(s, Btn.UP))
        assertEquals("p.x", pm.ctxPkg); assertEquals(RowId.PINS, pm.ctxRow); assertEquals(Item.Pin("p.x", null), pm.ctxItem)
        // the release that follows does not open the app
        assertTrue(release(m, Btn.OK).second.isEmpty())
        assertNull(hold(go(s, Btn.RIGHT)).ctxPkg)                                          // not on Kill all
    }

    @Test fun `R-10 menu Pin on a recent app adds it to the pins row, focus stays on the app`() {
        val m = menu(ready(a, b))
        val (s, e) = tap(m, Btn.OK)
        assertEquals(listOf(Effect.SetPin("app.a", true)), e)
        assertEquals(listOf("app.a"), s.pinned); assertEquals(Item.Task(a), s.focusedItem); assertEquals(RowId.TASKS, s.row)
        assertEquals(listOf("#killall", "app.a", "app.b"), s.items.map { it.key })        // still a recent app too
    }

    @Test fun `R-11 menu Unpin on the pins row keeps focus there, the last unpin falls back to the recents row`() {
        val s = ready(a, pins = listOf("p.x", "p.y"))
        val (u, e) = tap(menu(go(s, Btn.UP)), Btn.OK)
        assertEquals(listOf(Effect.SetPin("p.x", false)), e)
        assertEquals(RowId.PINS, u.row); assertEquals(Item.Pin("p.y", null), u.focusedItem)
        val last = tap(menu(u), Btn.OK).first
        assertEquals(RowId.TASKS, last.row); assertTrue(last.pins.isEmpty())
        // only pins, the last one unpinned: Empty
        assertEquals(Scene.EMPTY, tap(menu(ready(pins = listOf("p.x"))), Btn.OK).first.scene)
    }

    @Test fun `R-12 menu Close on a running pin removes its task and keeps the pin, on an idle pin nothing`() {
        val s = go(ready(a, b, pins = listOf("app.b", "p.x")), Btn.UP)
        val m = hold(s).copy(ctxSel = CtxOption.CLOSE.ordinal, longPress = false)
        val (r, e) = tap(m, Btn.OK)
        assertEquals(listOf(Effect.Dismiss(b)), e); assertEquals(listOf("app.b", "p.x"), r.pinned)
        assertEquals(listOf(a), r.all)
        val idle = hold(go(s, Btn.LEFT)).copy(ctxSel = CtxOption.CLOSE.ordinal, longPress = false)
        assertTrue(tap(idle, Btn.OK).second.isEmpty())
    }

    @Test fun `R-13 menu Close and Add to exceptions fly the card off in its own row`() {
        val m = hold(ready(a, b)).copy(ctxSel = CtxOption.CLOSE.ordinal, longPress = false)
        val f = tap(m, Btn.OK).first
        assertEquals("app.a", f.flying); assertEquals(RowId.TASKS, f.flyRow); assertEquals(FlyReason.DISMISS, f.flyReason)
        val (done, e) = Overlay.flyComplete(f, "app.a")
        assertEquals(listOf(Effect.Dismiss(a)), e); assertEquals(listOf(b), done.all)

        val p = hold(go(ready(a, pins = listOf("app.a", "p.x")), Btn.UP)).copy(ctxSel = CtxOption.EXCEPTION.ordinal, longPress = false)
        val (x, xe) = tap(p, Btn.OK)
        assertEquals(listOf(Effect.AddException("app.a")), xe); assertEquals(RowId.PINS, x.flyRow); assertEquals(FlyReason.EXCEPTION, x.flyReason)
        val gone = Overlay.flyComplete(x, "app.a").first
        assertEquals(listOf("p.x"), gone.pinned); assertTrue(gone.all.isEmpty())
    }

    // ── removing: ↓ ↓ or ↓ then OK (TV Recents) ──
    @Test fun `R-14 DOWN marks, DOWN again or OK sends the app off, UP or Back keeps it`() {
        val s = ready(a, b)
        val marked = go(s, Btn.DOWN)
        assertEquals(PEEK_DOWN, marked.peek)
        assertEquals("app.a", go(marked, Btn.DOWN).flying)
        val (ok, e) = tap(marked, Btn.OK)
        assertEquals("app.a", ok.flying); assertEquals(PEEK_DOWN, ok.flyDir)
        assertFalse(e.contains(Effect.ArmHold))                                            // no hold while marked
        assertEquals(PEEK_NONE, go(marked, Btn.UP).peek); assertEquals(RowId.TASKS, go(marked, Btn.UP).row)
        val back = press(marked, Btn.BACK)
        assertEquals(PEEK_NONE, back.first.peek); assertTrue(back.second.isEmpty()); assertFalse(back.first.closing)
        assertEquals(PEEK_NONE, go(marked, Btn.LEFT).peek)
    }

    @Test fun `R-15 DOWN does nothing on Kill all, on a pin, or with the gear (back to the row)`() {
        val s = ready(a, pins = listOf("p.x"))
        assertEquals(PEEK_NONE, go(s, Btn.RIGHT, Btn.DOWN).peek)
        assertEquals(RowId.TASKS, go(s, Btn.UP, Btn.DOWN).row)
        assertEquals(PEEK_NONE, go(s, Btn.UP, Btn.DOWN).peek)
    }

    @Test fun `R-16 removing the last recent app moves the focus up to the pins`() {
        val f = go(ready(a, pins = listOf("p.x")), Btn.DOWN, Btn.DOWN)
        val done = Overlay.flyComplete(f, "app.a").first
        assertEquals(RowId.PINS, done.row); assertTrue(done.emptySlot)
    }

    @Test fun `R-17 Kill all clears every recent app (running pins too), the pins stay, focus to the pins`() {
        val s = go(ready(a, b, pins = listOf("app.b", "p.x")), Btn.RIGHT)
        val (k, e) = tap(s, Btn.OK)
        assertEquals(listOf(Effect.KillAll(listOf(a, b))), e); assertTrue(k.killing)
        val done = Overlay.killAllDone(k)
        assertTrue(done.all.isEmpty()); assertEquals(listOf("app.b", "p.x"), done.pinned); assertEquals(RowId.PINS, done.row)
        assertFalse(done.pins.any { it.running })
    }

    @Test fun `R-18 a pin opens its running task, or launches the app`() {
        val s = go(ready(a, pins = listOf("app.a", "p.x")), Btn.UP)
        val (r, e) = tap(s, Btn.OK)
        assertEquals(listOf(Effect.ArmHold, Effect.Switch("app.a", 11)), e); assertEquals(RowId.PINS, r.selectRow)
        assertEquals(Effect.Switch("p.x", null), tap(go(s, Btn.LEFT), Btn.OK).second.last())
    }

    @Test fun `R-19 a reload resets the rows, the gear and the menu`() {
        val dirty = go(ready(a, pins = listOf("p.x")), Btn.UP, Btn.UP).copy(ctxPkg = "app.a", ctxRow = RowId.PINS)
        val s = Overlay.loaded(dirty, listOf(a), listOf("p.x"), true)
        assertFalse(s.gear); assertEquals(RowId.TASKS, s.row); assertNull(s.ctxPkg)
    }

    @Test fun `R-20 a pin that is gone keeps the pins focus in range`() {
        val s = go(ready(a, pins = listOf("p.x", "p.y")), Btn.UP, Btn.LEFT)
        assertEquals(1, s.pinFocus)
        val m = hold(s).copy(ctxSel = CtxOption.EXCEPTION.ordinal, longPress = false)
        val gone = Overlay.flyComplete(tap(m, Btn.OK).first, "p.y").first
        assertEquals(0, gone.pinFocus); assertEquals(Item.Pin("p.x", null), gone.focusedItem)
    }
}
