package com.websnake.recentstv.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The remote trigger (trigger/TriggerService): Hold Home, Double Home, a chosen button, the Recents key. */
class TriggerEngineTest {

    private val HOME = TriggerEngine.KEY_HOME
    private val STAR = 174          // KEYCODE_BOOKMARK
    private fun e(mode: Trigger, button: Int = 0) = TriggerEngine(mode, button)
    private fun TriggerEngine.down(code: Int, repeat: Int = 0) = key(code, true, repeat)
    private fun TriggerEngine.up(code: Int) = key(code, false, 0)
    private val arm = TrigOut.Arm(TriggerEngine.HOLD_MS)

    @Test fun `T-01 Hold Home - a short press is Home, on release`() {
        val t = e(Trigger.HOLD_HOME)
        assertEquals(true to listOf(arm), t.down(HOME))
        assertEquals(true to listOf(TrigOut.Disarm, TrigOut.Home), t.up(HOME))
        assertTrue(t.timeout().isEmpty())                                   // a late timer does nothing
    }

    @Test fun `T-02 Hold Home - held past the timeout opens the overlay, the release and repeats are swallowed`() {
        val t = e(Trigger.HOLD_HOME)
        t.down(HOME)
        assertEquals(true to emptyList<TrigOut>(), t.down(HOME, repeat = 1))
        assertEquals(listOf(TrigOut.Toggle), t.timeout())
        assertEquals(true to emptyList<TrigOut>(), t.down(HOME, repeat = 9))
        assertEquals(true to emptyList<TrigOut>(), t.up(HOME))
        assertTrue(t.timeout().isEmpty())
    }

    @Test fun `T-03 Double Home - two presses open the overlay, one press is Home after the wait`() {
        val t = e(Trigger.DOUBLE_HOME)
        assertEquals(true to listOf(arm), t.down(HOME))
        assertEquals(true to listOf(TrigOut.Disarm, TrigOut.Arm(TriggerEngine.DOUBLE_MS)), t.up(HOME))
        assertEquals(true to listOf(TrigOut.Disarm, TrigOut.Toggle), t.down(HOME))
        assertEquals(true to emptyList<TrigOut>(), t.up(HOME))
        assertTrue(t.timeout().isEmpty())
        // one press
        t.down(HOME); t.up(HOME)
        assertEquals(listOf(TrigOut.Home), t.timeout())
    }

    @Test fun `T-04 Double Home - holding Home still opens the Dashboard`() {
        val t = e(Trigger.DOUBLE_HOME)
        t.down(HOME)
        assertEquals(listOf(TrigOut.Dashboard), t.timeout())
        assertEquals(true to emptyList<TrigOut>(), t.up(HOME))
        assertTrue(t.timeout().isEmpty())
    }

    @Test fun `T-05 Button - the chosen button toggles once per press and is always swallowed, Home is left alone`() {
        val t = e(Trigger.BUTTON, STAR)
        assertEquals(true to listOf(TrigOut.Toggle), t.down(STAR))
        assertEquals(true to emptyList<TrigOut>(), t.down(STAR, repeat = 1))
        assertEquals(true to emptyList<TrigOut>(), t.up(STAR))
        assertEquals(false to emptyList<TrigOut>(), t.down(HOME))
        assertEquals(false to emptyList<TrigOut>(), t.up(HOME))
        assertEquals(false to emptyList<TrigOut>(), e(Trigger.BUTTON, 0).down(0))           // none chosen yet
        assertEquals(false to emptyList<TrigOut>(), e(Trigger.HOLD_HOME, STAR).down(STAR))  // only in Button mode
    }

    @Test fun `T-06 Off - nothing but the Recents key, other keys always pass`() {
        val t = e(Trigger.OFF)
        assertEquals(false to emptyList<TrigOut>(), t.down(HOME))
        assertEquals(true to listOf(TrigOut.Toggle), t.down(TriggerEngine.KEY_RECENTS))
        assertEquals(true to emptyList<TrigOut>(), t.up(TriggerEngine.KEY_RECENTS))
        assertEquals(true to listOf(TrigOut.Toggle), t.down(TriggerEngine.KEY_APP_SWITCH))
        assertEquals(false to emptyList<TrigOut>(), e(Trigger.HOLD_HOME).down(20))           // D-pad down
        assertTrue(t.timeout().isEmpty())
    }

    @Test fun `T-07 a settings change mid-press lets the rest of that press through`() {
        val t = e(Trigger.HOLD_HOME)
        t.down(HOME)
        t.reset(Trigger.OFF, 0)
        assertEquals(false to emptyList<TrigOut>(), t.up(HOME))
        assertTrue(t.timeout().isEmpty())
        // a release whose press was never ours passes in every mode
        assertEquals(false to emptyList<TrigOut>(), e(Trigger.HOLD_HOME).up(HOME))
    }

    @Test fun `T-08 learnable buttons exclude navigation, Back, Home, Menu, power and volume`() {
        assertTrue(TriggerEngine.learnable(STAR))
        assertTrue(TriggerEngine.learnable(172))                                              // KEYCODE_GUIDE
        for (k in listOf(19, 20, 21, 22, 23, 66, 4, 3, 82, 26, 24, 25, 164, 0)) assertFalse("$k", TriggerEngine.learnable(k))
    }
}
