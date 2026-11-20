package com.websnake.recentstv.ui.overlay

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.websnake.recentstv.R
import com.websnake.recentstv.data.RecentTask
import com.websnake.recentstv.logic.Geometry
import com.websnake.recentstv.logic.Item
import com.websnake.recentstv.logic.OvState
import com.websnake.recentstv.logic.PEEK_NONE
import com.websnake.recentstv.logic.PEEK_UP
import com.websnake.recentstv.logic.RowId
import com.websnake.recentstv.logic.Scene
import com.websnake.recentstv.logic.Settings
import com.websnake.recentstv.logic.SettingsCodec
import com.websnake.recentstv.ui.theme.LocalAccent
import com.websnake.recentstv.ui.theme.Motion
import com.websnake.recentstv.ui.theme.Tokens
import com.websnake.recentstv.ui.theme.Type
import com.websnake.recentstv.ui.theme.dp
import com.websnake.recentstv.ui.theme.shape
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val GAP = 20.dp
private val PIN_GAP = 18.dp
private val EDGE = 38.dp
private val PEEK = 23.dp
private val FLY = 350.dp
private val LABEL_GAP = 7.dp          // focused card → its name (plus 3dp as it fades in)
private val GLOW_SPREAD = 11.dp       // how far the focus glow reaches past the card
private val EMPTY_SLOT_W = 320.dp
private val GEAR_H = 38.dp            // ⚙ Settings button at the top
private val HEADER_TOP = 36.dp
private val MENU_EST_H = 226.dp       // hold-OK menu height (header + 4 rows)
private val MENU_GAP = 31.dp          // as in ContextMenu: menu bottom → its anchor (the tip is inside it)
private val MENU_TOP_MARGIN = 20.dp   // the menu stays this far below the top of the screen
private val FRAME_TOP = 96.dp         // the pinned section's frame starts below the header line
private val FRAME_HEAD = 36.dp        // frame edge → focused pin's top: the "Pinned" title sits in it
private val FRAME_FOOT = 41.dp        // focused pin's bottom → frame edge while a pin is focused: room for its name
private val FRAME_FOOT_REST = 18.dp   // … and at rest (no name shown)
private val FRAME_SIDE = 22.dp        // frame edge ↔ first / last pin
private val FRAME_MIN_W = 200.dp      // a lone pin still has room for the title
private val FRAME_INSET = 12.dp       // a pin's name stays this far inside the frame
private val FRAME_SCREEN_MARGIN = 12.dp
private val PIN_LABEL_GAP = 6.dp      // pin → its name
private val COUNT_GAP = 46.dp         // recents row → the "N apps" line, below the focused app's name
private val COUNT_GAP_BARE = 22.dp    // … when no name is showing under the row
private val TASK_TAIL = 58.dp         // below the focused card: its name and the count line
private val SECTION_GAP = 24.dp       // pinned frame → recents row
private val FOOTER_CLEAR = 82.dp      // the footer hints own this much of the bottom
private const val DIMMED = 0.32f

/** One place in a row. [item] null = the "No recent apps" placeholder (not focusable). */
private class Slot(val key: String, val item: Item?, val logical: Int, val width: Dp)

/** A row's layout: slots in visual order (left → right; logical order is right → left), their strip, the focused slot. */
private class RowLayout(val slots: List<Slot>, val strip: com.websnake.recentstv.logic.StripLayout, val focusVisual: Int)

@Composable
private fun rowLayout(items: List<Item>, focus: Int, width: (Item) -> Dp, gap: Dp): RowLayout {
    val density = LocalDensity.current
    return remember(items, focus, density, gap, width(Item.Settings)) {
        val slots = items.mapIndexed { i, it -> Slot(it.key, it, i, width(it)) }.asReversed().toList()
        val strip = with(density) { Geometry.layout(slots.map { it.width.toPx() }, gap.toPx(), EDGE.toPx()) }
        RowLayout(slots, strip, slots.indexOfFirst { it.logical == focus })
    }
}

/** Where everything sits top to bottom, in dp from the top of the screen, after fitting the screen's height. */
private class Fit(val cardDp: Dp, val pinDp: Dp, val frameTop: Dp, val frameHead: Dp, val pinsCenter: Dp, val tasksCenter: Dp)

/**
 * The vertical budget: header, the pinned frame, the recents row with its name and count, the footer hints. When it
 * doesn't fit (Large cards with pins "Same as tasks") the gaps tighten first, then the pins shrink, then the cards;
 * when there is room to spare the recents row settles lower, so a Small layout isn't top-heavy.
 */
private fun fit(screenH: Dp, card: Dp, pin: Dp, hasPins: Boolean): Fit {
    val s = Motion.FOCUS_SCALE
    if (!hasPins) return Fit(card, pin, FRAME_TOP, FRAME_HEAD, FRAME_TOP, screenH / 2f - 8.dp)
    val bottom = screenH - FOOTER_CLEAR
    var top = FRAME_TOP; var head = FRAME_HEAD; var gap = SECTION_GAP
    var c = card; var p = pin
    fun need() = top + head + p * s + FRAME_FOOT + gap + c * s + TASK_TAIL
    var over = need() - bottom
    if (over > 0.dp) { val d = minOf(over, 12.dp); gap -= d; over -= d }
    if (over > 0.dp) { val d = minOf(over, 12.dp); top -= d; over -= d }
    if (over > 0.dp) { val d = minOf(over, 6.dp); head -= d; over -= d }
    if (over > 0.dp) { val d = minOf(over, p * s * 0.35f); p -= d / s; over -= d }
    if (over > 0.dp) { val d = minOf(over, c * s * 0.35f); c -= d / s; over -= d }
    val slack = (bottom - need()).coerceAtLeast(0.dp)
    val pinsCenter = top + head + p * s / 2f
    val frameBottom = pinsCenter + p * s / 2f + FRAME_FOOT
    val tasksCenter = frameBottom + gap + minOf(slack * 0.45f, 44.dp) + c * s / 2f
    return Fit(c, p, top, head, pinsCenter, tasksCenter)
}

/**
 * Surface 1 — the recents overlay (magisk-root): the pinned apps in their own framed section at the top, the recent
 * apps on the row below (RTL: Kill all far right, newest next to it), the ⚙ Settings button above both. Positions are
 * computed (Geometry, [fit]), not measured, and every moving value is read in the draw phase (graphicsLayer /
 * drawBehind), so a focus move or a removal animates without recomposing or re-laying-out the rows.
 *
 * [loadId] changes on every fresh load: positions snap instead of sliding in.
 */
@Composable
fun RecentsOverlay(
    state: OvState,
    info: Map<String, RecentTask>,
    settings: Settings,
    loadId: Int,
    onFlyComplete: (String) -> Unit,
    limited: Boolean = false,
    toastOn: Boolean = false,
) = BoxWithConstraints(Modifier.fillMaxSize()) {
    val density = LocalDensity.current
    val viewportW = constraints.maxWidth.toFloat()
    val viewportH = constraints.maxHeight.toFloat()
    val hasPins = state.pins.isNotEmpty()
    val f = remember(settings.size, settings.pinSize, hasPins, viewportH, density) {
        fit(with(density) { viewportH.toDp() }, settings.size.dp, settings.size.dp * settings.pinSize.factor, hasPins)
    }
    val cardDp = f.cardDp
    val pinDp = f.pinDp
    val cardShape = settings.shape.shape()
    val circle = settings.shape == com.websnake.recentstv.logic.CardShape.CIRCLE
    val menuOpen = state.ctxPkg != null
    val selecting = state.selecting
    val pinsFocused = state.focusIn(RowId.PINS)

    // ── where the rows sit (offsets from the vertical centre, px) ──
    val pinsY = with(density) { f.pinsCenter.toPx() } - viewportH / 2f
    val tasksY = with(density) { f.tasksCenter.toPx() } - viewportH / 2f
    fun rowY(r: RowId) = if (r == RowId.PINS) pinsY else tasksY
    val pinHalfPx = with(density) { (pinDp * Motion.FOCUS_SCALE / 2f).toPx() }
    val cardHalfPx = with(density) { (cardDp * Motion.FOCUS_SCALE / 2f).toPx() }
    val frameTopPx = viewportH / 2f + pinsY - pinHalfPx - with(density) { f.frameHead.toPx() }

    // everything but the chosen card fades out when an app is chosen
    val chrome = animateFloatAsState(if (selecting != null) 0f else 1f, tween(Motion.CHROME_FADE_MS), label = "chrome")
    // The menu opens above its card (a pin: above the pinned frame). The rows slide down just far enough for it to fit
    // under the top margin, and never so far that the menu's own row leaves the screen. The other section steps aside.
    val menuA = animateFloatAsState(if (menuOpen) 1f else 0f, tween(200), label = "menuA")
    val anchorRest = if (state.ctxRow == RowId.PINS) frameTopPx + with(density) { (MENU_GAP - 12.dp).toPx() }
        else viewportH / 2f + tasksY - cardHalfPx
    val shiftOpen = with(density) {
        val need = (MENU_EST_H + MENU_GAP + MENU_TOP_MARGIN).toPx() - anchorRest
        val rowBottom = if (state.ctxRow == RowId.PINS) viewportH / 2f + pinsY + pinHalfPx + FRAME_FOOT.toPx()
            else viewportH / 2f + tasksY + cardHalfPx
        need.coerceIn(0f, (viewportH - 16.dp.toPx() - rowBottom).coerceAtLeast(0f))
    }
    val shift = animateFloatAsState(
        if (menuOpen) shiftOpen else 0f,
        tween(Motion.MENU_SHIFT_MS, easing = Motion.standard), label = "shift",
    )
    // while a menu is open the section it doesn't belong to goes away, so nothing shows through or collides
    val tasksA = animateFloatAsState(if (menuOpen && state.ctxRow == RowId.PINS) 0f else 1f, tween(200), label = "tasksA")
    val pinsA = animateFloatAsState(if (menuOpen && state.ctxRow == RowId.TASKS) 0f else 1f, tween(200), label = "pinsA")

    val tasksRow = rowLayout(state.items, state.focus, { if (it == Item.KillAll) cardDp * 1.34f else cardDp }, GAP)
    val pinsRow = rowLayout(state.pins, state.pinFocus, { pinDp }, PIN_GAP)
    val pinsOverflow = pinsRow.strip.total > viewportW

    /**
     * Horizontal offset of a row's strip. The recents row scrolls so its focused app is in the middle; the pinned
     * section stays put, centred on screen. With more pins than fit across it scrolls too, but only as far as its ends.
     */
    fun offsetOf(r: RowId, layout: RowLayout): Float = when {
        r == RowId.TASKS -> Geometry.stripOffset(viewportW, layout.strip, layout.focusVisual)
        !pinsOverflow -> (viewportW - layout.strip.total) / 2f
        else -> Geometry.stripOffset(viewportW, layout.strip, layout.focusVisual).coerceIn(viewportW - layout.strip.total, 0f)
    }

    // ── a darker backdrop while a menu is open: the dimmed cards don't let the app behind read through ──
    Box(Modifier.fillMaxSize().drawBehind { if (menuA.value > 0.01f) drawRect(Color.Black, alpha = 0.45f * menuA.value) })

    // ── header: the title at the top left, the ⚙ at the top centre, on one line; both step aside for a menu ──
    Box(
        Modifier.align(Alignment.TopStart).padding(start = 48.dp, top = HEADER_TOP).height(GEAR_H)
            .graphicsLayer { alpha = chrome.value * (1f - menuA.value) },
        contentAlignment = Alignment.CenterStart,
    ) { Text("Recent apps", style = Type.title, color = Tokens.text, maxLines = 1) }
    Box(
        Modifier.align(Alignment.TopCenter).padding(top = HEADER_TOP)
            .graphicsLayer { alpha = chrome.value * (1f - menuA.value) },
    ) { SettingsButton(state.gearFocused) }

    // ── how many recent apps: under the recents row, centred on screen (it doesn't move with the row). It steps
    //    aside for anything that lands there: the remove hint, a toast, a menu ──
    val count = SettingsCodec.countLabel(state.tasks.size)
    val nameShown = state.focusIn(RowId.TASKS) && state.focusedItem is Item.Task
    val countA = animateFloatAsState(
        if (menuOpen || state.killing || state.peek != PEEK_NONE || state.flying != null || toastOn) 0f else 1f,
        tween(160), label = "count",
    )
    val countY = animateFloatAsState(
        with(density) { (if (nameShown) COUNT_GAP else COUNT_GAP_BARE).toPx() }, tween(Motion.FOCUS_MS, easing = Motion.standard), label = "countY",
    )
    if (count.isNotEmpty()) Text(
        count, style = Type.small, color = Tokens.textDim, maxLines = 1,
        modifier = Modifier.align(Alignment.Center).graphicsLayer {
            alpha = countA.value * chrome.value
            translationY = (tasksY + cardHalfPx + countY.value).roundToInt().toFloat()
        },
    )

    // ── the pinned section: an outlined frame around the pins, its "Pinned" title inside the top edge ──
    val measurer = rememberTextMeasurer()
    val focusedPin = if (pinsFocused) pinsRow.slots.getOrNull(pinsRow.focusVisual) else null
    val focusedPinLabelW = focusedPin?.let { s -> info[s.key]?.label }?.let { name ->
        remember(name) { measurer.measure(name, Type.label, maxLines = 1).size.width.toFloat() }
    } ?: 0f
    val (frameL, frameW) = if (!hasPins) 0f to 0f else with(density) {
        val strip = pinsRow.strip
        val ox = offsetOf(RowId.PINS, pinsRow)
        var l = ox + strip.lefts.first() - FRAME_SIDE.toPx()
        var r = ox + strip.lefts.last() + strip.widths.last() + FRAME_SIDE.toPx()
        val mid = (l + r) / 2f
        var half = maxOf((r - l) / 2f, FRAME_MIN_W.toPx() / 2f)
        // wide enough for the focused pin's name to sit centred under its icon
        if (focusedPin != null) {
            val cx = ox + strip.center(pinsRow.focusVisual)
            half = maxOf(half, kotlin.math.abs(cx - mid) + focusedPinLabelW / 2f + FRAME_INSET.toPx())
        }
        l = (mid - half).coerceAtLeast(FRAME_SCREEN_MARGIN.toPx())
        r = (mid + half).coerceAtMost(viewportW - FRAME_SCREEN_MARGIN.toPx())
        l to (r - l)
    }
    if (hasPins) {
        // the outline takes the accent, and the frame grows to hold the name, while the focus is in the section
        val active = animateFloatAsState(if (pinsFocused) 1f else 0f, tween(Motion.FOCUS_MS, easing = Motion.standard), label = "pinActive")
        val accent = LocalAccent.current
        val fl = remember(loadId) { Animatable(frameL) }
        val fw = remember(loadId) { Animatable(frameW) }
        LaunchedEffect(frameL) { if (fl.targetValue != frameL) fl.animateTo(frameL, Motion.glide()) }
        LaunchedEffect(frameW) { if (fw.targetValue != frameW) fw.animateTo(frameW, Motion.glide()) }
        Box(
            Modifier.fillMaxSize().drawBehind {
                val a = pinsA.value * chrome.value
                if (a <= 0.01f) return@drawBehind
                val top = frameTopPx + shift.value
                val foot = FRAME_FOOT_REST.toPx() + (FRAME_FOOT - FRAME_FOOT_REST).toPx() * active.value
                val h = f.frameHead.toPx() + pinHalfPx * 2f + foot
                val cr = CornerRadius(24.dp.toPx())
                // a dark fill: whatever the app behind shows there must not read as part of the section
                drawRoundRect(Tokens.surface.copy(alpha = 0.78f * a), Offset(fl.value, top), Size(fw.value, h), cr)
                val edge = lerp(Color.White.copy(alpha = 0.18f), accent.base.copy(alpha = 0.6f), active.value)
                drawRoundRect(edge.copy(alpha = edge.alpha * a), Offset(fl.value, top), Size(fw.value, h), cr, style = Stroke(1.5.dp.toPx()))
            },
        )
        Row(
            Modifier.align(Alignment.Center).graphicsLayer {
                alpha = pinsA.value * chrome.value
                translationY = (pinsY - pinHalfPx - f.frameHead.toPx() / 2f + shift.value).roundToInt().toFloat()
            },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(painterResource(R.drawable.ic_pin), null, colorFilter = ColorFilter.tint(Tokens.textDim), modifier = Modifier.size(13.dp))
            Text("Pinned", style = Type.small.copy(fontWeight = FontWeight.Bold), color = Tokens.textDim, maxLines = 1)
        }
    }

    @Composable
    fun DrawRow(r: RowId, layout: RowLayout) {
        val targetTx = offsetOf(r, layout)
        val tx = remember(loadId, r) { Animatable(targetTx) }
        LaunchedEffect(targetTx, loadId) {
            if (tx.targetValue != targetTx) tx.animateTo(targetTx, Motion.glide())
        }
        val y = rowY(r)
        val sectionA = if (r == RowId.PINS) pinsA else tasksA
        layout.slots.forEachIndexed { vi, slot ->
            key(r, slot.key) {
                val left = layout.strip.lefts[vi]
                val leftAnim = remember(loadId) { Animatable(left) }
                LaunchedEffect(left) {   // a neighbour was removed or moved: slide over
                    if (leftAnim.targetValue != left) leftAnim.animateTo(left, Motion.glide())
                }
                val item = slot.item
                val focused = state.focusIn(r) && slot.logical == (if (r == RowId.PINS) state.pinFocus else state.focus)
                val chosen = selecting != null && slot.key == selecting && state.selectRow == r
                val isCtx = menuOpen && state.ctxRow == r && slot.key == state.ctxPkg
                val dim = animateFloatAsState(if (menuOpen && !isCtx) DIMMED else 1f, tween(200), label = "dim")
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .graphicsLayer {
                            // whole pixels: text and icon move together, no sub-pixel shimmer
                            translationX = (leftAnim.value + tx.value).roundToInt().toFloat()
                            translationY = (y + shift.value).roundToInt().toFloat()
                            alpha = dim.value * sectionA.value * (if (chosen) 1f else chrome.value)
                        },
                ) {
                    when (item) {
                        null -> {}
                        Item.KillAll -> KillAllTile(focused, cardDp, cardShape, state.killing)
                        Item.Settings -> {}
                        is Item.Task, is Item.Pin -> {
                            val task = info[slot.key]
                            if (task != null) AppCard(
                                task = task,
                                pinned = r == RowId.TASKS && slot.key in state.pinned,
                                running = item !is Item.Pin || item.running,
                                // the dots step aside while a pin's name is showing: the name would run over them
                                showRunning = r == RowId.PINS && !pinsFocused,
                                focused = focused,
                                pressing = focused && state.pressing,
                                peek = if (focused && r == RowId.TASKS) state.peek else PEEK_NONE,
                                flying = state.flying == slot.key && state.flyRow == r,
                                flyDir = state.flyDir,
                                killing = state.killing && r == RowId.TASKS,
                                staggerIndex = slot.logical,
                                labelOn = focused && state.peek == PEEK_NONE && state.flying == null && !state.killing &&
                                    selecting == null && !menuOpen,
                                cardDp = slot.width, cardShape = cardShape, circle = circle,
                                compactLabel = r == RowId.PINS,
                                labelMaxW = with(density) { (frameW - FRAME_INSET.toPx() * 2f).coerceAtLeast(40.dp.toPx()).toDp() },
                                labelMinX = frameL + with(density) { FRAME_INSET.toPx() } - (targetTx + layout.strip.center(vi)),
                                labelMaxX = frameL + frameW - with(density) { FRAME_INSET.toPx() } - (targetTx + layout.strip.center(vi)),
                                onFlyComplete = { onFlyComplete(slot.key) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (hasPins) DrawRow(RowId.PINS, pinsRow)
    DrawRow(RowId.TASKS, tasksRow)
    if (state.emptySlot) Box(
        Modifier.align(Alignment.Center).graphicsLayer {
            translationY = (tasksY + shift.value).roundToInt().toFloat()
            alpha = chrome.value * tasksA.value
        },
    ) { EmptySlotBox(cardDp) }

    // ── hold-OK menu: above the target card; for a pin, above the pinned frame ──
    val ctxTask = state.ctxPkg?.let { info[it] }
    val ctxLayout = if (state.ctxRow == RowId.PINS) pinsRow else tasksRow
    val ctxVisual = ctxLayout.slots.indexOfFirst { it.key == state.ctxPkg }
    ContextMenu(
        task = ctxTask,
        pinned = state.ctxPkg != null && state.ctxPkg in state.pinned,
        running = when (val it = state.ctxItem) { is Item.Pin -> it.running; is Item.Task -> true; else -> false },
        selected = state.ctxSel,
        anchorX = {
            if (ctxVisual >= 0) ctxLayout.strip.center(ctxVisual) + offsetOf(state.ctxRow, ctxLayout)
            else viewportW / 2f
        },
        anchorTop = { anchorRest + shift.value },
        viewportW = viewportW,
    )

    // ── footer key hints: what the keys do on the thing in focus (hidden while the menu is open) ──
    val footer by animateFloatAsState(if (menuOpen || toastOn) 0f else 1f, tween(200), label = "foot")
    val hints = when {
        state.gearFocused -> listOf("OK" to "Settings", "↓" to "Apps", "Back" to "Close")
        state.peek != PEEK_NONE -> listOf("OK / ↓" to "Remove", "↑ / Back" to "Keep")
        state.focusedItem == Item.KillAll -> listOf("OK" to "Close all apps", "Back" to "Close")
        pinsFocused -> listOf("OK" to "Open", "Hold OK" to "Options", "Back" to "Close")
        else -> listOf("OK" to "Open", "Hold OK" to "Options", "↓" to "Remove", "Back" to "Close")
    }
    Row(
        Modifier.align(Alignment.BottomCenter).padding(bottom = 34.dp).graphicsLayer { alpha = footer * chrome.value },
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        hints.forEach { (k, v) -> key(k, v) { KeyHint(k, v) } }
    }
    // Shizuku is off (after a reboot, Shizuku build only): the row comes from the last list + usage access
    if (limited) Text(
        "Shizuku is off · apps open from their start screen · removals wait for Shizuku",
        style = Type.small, color = LIMITED_TINT, maxLines = 1,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 74.dp).graphicsLayer { alpha = footer * chrome.value },
    )

    // ── select: the chosen tile grows to full screen in the app's colour ──
    if (selecting != null) {
        val t = info[selecting]
        val layout = if (state.selectRow == RowId.PINS) pinsRow else tasksRow
        val vi = layout.slots.indexOfFirst { it.key == selecting }
        if (t != null && vi >= 0) {
            val w = with(density) { (layout.slots[vi].width * Motion.PRESS_SCALE).toPx() }
            val cx = layout.strip.center(vi) + offsetOf(state.selectRow, layout)
            val cy = viewportH / 2f + rowY(state.selectRow) + shift.value
            SelectZoom(
                task = t,
                from = androidx.compose.ui.geometry.Rect(cx - w / 2f, cy - w / 2f, cx + w / 2f, cy + w / 2f),
                startRadius = settings.shape.let { s ->
                    when (s) {
                        com.websnake.recentstv.logic.CardShape.CIRCLE -> w / 2f
                        com.websnake.recentstv.logic.CardShape.ROUNDED -> with(density) { 16.dp.toPx() } * Motion.PRESS_SCALE
                        com.websnake.recentstv.logic.CardShape.SQUARE -> with(density) { 8.dp.toPx() } * Motion.PRESS_SCALE
                    }
                },
                iconDp = layout.slots[vi].width * 0.66f,
            )
        }
    }
}

/**
 * The focus glow: a soft halo that follows [shape] (the card shape chosen in Settings) and fades out over [spread]
 * past the card — a few widening outlines, cheap to draw and never wider than the card needs.
 */
private fun DrawScope.shapeGlow(shape: Shape, color: Color, at: Offset, w: Float, h: Float, spread: Float, strength: Float) {
    if (strength <= 0.01f || spread <= 0f) return
    val steps = 8
    val band = spread / steps
    for (i in 0 until steps) {
        val grow = band * (i + 0.5f)
        val fade = 1f - i / steps.toFloat()
        val outline = shape.createOutline(Size(w + grow * 2f, h + grow * 2f), layoutDirection, this)
        translate(at.x - w / 2f - grow, at.y - h / 2f - grow) {
            drawOutline(outline, color.copy(alpha = color.alpha * strength * fade * fade * 0.6f), style = Stroke(band * 1.25f))
        }
    }
}

private val LIMITED_TINT = androidx.compose.ui.graphics.Color(0xFFE8B04B)

@Composable
internal fun KeyHint(key: String, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            key, style = Type.small.copy(fontWeight = FontWeight.Bold), color = Tokens.text, maxLines = 1,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(Tokens.text.copy(alpha = 0.08f))
                .border(1.dp, Tokens.line, RoundedCornerShape(4.dp))
                .padding(horizontal = 7.dp, vertical = 3.dp),
        )
        Text(label, style = Type.small, color = Tokens.textDim, maxLines = 1)
    }
}

/** Empty-with-pins (screen 11): a dashed placeholder where the tasks would be. */
@Composable
private fun EmptySlotBox(cardDp: Dp) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, tween(Motion.FOCUS_MS)) }
    Column(
        Modifier
            .size(EMPTY_SLOT_W, cardDp * 1.18f)
            .graphicsLayer { alpha = a.value }
            .drawBehind {
                drawRoundRect(
                    Color.White.copy(alpha = 0.14f), cornerRadius = CornerRadius(20.dp.toPx()),
                    style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))),
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterVertically),
    ) {
        Text("No recent apps", style = Type.label, color = Tokens.text, maxLines = 1)
        Text("Pinned apps are still one press away.", style = Type.small, color = Tokens.textDim, maxLines = 1)
    }
}

@Composable
private fun AppCard(
    task: RecentTask,
    pinned: Boolean,
    running: Boolean,
    showRunning: Boolean,
    focused: Boolean,
    pressing: Boolean,
    peek: Int,
    flying: Boolean,
    flyDir: Int,
    killing: Boolean,
    staggerIndex: Int,
    labelOn: Boolean,
    cardDp: Dp,
    cardShape: Shape,
    onFlyComplete: () -> Unit,
    circle: Boolean = false,
    compactLabel: Boolean = false,       // pinned section: the name only, right under the icon
    labelMaxW: Dp = 300.dp,
    labelMinX: Float = 0f,               // compact: the name's allowed span, px from the card's centre
    labelMaxX: Float = 0f,
) {
    val density = LocalDensity.current
    val accent = LocalAccent.current
    val std = tween<Float>(Motion.FOCUS_MS, easing = Motion.standard)
    val scale = animateFloatAsState(
        when { pressing -> Motion.PRESS_SCALE; focused -> Motion.FOCUS_SCALE; else -> Motion.IDLE_SCALE },
        if (pressing) tween(Motion.PRESS_MS, easing = Motion.standard) else Motion.glide(), label = "scale",
    )
    val focusAmt = animateFloatAsState(if (focused) 1f else 0f, Motion.glide(), label = "focus")
    val baseAlpha = animateFloatAsState(if (focused) 1f else Motion.IDLE_ALPHA, std, label = "alpha")
    val peekY = animateFloatAsState(if (peek != PEEK_NONE) peek * with(density) { PEEK.toPx() } else 0f, Motion.lift(), label = "peek")
    val fly = remember { Animatable(0f) }
    val flyAlpha = remember { Animatable(1f) }
    val flyPx = with(density) { FLY.toPx() }
    // label: fades in 40 ms after the focus lands and out as it leaves — a cross-fade, never a blink
    val labelA = animateFloatAsState(
        if (labelOn) 1f else 0f, if (labelOn) tween(200, delayMillis = 40) else tween(160), label = "label",
    )
    var labelUsed by remember { mutableStateOf(false) }
    if (labelOn) labelUsed = true

    LaunchedEffect(flying) {
        if (flying) {
            coroutineScope {
                // 1.8: one continuous ease from the lifted spot (no hard start), the fade trailing behind it
                launch { fly.animateTo(flyDir * flyPx, tween(Motion.FLY_MS, easing = Motion.flySmooth)) }
                launch { flyAlpha.animateTo(0f, tween(Motion.FLY_MS - 60, delayMillis = 60, easing = Motion.standard)) }
            }
            onFlyComplete()
        }
    }
    LaunchedEffect(killing) {
        if (killing) {
            delay(staggerIndex * Motion.KILL_STAGGER_MS.toLong())
            coroutineScope {
                launch { fly.animateTo(-flyPx, tween(Motion.FLY_MS, easing = Motion.flyOut)) }
                launch { flyAlpha.animateTo(0f, tween(Motion.FLY_MS, easing = Motion.flyOut)) }
            }
        }
    }

    // Fixed footprint = exactly one card; glow, streak, label and hint overflow it.
    Box(Modifier.size(cardDp), contentAlignment = Alignment.Center) {
        // accent glow + motion streak (draw phase only)
        Box(
            Modifier
                .requiredSize(cardDp * 1.7f)
                .drawBehind {
                    val f = focusAmt.value * flyAlpha.value
                    val y = peekY.value + fly.value
                    val side = cardDp.toPx() * scale.value
                    shapeGlow(cardShape, accent.glow, center + Offset(0f, y), side, side, GLOW_SPREAD.toPx(), f)
                    if (kotlin.math.abs(fly.value) > 1f) {        // streak trailing the flying card
                        val w = 3.dp.toPx(); val len = 110.dp.toPx()
                        val up = fly.value < 0f
                        val edge = center.y + y + (if (up) 1f else -1f) * cardDp.toPx() * 0.5f
                        val top = if (up) edge else edge - len
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                if (up) listOf(accent.glow, Color.Transparent) else listOf(Color.Transparent, accent.glow),
                                top, top + len,
                            ),
                            topLeft = Offset(center.x - w / 2f, top), size = Size(w, len),
                            cornerRadius = CornerRadius(w), alpha = 0.5f * flyAlpha.value,
                        )
                    }
                },
        )
        // the card: one layer for transform + clip; the ring is drawn on top, inside the clip
        Box(
            Modifier
                .size(cardDp)
                .graphicsLayer {
                    scaleX = scale.value; scaleY = scale.value
                    translationY = peekY.value + fly.value
                    alpha = flyAlpha.value
                    this.shape = cardShape; clip = true
                }
                .background(Tokens.card)
                .drawWithContent {
                    drawContent()
                    val shade = (1f - baseAlpha.value) * 1.5f
                    if (shade > 0.01f) drawRect(Color.Black, alpha = shade.coerceAtMost(1f))
                    val f = focusAmt.value
                    if (f > 0.01f) drawOutline(
                        cardShape.createOutline(size, layoutDirection, this),
                        accent.base.copy(alpha = f), style = Stroke(width = 5.dp.toPx() / scale.value),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            val icon = task.icon
            if (icon != null) {
                Image(
                    bitmap = icon, contentDescription = task.label, contentScale = ContentScale.Fit,
                    modifier = Modifier.size(cardDp * 0.66f).clip(cardShape),
                )
            } else {
                Text(task.label.take(1).uppercase(), style = Type.hero, color = Tokens.text)
            }
        }
        // pin badge on the top corner (RTL inline-end = left), moving with the card
        val badge = (cardDp * 0.24f).coerceIn(16.dp, 22.dp)
        if (pinned) {
            Box(
                Modifier
                    .size(cardDp)
                    .graphicsLayer {
                        scaleX = scale.value; scaleY = scale.value
                        translationY = peekY.value + fly.value
                        alpha = flyAlpha.value
                    },
            ) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .graphicsLayer {
                            // a circle's outline is 14.6 % in from the corner of its square
                            val at = if (circle) cardDp.toPx() * 0.146f - badge.toPx() / 2f else -5.dp.toPx()
                            translationX = at; translationY = at
                        }
                        .size(badge)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(Tokens.bg)
                        .border(1.dp, Tokens.text.copy(alpha = 0.22f), RoundedCornerShape(percent = 50)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(painterResource(R.drawable.ic_pin), null, colorFilter = ColorFilter.tint(Tokens.text), modifier = Modifier.size(badge * 0.5f))
                }
            }
        }
        // running dot under an unfocused pin (pins row) whose app is also running
        if (showRunning && running) Box(
                Modifier
                    .graphicsLayer {
                        translationY = (cardDp * Motion.IDLE_SCALE / 2f + 11.dp).toPx()
                        alpha = 0.7f * (1f - focusAmt.value) * flyAlpha.value
                    }
                    .size(5.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Tokens.text),
            )
        // the name under the focused card, nothing else (a pin: close under the icon, inside its frame)
        if (labelUsed) Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = (if (compactLabel) Modifier.wrapContentWidth(unbounded = true).widthIn(max = labelMaxW) else Modifier.requiredWidth(300.dp))
                .graphicsLayer {
                    alpha = labelA.value
                    val gap = (if (compactLabel) PIN_LABEL_GAP else LABEL_GAP) + 3.dp * labelA.value
                    val y = (cardDp * Motion.FOCUS_SCALE / 2f + gap).toPx() + size.height / 2f
                    translationY = y.roundToInt().toFloat()
                    if (compactLabel) {
                        // an end pin's name would stick out of the frame: slide it back in
                        val half = size.width / 2f
                        translationX = (if (-half < labelMinX) labelMinX + half else if (half > labelMaxX) labelMaxX - half else 0f)
                            .roundToInt().toFloat()
                    }
                },
        ) {
            Text(
                task.label, style = Type.label, color = Tokens.text, maxLines = 1, textAlign = TextAlign.Center,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        // stage-1 hint, on the side the card is peeking to
        PeekHint(peek = if (flying) PEEK_NONE else peek, cardDp = cardDp)
    }
}

@Composable
private fun PeekHint(peek: Int, cardDp: Dp) {
    val accent = LocalAccent.current
    var lastDir by remember { mutableStateOf(PEEK_UP) }
    if (peek != PEEK_NONE) lastDir = peek
    val a by animateFloatAsState(if (peek != PEEK_NONE) 1f else 0f, tween(if (peek != PEEK_NONE) 160 else 120), label = "hint")
    if (a <= 0f) return
    Box(
        Modifier
            .requiredWidth(cardDp * 4.5f)
            .graphicsLayer {
                alpha = a
                translationY = lastDir * (cardDp * 0.5f * Motion.FOCUS_SCALE + 48.dp).toPx()
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(Tokens.surface)
                .border(1.dp, accent.border, RoundedCornerShape(percent = 50))
                .padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(if (lastDir == PEEK_UP) "▲" else "▼", style = Type.small, color = accent.base)
            Text("Press ↓ or OK to remove", style = Type.small, color = accent.base, maxLines = 1)
        }
    }
}

@Composable
private fun KillAllTile(focused: Boolean, cardDp: Dp, cardShape: Shape, killing: Boolean) {
    val accent = LocalAccent.current
    val std = tween<Float>(Motion.FOCUS_MS, easing = Motion.standard)
    val scale = animateFloatAsState(if (focused) Motion.KILL_FOCUS_SCALE else 1f, Motion.glide(), label = "kscale")
    val f = animateFloatAsState(if (focused) 1f else 0f, std, label = "kfocus")
    val gone = animateFloatAsState(if (killing) 0f else 1f, tween(200), label = "kgone")
    val glyph by animateColorAsState(if (focused) accent.onDark else accent.base, tween(Motion.FOCUS_MS), label = "kglyph")
    val w = cardDp * 1.34f
    val h = cardDp * 1.18f

    Box(Modifier.size(w, h), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .requiredSize(w * 1.5f, h * 1.5f)
                .drawBehind {
                    val a = f.value * gone.value
                    shapeGlow(cardShape, accent.glow, center, w.toPx() * scale.value, h.toPx() * scale.value, GLOW_SPREAD.toPx(), a)
                },
        )
        // unfocused: accent tint + 45 % accent border; focused: solid accent (design)
        Column(
            Modifier
                .size(w, h)
                .graphicsLayer {
                    scaleX = scale.value; scaleY = scale.value; alpha = gone.value
                    this.shape = cardShape; clip = true
                }
                .drawBehind {
                    val outline = cardShape.createOutline(size, layoutDirection, this)
                    drawOutline(outline, Tokens.surface)        // opaque under the tint: a bright app must not show through
                    drawOutline(outline, lerp(accent.tint, accent.base, f.value))
                    drawOutline(outline, lerp(accent.border, accent.base, f.value), style = Stroke(2.dp.toPx()))
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterVertically),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_clear_all),
                contentDescription = "Kill all",
                colorFilter = ColorFilter.tint(glyph),
                modifier = Modifier.size(cardDp * 0.42f),
            )
            Text("Kill all", style = Type.killLabel, color = glyph, maxLines = 1)
        }
    }
}

/** The ⚙ Settings button at the top of the overlay, as wide as its icon and text. Neutral until focused (↑ from the top row). */
@Composable
internal fun SettingsButton(focused: Boolean) {
    val accent = LocalAccent.current
    val std = tween<Float>(Motion.FOCUS_MS, easing = Motion.standard)
    val scale = animateFloatAsState(if (focused) Motion.KILL_FOCUS_SCALE else 1f, Motion.glide(), label = "pscale")
    val f = animateFloatAsState(if (focused) 1f else 0f, std, label = "pfocus")
    val glyph by animateColorAsState(if (focused) accent.onDark else Tokens.textDim, tween(Motion.FOCUS_MS), label = "pglyph")
    val shape = RoundedCornerShape(GEAR_H / 2)
    Row(
        Modifier
            .height(GEAR_H)
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .drawBehind {
                // the same halo as the cards, drawn before the clip
                shapeGlow(shape, accent.glow, center, size.width, size.height, GLOW_SPREAD.toPx(), f.value)
            }
            .clip(shape)
            .drawBehind {
                val outline = shape.createOutline(size, layoutDirection, this)
                drawOutline(outline, lerp(Tokens.surface2, accent.base, f.value))
                drawOutline(outline, lerp(Tokens.line, accent.base, f.value), style = Stroke(2.dp.toPx()))
            }
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_settings),
            contentDescription = "Settings",
            colorFilter = ColorFilter.tint(glyph),
            modifier = Modifier.size(GEAR_H * 0.5f),
        )
        Text("Settings", style = Type.small.copy(fontWeight = FontWeight.Bold), color = glyph, maxLines = 1, softWrap = false)
    }
}
