package com.websnake.recentstv

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * The component the Magisk module's framework overlay names as the system's recents
 * (`config_recentsComponentName`), so the app holds the recents-role permissions (REMOVE_TASKS,
 * MANAGE_ACTIVITY_TASKS). Should anything start it, it opens the overlay and steps away.
 */
class SystemRecentsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching {
            startActivity(Intent(this, RecentsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        finish()
    }
}
