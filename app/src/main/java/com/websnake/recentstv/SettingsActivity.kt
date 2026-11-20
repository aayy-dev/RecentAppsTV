package com.websnake.recentstv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.websnake.recentstv.data.BackdropImage
import com.websnake.recentstv.data.ImageSources
import com.websnake.recentstv.data.InstalledApp
import com.websnake.recentstv.data.PackageRepository
import com.websnake.recentstv.data.Picker
import com.websnake.recentstv.data.SettingsStore
import com.websnake.recentstv.logic.Settings
import com.websnake.recentstv.logic.SettingsCodec
import com.websnake.recentstv.logic.SettingsNav
import com.websnake.recentstv.shizuku.Privileged
import com.websnake.recentstv.trigger.TriggerService
import com.websnake.recentstv.ui.overlay.Toast
import com.websnake.recentstv.ui.settings.AppListPage
import com.websnake.recentstv.ui.settings.ChoiceId
import com.websnake.recentstv.ui.settings.ChoicePage
import com.websnake.recentstv.ui.settings.ImagesPage
import com.websnake.recentstv.ui.settings.MainPage
import com.websnake.recentstv.ui.settings.Page
import com.websnake.recentstv.ui.settings.keyName
import com.websnake.recentstv.ui.theme.Motion
import com.websnake.recentstv.ui.theme.RecentsTheme
import com.websnake.recentstv.ui.theme.color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings, in the format of Google TV's own settings (ui/settings/TvKit.kt): a two-pane page of rows, each choice on
 * its own page. Focus and D-pad movement are the framework's. The launcher icon opens this; so do the ⚙ button and
 * the Menu key in the overlay.
 */
class SettingsActivity : ComponentActivity() {

    private lateinit var store: SettingsStore
    private lateinit var pkgRepo: PackageRepository

    private val pages = mutableStateListOf<Page>(Page.Main)
    private var mainFocus by mutableStateOf("trigger")          // the main page's row to come back to
    private var settings by mutableStateOf(Settings())
    private var apps by mutableStateOf<List<InstalledApp>>(emptyList())
    private var appsLoading by mutableStateOf(false)
    private var toast by mutableStateOf<String?>(null)
    private var toastJob: Job? = null
    private var pickers by mutableStateOf<List<Picker>>(emptyList())
    private var images by mutableStateOf<List<Uri>>(emptyList())
    private var imagesLoading by mutableStateOf(false)
    private var imageAccess by mutableStateOf(false)
    private var pickerOpenedAt = 0L
    private var learning by mutableStateOf(false)
    private var learnJob: Job? = null

    private val blurSupported by lazy {
        android.os.Build.VERSION.SDK_INT >= 31 && runCatching { windowManager.isCrossWindowBlurEnabled }.getOrDefault(false)
    }

    // Background image: the device's pictures need the read permission (asked once, when the
    // picture page opens); a picker app hands back a content URI.
    private val askRead = registerForActivityResult(ActivityResultContracts.RequestPermission()) { loadImages() }
    private val pickWith = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val uri = r.data?.data
        when {
            uri != null -> onPicked(uri)
            // came straight back with nothing: the app couldn't show a picker (a stub, or no storage access)
            android.os.SystemClock.uptimeMillis() - pickerOpenedAt < 700 -> showToast("That app couldn't open a picker")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SettingsStore(applicationContext)
        pkgRepo = PackageRepository(applicationContext)
        lifecycleScope.launch { store.settings.collect { settings = it } }

        setContent {
            RecentsTheme(accent = settings.accent.color) {
                Box(Modifier.fillMaxSize()) {
                    BackHandler(enabled = pages.size > 1) { back() }
                    AnimatedContent(
                        targetState = pages.last(),
                        transitionSpec = { fadeIn(tween(200, delayMillis = 60)) togetherWith fadeOut(tween(140)) },
                        label = "page",
                    ) { page ->
                        when (page) {
                            Page.Main -> MainPage(
                                settings, triggerOn = TriggerService.isEnabled(this@SettingsActivity), learning = learning,
                                blurSupported = blurSupported, focusKey = mainFocus, onFocus = { mainFocus = it },
                                onSave = ::save, open = ::open, onLearn = ::learnKey,
                            )
                            is Page.Choice -> ChoicePage(page.id, settings, blurSupported) {
                                save(it)
                                // choosing a trigger, even the one already chosen, is asking for the service (system app only)
                                if (page.id == ChoiceId.TRIGGER && Privileged.isSystem) TriggerService.ensureEnabled(applicationContext)
                                back()
                            }
                            Page.Pinned -> AppListPage(
                                title = "Pinned apps",
                                explain = "Pinned apps sit in their own section above the recent apps, whether they are running or not. Turn an app on to pin it.",
                                apps = apps, loading = appsLoading, isOn = { it in settings.pinned }, onLabel = "Pinned. Press OK to unpin.",
                                summary = SettingsCodec.pinnedSummary(settings.pinned.size),
                                actions = listOf("Unpin all" to { save(settings.copy(pinned = emptyList())); showToast("All apps unpinned") }),
                                onToggle = { save(SettingsNav.togglePin(settings, it)) },
                            )
                            Page.Hidden -> AppListPage(
                                title = "Hidden apps",
                                explain = "Apps turned on here never show in Recent apps.",
                                apps = apps, loading = appsLoading, isOn = { it in settings.exceptions }, onLabel = "Hidden from Recent apps. Press OK to show it again.",
                                summary = SettingsCodec.exceptionsSummary(settings.exceptions.count { e -> apps.isEmpty() || apps.any { it.packageName == e } }),
                                actions = listOf(
                                    "Hide all" to { save(settings.copy(exceptions = settings.exceptions + apps.map { it.packageName })); showToast("All apps hidden from Recent apps") },
                                    "Show all" to { save(settings.copy(exceptions = emptySet())); showToast("All apps shown in Recent apps") },
                                ),
                                onToggle = { save(SettingsNav.toggleException(settings, it)) },
                            )
                            Page.Images -> ImagesPage(pickers, images, imagesLoading, imageAccess, onOpenWith = ::openWith, onUse = ::onPicked)
                        }
                    }
                    Toast(toast)
                }
            }
        }
    }

    private fun open(page: Page) {
        when (page) {
            Page.Pinned, Page.Hidden -> loadApps()
            Page.Images -> openImages()
            else -> {}
        }
        pages.add(page)
    }

    private fun back() {
        if (pages.size > 1) pages.removeAt(pages.lastIndex)
    }

    private fun save(n: Settings) {
        val trigger = n.trigger != settings.trigger || n.triggerKey != settings.triggerKey
        settings = n
        lifecycleScope.launch {
            store.save(n)
            // the trigger service is another process: it is told, it doesn't watch the file
            if (trigger) {
                // choosing a trigger is the user asking for the service: turn it on (system app only). Not at every start,
                // so switching it off in Accessibility stays off.
                if (Privileged.isSystem) TriggerService.ensureEnabled(applicationContext)
                TriggerService.sendConfig(applicationContext, n)
            }
        }
    }

    /** The trigger service's answer to [learnKey]: the button the user pressed. */
    private val learned = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val code = i.getIntExtra(TriggerService.EXTRA_KEY, 0)
            if (!learning || code == 0) return
            learnJob?.cancel()
            learning = false
            save(settings.copy(triggerKey = code))
            showToast("${keyName(code)} now opens Recent apps")
        }
    }

    /** Settings → Open with → Button: the trigger service hands over the next remote button (10 s to press it). */
    private fun learnKey() {
        if (!TriggerService.isEnabled(this)) {
            if (Privileged.isSystem) { TriggerService.ensureEnabled(this); showToast("Trigger service turned on. Choose Change again") }
            else showToast("Needs the accessibility service: turn it on in Settings > Accessibility")
            return
        }
        learning = true
        learnJob?.cancel()
        TriggerService.startLearning(this)
        learnJob = lifecycleScope.launch {
            delay(TriggerService.LEARN_MS)
            learning = false
            showToast("No button pressed")
        }
    }

    override fun onStart() {
        super.onStart()
        androidx.core.content.ContextCompat.registerReceiver(
            this, learned, IntentFilter(TriggerService.ACTION_LEARNED), androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStop() {
        super.onStop()
        runCatching { unregisterReceiver(learned) }
        if (learning) { TriggerService.cancelLearning(this); learnJob?.cancel(); learning = false }
    }

    private fun loadApps() {
        if (apps.isNotEmpty() || appsLoading) return
        appsLoading = true
        lifecycleScope.launch {
            apps = withContext(Dispatchers.IO) { pkgRepo.installedApps() }.filter { it.packageName != packageName }
            appsLoading = false
        }
    }

    private fun openImages() {
        imagesLoading = true
        lifecycleScope.launch { pickers = withContext(Dispatchers.IO) { ImageSources.pickers(applicationContext) } }
        if (ImageSources.canRead(this)) loadImages()
        else runCatching { askRead.launch(ImageSources.readPermission()) }.onFailure { loadImages() }
    }

    private fun loadImages() {
        imageAccess = ImageSources.canRead(this)
        lifecycleScope.launch {
            images = if (imageAccess) withContext(Dispatchers.IO) { ImageSources.deviceImages(applicationContext) } else emptyList()
            imagesLoading = false
        }
    }

    private fun openWith(p: Picker) {
        pickerOpenedAt = android.os.SystemClock.uptimeMillis()
        runCatching { pickWith.launch(p.intent) }.onFailure { showToast("Couldn't open ${p.label}") }
    }

    private fun onPicked(uri: Uri?) {
        uri ?: return
        lifecycleScope.launch {
            val name = withContext(Dispatchers.IO) { BackdropImage.import(applicationContext, uri) }
            if (name == null) { showToast("Couldn't read that image"); return@launch }
            save(settings.copy(bgImage = name))
            if (pages.lastOrNull() == Page.Images) back()
            showToast("Background image set")
        }
    }

    private fun showToast(text: String) {
        toast = text
        toastJob?.cancel()
        toastJob = lifecycleScope.launch { delay(Motion.TOAST_MS.toLong()); toast = null }
    }
}
