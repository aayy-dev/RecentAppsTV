package com.websnake.recentstv.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Area SET — stored settings and the pin / hide toggles (the Settings screens themselves use the framework's focus). */
class SettingsTest {

    @Test fun `P-SET-01 decode of an empty store gives the defaults (design values)`() {
        val s = SettingsCodec.decode { null }
        assertEquals(Settings(), s); assertEquals(66, s.bgDim); assertEquals(CardSize.MEDIUM, s.size)
        assertEquals(PinSize.SMALL, s.pinSize); assertEquals(Backdrop.FADE, s.background)
        assertEquals(Trigger.HOLD_HOME, s.trigger); assertEquals(0, s.triggerKey)
        assertTrue(s.pinned.isEmpty()); assertNull(s.bgImage)
    }

    @Test fun `P-SET-02 encode then decode round-trips every field (v1 key names kept)`() {
        val s = Settings(false, CardSize.LARGE, CardShape.CIRCLE, setOf("x"), Accent.PINK, 40, false,
            listOf("p.b", "p.a"), PinSize.SAME, Backdrop.IMAGE, 3, "wall.jpg", Trigger.BUTTON, 174)
        val m = SettingsCodec.encode(s)
        assertEquals(s, SettingsCodec.decode { m[it] })
        assertTrue(m.keys.containsAll(listOf("show_kill_all", "bg_dim_pct", "force_stop_on_kill", "exceptions", "pinned")))
    }

    @Test fun `P-SET-03 unknown names, wrong types and out-of-range numbers fall back safely`() {
        val m = mapOf<String, Any>("size" to "HUGE", "shape" to 3, "bg_dim_pct" to 250, "show_kill_all" to "yes",
            "bg_color" to 99, "bg_image" to "  ", "pinned" to 5)
        val s = SettingsCodec.decode { m[it] }
        assertEquals(CardSize.MEDIUM, s.size); assertEquals(CardShape.ROUNDED, s.shape)
        assertEquals(100, s.bgDim); assertEquals(true, s.showKillAll); assertEquals(4, s.bgColor)
        assertNull(s.bgImage); assertTrue(s.pinned.isEmpty())
        assertEquals(0, SettingsCodec.decode { if (it == "bg_dim_pct") -5 else null }.bgDim)
    }

    @Test fun `P-SET-04 the pin list keeps its order, drops blanks and repeats`() {
        assertEquals(listOf("b", "a"), SettingsCodec.decode { if (it == "pinned") "b\n\n a \nb\na" else null }.pinned)
        assertEquals("", SettingsCodec.encode(Settings())["bg_image"])
    }

    @Test fun `P-SET-05 summary and count labels`() {
        assertEquals("No apps hidden", SettingsCodec.exceptionsSummary(0))
        assertEquals("1 app hidden", SettingsCodec.exceptionsSummary(1))
        assertEquals("3 apps hidden", SettingsCodec.exceptionsSummary(3))
        assertEquals("No apps pinned", SettingsCodec.pinnedSummary(0))
        assertEquals("1 app pinned", SettingsCodec.pinnedSummary(1))
        assertEquals("2 apps pinned", SettingsCodec.pinnedSummary(2))
        assertEquals("", SettingsCodec.countLabel(0)); assertEquals("1 app", SettingsCodec.countLabel(1))
        assertEquals("7 apps · 3 pinned", SettingsCodec.countLabel(7, 3)); assertEquals("2 pinned", SettingsCodec.countLabel(0, 2))
    }

    @Test fun `P-SET-20 setPin from the overlay adds last or removes`() {
        assertEquals(listOf("b", "a"), SettingsNav.setPin(Settings(pinned = listOf("a", "b")), "a", true).pinned)
        assertEquals(listOf("b"), SettingsNav.setPin(Settings(pinned = listOf("a", "b")), "a", false).pinned)
    }

    @Test fun `P-SET-26 toggling an app hides or shows it, pins it last or unpins it`() {
        val s = Settings(exceptions = setOf("a"), pinned = listOf("p"))
        assertEquals(setOf("a", "b"), SettingsNav.toggleException(s, "b").exceptions)
        assertEquals(emptySet<String>(), SettingsNav.toggleException(s, "a").exceptions)
        assertEquals(listOf("p", "q"), SettingsNav.togglePin(s, "q").pinned)
        assertEquals(emptyList<String>(), SettingsNav.togglePin(s, "p").pinned)
    }
}
