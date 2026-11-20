package com.websnake.recentstv.ui.settings

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Border
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.websnake.recentstv.data.ImageSources
import com.websnake.recentstv.data.InstalledApp
import com.websnake.recentstv.data.Picker
import com.websnake.recentstv.logic.Accent
import com.websnake.recentstv.logic.Backdrop
import com.websnake.recentstv.logic.CardShape
import com.websnake.recentstv.logic.CardSize
import com.websnake.recentstv.logic.PinSize
import com.websnake.recentstv.logic.Settings
import com.websnake.recentstv.logic.SettingsCodec
import com.websnake.recentstv.logic.Trigger
import com.websnake.recentstv.ui.theme.BackdropSwatches
import com.websnake.recentstv.ui.theme.LocalAccent
import com.websnake.recentstv.ui.theme.Tokens
import com.websnake.recentstv.ui.theme.Type
import com.websnake.recentstv.ui.theme.color
import com.websnake.recentstv.ui.theme.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** Where Settings is. */
sealed interface Page {
    data object Main : Page
    data class Choice(val id: ChoiceId) : Page
    data object Pinned : Page
    data object Hidden : Page
    data object Images : Page
}

enum class ChoiceId { TRIGGER, SIZE, SHAPE, ACCENT, PIN_SIZE, BACKGROUND, DIM, BG_COLOR }

/** One option of a choice page: what it is called, what it does to the settings, and its explanation. */
class Option(val title: String, val summary: String? = null, val swatch: Color? = null, val apply: (Settings) -> Settings)

/** Give the focus to whatever carries the returned requester, once it is on screen. */
@Composable
private fun rememberInitialFocus(key: Any? = Unit): FocusRequester {
    val r = remember { FocusRequester() }
    LaunchedEffect(key) {
        repeat(12) {
            withFrameNanos { }
            if (runCatching { r.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }
    return r
}

// ── names shown for each value ───────────────────────────────────────────────
private fun Trigger.label() = when (this) {
    Trigger.HOLD_HOME -> "Hold Home"; Trigger.DOUBLE_HOME -> "Double-press Home"; Trigger.BUTTON -> "A button you choose"; Trigger.OFF -> "Off"
}
private fun CardSize.label() = when (this) { CardSize.SMALL -> "Small"; CardSize.MEDIUM -> "Medium"; CardSize.LARGE -> "Large" }
private fun CardShape.label() = when (this) { CardShape.SQUARE -> "Square"; CardShape.ROUNDED -> "Rounded"; CardShape.CIRCLE -> "Circle" }
private fun Accent.label() = name.lowercase().replaceFirstChar(Char::uppercase)
private fun PinSize.label() = when (this) { PinSize.SMALL -> "Small"; PinSize.SAME -> "Same as the cards" }
private fun Backdrop.label() = when (this) {
    Backdrop.FADE -> "Fade"; Backdrop.DIM -> "Dim"; Backdrop.BLUR -> "Blur"; Backdrop.COLOR -> "Color"; Backdrop.IMAGE -> "Image"
}

/** The title, the options and the option now chosen, for each choice page. */
fun choiceOf(id: ChoiceId, s: Settings, blurSupported: Boolean): Triple<String, List<Option>, Int> = when (id) {
    ChoiceId.TRIGGER -> Triple(
        "Open Recent apps",
        listOf(
            Option("Hold Home", "Hold the Home button. A short press still goes Home.") { it.copy(trigger = Trigger.HOLD_HOME) },
            Option("Double-press Home", "Press Home twice. A single Home waits 0.3 s for a second press; holding it still opens the Dashboard.") { it.copy(trigger = Trigger.DOUBLE_HOME) },
            Option("A button you choose", "Any other remote button. That button then does nothing else. Choose it in the Button row afterwards.") { it.copy(trigger = Trigger.BUTTON) },
            Option("Off", "Only from a shortcut, a button-mapper app or a remote's Recents key.") { it.copy(trigger = Trigger.OFF) },
        ),
        s.trigger.ordinal,
    )
    ChoiceId.SIZE -> Triple("Card size", CardSize.entries.map { v -> Option(v.label(), "Scales every card in the overlay.") { it.copy(size = v) } }, s.size.ordinal)
    ChoiceId.SHAPE -> Triple("Card shape", CardShape.entries.map { v -> Option(v.label(), "The corner style of every card, and of its focus glow.") { it.copy(shape = v) } }, s.shape.ordinal)
    ChoiceId.ACCENT -> Triple(
        "Accent color",
        Accent.entries.map { v -> Option(v.label(), "Focus rings, the Kill all tile and these settings.", swatch = v.color) { it.copy(accent = v) } },
        s.accent.ordinal,
    )
    ChoiceId.PIN_SIZE -> Triple(
        "Pinned icon size",
        PinSize.entries.map { v -> Option(v.label(), "Pinned apps can be smaller than the recent apps below them.") { it.copy(pinSize = v) } },
        s.pinSize.ordinal,
    )
    ChoiceId.BACKGROUND -> Triple(
        "Background",
        listOf(
            Option("Fade", "The app stays visible behind the overlay, darker at the top and bottom.") { it.copy(background = Backdrop.FADE) },
            Option("Dim", "An even dim over the app. Set how strong in Dim level.") { it.copy(background = Backdrop.DIM) },
            Option("Blur", if (blurSupported) "The app behind is blurred." else "Blur isn't available on this device: Dim is used.") { it.copy(background = Backdrop.BLUR) },
            Option("Color", "A solid color hides the app completely.") { it.copy(background = Backdrop.COLOR) },
            Option("Image", "A picture of yours hides the app completely.") { it.copy(background = Backdrop.IMAGE) },
        ),
        s.background.ordinal,
    )
    ChoiceId.DIM -> {
        val levels = (listOf(20, 30, 40, 50, 60, 66, 70, 80, 90, 100) + s.bgDim).distinct().sorted()
        Triple("Dim level", levels.map { v -> Option("$v %", "How much of the running app is hidden.") { it.copy(bgDim = v) } }, levels.indexOf(s.bgDim))
    }
    ChoiceId.BG_COLOR -> Triple(
        "Background color",
        BackdropSwatches.mapIndexed { i, (name, c) -> Option(name, "A solid color behind the overlay.", swatch = c) { it.copy(bgColor = i) } },
        s.bgColor,
    )
}

// ── main page ────────────────────────────────────────────────────────────────
private class Entry(val key: String, val section: String?, val preview: TvPreview, val row: @Composable (Modifier, () -> Unit) -> Unit)

@Composable
fun MainPage(
    s: Settings,
    triggerOn: Boolean,
    learning: Boolean,
    blurSupported: Boolean,
    focusKey: String,
    onFocus: (String) -> Unit,
    onSave: (Settings) -> Unit,
    open: (Page) -> Unit,
    onLearn: () -> Unit,
) {
    val visual: @Composable () -> Unit = { OverlayPreview(s) }
    fun pick(key: String, section: String?, title: String, value: String, id: ChoiceId, text: String, vis: Boolean = true) = Entry(
        key, section, TvPreview(title, text, if (vis) visual else null),
    ) { m, f -> TvRow(title, m, trailing = { TvValue(value) }, opensPage = true, onFocused = f, onClick = { open(Page.Choice(id)) }) }

    val entries = buildList {
        add(pick(
            "trigger", "Open with", "Open Recent apps", s.trigger.label(), ChoiceId.TRIGGER,
            if (!triggerOn) "The remote buttons need the Magisk module (the app as a system app). Shortcuts and button-mapper apps still work."
            else "The remote button that opens Recent apps over whatever is playing. Press it again to close.", vis = false,
        ))
        if (s.trigger == Trigger.BUTTON) add(Entry(
            "button", null,
            TvPreview("Button", "Press OK, then press the remote button you want to use. Navigation, Back, Home, Menu, power and volume can't be chosen."),
        ) { m, f ->
            TvRow(
                "Button", m,
                summary = when { learning -> "Press the button to use now…"; s.triggerKey == 0 -> "None chosen yet"; else -> keyName(s.triggerKey) },
                trailing = { if (learning) Text("Listening…", style = Type.small.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold)) else TvValue("Change", chevron = false) },
                onFocused = f, onClick = onLearn,
            )
        })
        add(Entry("killall", "Overlay", TvPreview("Show Kill all", "A tile at the end of the row that closes every recent app at once. Pinned apps stay pinned.", visual)) { m, f ->
            TvSwitchRow("Show Kill all", s.showKillAll, { onSave(s.copy(showKillAll = it)) }, m, onFocused = f)
        })
        add(Entry("forcestop", null, TvPreview("Force stop on close", "Closing an app also force-stops it, so it can't restart itself in the background. Off: only its recent-apps entry is removed.")) { m, f ->
            TvSwitchRow("Force stop on close", s.forceStopOnKill, { onSave(s.copy(forceStopOnKill = it)) }, m, onFocused = f)
        })
        add(pick("size", null, "Card size", s.size.label(), ChoiceId.SIZE, "Scales every card in the overlay."))
        add(pick("shape", null, "Card shape", s.shape.label(), ChoiceId.SHAPE, "The corner style of every card, and of its focus glow."))
        add(pick("accent", null, "Accent color", s.accent.label(), ChoiceId.ACCENT, "Focus rings, the Kill all tile and these settings."))
        add(Entry("pinned", "Pinned apps", TvPreview("Pinned apps", "Pinned apps sit in their own section above the recent apps, whether they are running or not. You can also pin an app from the overlay: hold OK on it.", visual)) { m, f ->
            TvRow("Pinned apps", m, trailing = { TvValue(SettingsCodec.pinnedSummary(s.pinned.size)) }, opensPage = true, onFocused = f, onClick = { open(Page.Pinned) })
        })
        add(pick("pinsize", null, "Pinned icon size", s.pinSize.label(), ChoiceId.PIN_SIZE, "Pinned apps can be smaller than the recent apps below them."))
        add(pick("background", "Background", "Background", s.background.label(), ChoiceId.BACKGROUND, "What sits behind the overlay while it is open."))
        if (s.background == Backdrop.DIM) add(pick("dim", null, "Dim level", "${s.bgDim} %", ChoiceId.DIM, "How much of the running app is hidden."))
        if (s.background == Backdrop.COLOR) add(pick("bgcolor", null, "Background color", BackdropSwatches[s.bgColor].first, ChoiceId.BG_COLOR, "A solid color behind the overlay."))
        if (s.background == Backdrop.IMAGE) add(Entry("bgimage", null, TvPreview("Background image", "A picture from this device, or one opened with another app. It is copied into the app, so it stays if the original is deleted.")) { m, f ->
            TvRow("Background image", m, trailing = { TvValue(s.bgImage ?: "Choose") }, opensPage = true, onFocused = f, onClick = { open(Page.Images) })
        })
        add(Entry("hidden", "Hidden", TvPreview("Hidden apps", "Apps that never show in Recent apps. Hide one from the overlay too: hold OK on it, then Hide from Recent apps.")) { m, f ->
            TvRow("Hidden apps", m, trailing = { TvValue(SettingsCodec.exceptionsSummary(s.exceptions.size)) }, opensPage = true, onFocused = f, onClick = { open(Page.Hidden) })
        })
    }
    val start = if (entries.any { it.key == focusKey }) focusKey else entries.first().key
    var focused by remember { mutableStateOf(start) }
    val requester = rememberInitialFocus()
    TvSettingsPage(
        title = "Settings", subtitle = "Recent apps",
        preview = (entries.firstOrNull { it.key == focused } ?: entries.first()).preview,
        // back from a choice: the list opens with the row we came from already composed, or it can't take the focus
        firstItem = remember {
            var n = 0; var at = 0
            for (e in entries) { if (e.section != null) n++; if (e.key == start) at = n; n++ }
            (at - 3).coerceAtLeast(0)
        },
    ) {
        entries.forEach { e ->
            if (e.section != null) item(key = "s-${e.section}") { TvSectionHeader(e.section) }
            item(key = e.key) {
                e.row(if (e.key == start) Modifier.focusRequester(requester) else Modifier) { focused = e.key; onFocus(e.key) }
            }
        }
    }
}

// ── a choice ─────────────────────────────────────────────────────────────────
@Composable
fun ChoicePage(id: ChoiceId, s: Settings, blurSupported: Boolean, onPick: (Settings) -> Unit) {
    val (title, options, selected) = choiceOf(id, s, blurSupported)
    var focused by remember { mutableStateOf(selected.coerceIn(0, options.lastIndex)) }
    val requester = rememberInitialFocus()
    val o = options[focused.coerceIn(0, options.lastIndex)]
    val live = id != ChoiceId.TRIGGER
    TvSettingsPage(
        title = title,
        firstItem = (selected - 3).coerceAtLeast(0),   // the chosen row must be composed to take the focus
        // the right pane shows the overlay as the focused option would make it
        preview = TvPreview(o.title, o.summary, if (live) ({ OverlayPreview(o.apply(s)) }) else null),
    ) {
        itemsIndexed(options) { i, opt ->
            TvRadioRow(
                opt.title, selected = i == selected, onClick = { onPick(opt.apply(s)) },
                modifier = if (i == selected.coerceIn(0, options.lastIndex)) Modifier.focusRequester(requester) else Modifier,
                leading = opt.swatch?.let { c -> { TvSwatch(c) } },
                onFocused = { focused = i },
            )
        }
    }
}

// ── app lists: Pinned apps, Hidden apps ──────────────────────────────────────
@Composable
fun AppListPage(
    title: String,
    explain: String,
    apps: List<InstalledApp>,
    loading: Boolean,
    isOn: (String) -> Boolean,
    onLabel: String,
    summary: String,
    actions: List<Pair<String, () -> Unit>>,
    onToggle: (String) -> Unit,
) {
    var focused by remember { mutableStateOf<InstalledApp?>(null) }
    val requester = rememberInitialFocus(apps.isNotEmpty())
    TvSettingsPage(
        title = title, subtitle = summary,
        preview = focused?.let { a -> TvPreview(a.label, "${a.tag} · ${a.packageName}\n\n${if (isOn(a.packageName)) onLabel else "Press OK to change."}") } ?: TvPreview(title, explain),
        header = if (actions.isEmpty()) null else ({
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                actions.forEachIndexed { i, (label, run) ->
                    TvPillButton(label, run, if (apps.isEmpty() && i == 0) Modifier.focusRequester(requester) else Modifier)
                }
            }
        }),
    ) {
        if (loading && apps.isEmpty()) item { Text("Loading apps…", style = Type.body, color = Tokens.textDim, modifier = Modifier.padding(start = 26.dp, top = 12.dp)) }
        itemsIndexed(apps, key = { _, a -> a.packageName }) { i, a ->
            TvSwitchRow(
                a.label, isOn(a.packageName), { onToggle(a.packageName) },
                modifier = if (i == 0) Modifier.focusRequester(requester) else Modifier,
                summary = a.tag, leading = { TvAppIcon(a.icon, a.label) }, onFocused = { focused = a },
            )
        }
    }
}

// ── background image ─────────────────────────────────────────────────────────
@Composable
fun ImagesPage(
    pickers: List<Picker>,
    images: List<Uri>,
    loading: Boolean,
    hasAccess: Boolean,
    onOpenWith: (Picker) -> Unit,
    onUse: (Uri) -> Unit,
) {
    val requester = rememberInitialFocus(images.isNotEmpty() to pickers.isNotEmpty())
    val thumbs = remember { ConcurrentHashMap<Uri, ImageBitmap>() }
    TvSafeColumn("Background image", "Pick a picture below, or open one with an app.") {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (pickers.isNotEmpty()) Row(Modifier.padding(start = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pickers.forEachIndexed { i, p ->
                    TvPillButton(p.label, { onOpenWith(p) }, if (images.isEmpty() && i == 0) Modifier.focusRequester(requester) else Modifier, icon = p.icon)
                }
            }
            TvSectionHeader("On this device", Modifier.padding(top = 0.dp))
            when {
                loading -> Hint("Loading pictures…")
                !hasAccess -> Hint("No access to the pictures on this device. Use an app above.")
                images.isEmpty() -> Hint("No pictures on this device. Use an app above.")
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(start = 26.dp, end = 26.dp, top = 8.dp, bottom = 48.dp),
                    modifier = Modifier.fillMaxSize().fadingEdges(),
                ) {
                    itemsIndexed(images, key = { _, u -> u.toString() }) { i, uri ->
                        Thumb(uri, thumbs, if (i == 0) Modifier.focusRequester(requester) else Modifier) { onUse(uri) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) = Text(text, style = Type.body.copy(fontSize = 15.sp), color = Tokens.textDim, modifier = Modifier.padding(start = 26.dp))

@Composable
private fun Thumb(uri: Uri, cache: ConcurrentHashMap<Uri, ImageBitmap>, modifier: Modifier, onClick: () -> Unit) {
    val context = LocalContext.current
    // decoded at the drawn size, off the main thread, once per picture
    val img by produceState(cache[uri], uri) {
        if (value == null) value = withContext(Dispatchers.IO) {
            ImageSources.thumbnail(context, uri, 384, 216)?.also { cache[uri] = it }
        }
    }
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().aspectRatio(16f / 9f),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(containerColor = Tokens.card, focusedContainerColor = Tokens.card),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(androidx.compose.foundation.BorderStroke(3.dp, tvFocusedContainer()), shape = shape),
        ),
    ) {
        img?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}

// ── the overlay in miniature: what the settings look like ────────────────────
private fun CardShape.mini(): Shape = when (this) {
    CardShape.SQUARE -> RoundedCornerShape(4.dp); CardShape.ROUNDED -> RoundedCornerShape(8.dp); CardShape.CIRCLE -> RoundedCornerShape(percent = 50)
}

@Composable
fun OverlayPreview(s: Settings) {
    val accent = s.accent.color
    val card: Dp = s.size.dp * 0.46f
    val pin: Dp = card * s.pinSize.factor
    val shape = s.shape.mini()
    val frame = RoundedCornerShape(14.dp)
    Box(
        Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(frame)
            // a stand-in for whatever app is behind the overlay
            .background(Brush.linearGradient(listOf(Color(0xFF3F6FA8), Color(0xFF9A5C93), Color(0xFFD08A52))))
            .border(1.dp, Color.White.copy(alpha = 0.12f), frame),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.fillMaxSize().background(
                when (s.background) {
                    Backdrop.FADE -> Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.84f), 0.26f to Color.Black.copy(alpha = 0.62f), 1f to Color.Black.copy(alpha = 0.8f))
                    Backdrop.DIM -> Brush.verticalGradient(listOf(Color.Black.copy(alpha = s.bgDim / 100f), Color.Black.copy(alpha = s.bgDim / 100f)))
                    Backdrop.BLUR -> Brush.verticalGradient(listOf(Color(0xCC3A3A44), Color(0xCC3A3A44)))
                    Backdrop.COLOR -> Brush.verticalGradient(listOf(BackdropSwatches[s.bgColor].second, BackdropSwatches[s.bgColor].second))
                    Backdrop.IMAGE -> Brush.linearGradient(listOf(Color(0xFF1B2430), Color(0xFF2B1F33)))
                },
            ),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // the pinned section
            Row(
                Modifier.clip(RoundedCornerShape(10.dp)).background(Tokens.surface.copy(alpha = 0.78f))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
            ) { repeat(3) { Box(Modifier.size(pin).clip(shape).background(Tokens.card)) } }
            // the recents row: the focused card in the middle, Kill all at the end
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(card * 0.9f).clip(shape).background(Tokens.card))
                Box(Modifier.size(card * 1.15f).clip(shape).background(Tokens.card).border(2.5.dp, accent, shape))
                Box(Modifier.size(card * 0.9f).clip(shape).background(Tokens.card))
                if (s.showKillAll) Box(
                    Modifier.size(card * 1.2f, card * 1.06f).clip(shape).background(accent.copy(alpha = 0.16f)).border(1.dp, accent.copy(alpha = 0.5f), shape),
                )
            }
        }
    }
}
