package com.websnake.recentstv.logic

/* The settings changes that are rules rather than plain field writes. The Settings screens themselves are built on
   the framework's focus system (ui/settings), so there is no key handling here any more. */
object SettingsNav {

    fun toggleException(s: Settings, pkg: String): Settings =
        s.copy(exceptions = if (pkg in s.exceptions) s.exceptions - pkg else s.exceptions + pkg)

    /** Pin order = the order the user pinned them in: a new pin goes last. */
    fun togglePin(s: Settings, pkg: String): Settings =
        s.copy(pinned = if (pkg in s.pinned) s.pinned - pkg else s.pinned + pkg)

    fun setPin(s: Settings, pkg: String, on: Boolean): Settings =
        s.copy(pinned = if (on) (s.pinned - pkg) + pkg else s.pinned - pkg)
}
