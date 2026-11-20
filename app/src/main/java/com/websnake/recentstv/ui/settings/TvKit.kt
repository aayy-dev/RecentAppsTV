package com.websnake.recentstv.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.RadioButton
import androidx.tv.material3.RadioButtonDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Switch
import androidx.tv.material3.SwitchDefaults
import androidx.tv.material3.Text
import com.websnake.recentstv.ui.theme.LocalAccent
import com.websnake.recentstv.ui.theme.Tokens
import com.websnake.recentstv.ui.theme.Type

/*
 * The settings screens' building blocks, in the format Google TV's own settings use (and InstallerX Revived's TV
 * pages): borderless rows that become a light pill when focused, uppercase section labels, pill buttons, and a
 * two-pane page — rows on the left, what the focused row means on the right. Focus, scrolling and D-pad movement are
 * the framework's (androidx.tv Surface), not hand-rolled.
 */
object TvKit {
    val SafeHorizontal = 58.dp
    val SafeVertical = 36.dp
    val Pill = RoundedCornerShape(percent = 50)
}

/** The light, accent-tinted fill of whatever has the focus. */
@Composable
fun tvFocusedContainer(): Color = lerp(LocalAccent.current.base, Color.White, 0.72f)

/** Dark content on [tvFocusedContainer]. */
val TvFocusedContent = Color(0xFF0B0D10)

@Composable
fun TvPageTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, style = Type.hero.copy(fontSize = 34.sp, lineHeight = 40.sp), color = Tokens.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

@Composable
fun TvSectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(), modifier = modifier.padding(start = 26.dp, top = 22.dp, bottom = 6.dp),
        style = Type.badge.copy(fontSize = 12.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.Medium), color = Tokens.textDim,
    )
}

/**
 * A full-width row. Transparent until focused, then a light pill with dark text. [leading] is an app icon or a
 * swatch; [trailing] a switch, a radio button, a value or a chevron. RIGHT also runs [onClick] when [opensPage].
 */
@Composable
fun TvRow(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    leading: (@Composable (focused: Boolean) -> Unit)? = null,
    trailing: (@Composable (focused: Boolean) -> Unit)? = null,
    opensPage: Boolean = false,
    onFocused: (() -> Unit)? = null,
    onClick: () -> Unit = {},
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    LaunchedEffect(focused) { if (focused) onFocused?.invoke() }
    val container = tvFocusedContainer()
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().then(
            if (!opensPage) Modifier else Modifier.onPreviewKeyEvent { e ->
                if (e.key == Key.DirectionRight) { if (e.type == KeyEventType.KeyDown) onClick(); true } else false
            },
        ),
        shape = ClickableSurfaceDefaults.shape(shape = TvKit.Pill),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent, contentColor = Tokens.text,
            focusedContainerColor = container, focusedContentColor = TvFocusedContent,
            pressedContainerColor = container, pressedContentColor = TvFocusedContent,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        interactionSource = interaction,
    ) {
        Row(
            Modifier.defaultMinSize(minHeight = 60.dp).padding(start = if (leading != null) 14.dp else 26.dp, end = 24.dp, top = 11.dp, bottom = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) { leading(focused); Spacer(Modifier.width(16.dp)) }
            Column(Modifier.weight(1f)) {
                Text(title, style = Type.row.copy(fontSize = 17.sp, lineHeight = 22.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!summary.isNullOrBlank()) Text(
                    summary, style = Type.small.copy(fontSize = 14.sp, lineHeight = 19.sp),
                    color = LocalContentColor.current.copy(alpha = 0.72f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            if (trailing != null) { Spacer(Modifier.width(16.dp)); trailing(focused) }
        }
    }
}

/** The › that marks a row leading to another page, or the current value of a choice. */
@Composable
fun TvValue(text: String?, chevron: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!text.isNullOrBlank()) Text(text, style = Type.small.copy(fontSize = 15.sp), color = LocalContentColor.current.copy(alpha = 0.8f), maxLines = 1)
        if (chevron) Text("›", style = Type.title.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold), color = LocalContentColor.current.copy(alpha = 0.7f))
    }
}

@Composable
fun TvSwitchRow(
    title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier,
    summary: String? = null, leading: (@Composable (Boolean) -> Unit)? = null, onFocused: (() -> Unit)? = null,
) {
    val accent = LocalAccent.current.base
    TvRow(
        title, modifier, summary, leading, onFocused = onFocused, onClick = { onCheckedChange(!checked) },
        trailing = { focused ->
            // on the light focused pill the switch needs dark tracks to stay visible
            Switch(
                checked = checked, onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = if (focused) lerp(accent, Color.Black, 0.45f) else accent,
                    uncheckedThumbColor = if (focused) Color.White else Tokens.textDim,
                    uncheckedTrackColor = if (focused) Color.Black.copy(alpha = 0.35f) else Tokens.surface2,
                    uncheckedBorderColor = Color.Transparent,
                ),
            )
        },
    )
}

@Composable
fun TvRadioRow(
    title: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier,
    summary: String? = null, leading: (@Composable (Boolean) -> Unit)? = null, onFocused: (() -> Unit)? = null,
) {
    val accent = LocalAccent.current.base
    TvRow(
        title, modifier, summary, leading, onFocused = onFocused, onClick = onClick,
        trailing = { focused ->
            RadioButton(
                selected = selected, onClick = null,
                colors = RadioButtonDefaults.colors(
                    selectedColor = if (focused) lerp(accent, Color.Black, 0.5f) else accent,
                    unselectedColor = if (focused) Color.Black.copy(alpha = 0.5f) else Tokens.textDim,
                ),
            )
        },
    )
}

/** A pill button: dark until focused, then the same light fill as a focused row. */
@Composable
fun TvPillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageBitmap? = null) {
    Button(
        onClick = onClick, modifier = modifier,
        shape = ButtonDefaults.shape(shape = TvKit.Pill),
        colors = ButtonDefaults.colors(
            containerColor = Tokens.surface2, contentColor = Tokens.text,
            focusedContainerColor = tvFocusedContainer(), focusedContentColor = TvFocusedContent,
            pressedContainerColor = tvFocusedContainer(), pressedContentColor = TvFocusedContent,
        ),
        scale = ButtonDefaults.scale(focusedScale = 1.04f),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
    ) {
        if (icon != null) { Image(icon, null, Modifier.size(22.dp)); Spacer(Modifier.width(10.dp)) }
        Text(text, style = Type.label.copy(fontSize = 15.sp), maxLines = 1)
    }
}

/** An app icon on a rounded tile (or the app's initial). */
@Composable
fun TvAppIcon(icon: ImageBitmap?, label: String) {
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Tokens.card), contentAlignment = Alignment.Center) {
        if (icon != null) Image(icon, label, Modifier.size(30.dp), contentScale = ContentScale.Fit)
        else Text(label.take(1).uppercase(), style = Type.row, color = Tokens.text)
    }
}

/** A colour dot for accent and background-colour choices. */
@Composable
fun TvSwatch(color: Color) {
    Box(Modifier.size(30.dp).clip(CircleShape).background(color).border(1.5.dp, Color.White.copy(alpha = 0.35f), CircleShape))
}

/** What the right-hand pane says about the focused row. */
class TvPreview(val title: String, val text: String? = null, val visual: (@Composable () -> Unit)? = null)

/**
 * A two-pane settings page: the title and the rows on the left, the focused row's [preview] on the right.
 * Rows scrolling up under the title fade out instead of being cut.
 */
@Composable
fun TvSettingsPage(
    title: String,
    preview: TvPreview?,
    subtitle: String? = null,
    header: (@Composable () -> Unit)? = null,
    /** The list's first visible item: a row far down must be composed before it can take the focus. */
    firstItem: Int = 0,
    rows: LazyListScope.() -> Unit,
) {
    Row(Modifier.fillMaxSize().background(Tokens.bg).padding(horizontal = TvKit.SafeHorizontal)) {
        Column(Modifier.weight(0.54f).fillMaxHeight().padding(top = TvKit.SafeVertical)) {
            TvPageTitle(title, Modifier.padding(start = 26.dp))
            if (subtitle != null) Text(
                subtitle, style = Type.body.copy(fontSize = 15.sp), color = Tokens.textDim,
                modifier = Modifier.padding(start = 26.dp, top = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (header != null) Box(Modifier.padding(start = 20.dp, top = 14.dp)) { header() }
            LazyColumn(
                state = rememberLazyListState(initialFirstVisibleItemIndex = firstItem),
                modifier = Modifier.fillMaxSize().padding(top = 10.dp).fadingEdges(),
                contentPadding = PaddingValues(top = 12.dp, bottom = 48.dp, start = 6.dp, end = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                content = rows,
            )
        }
        Spacer(Modifier.width(36.dp))
        Box(Modifier.weight(0.46f).fillMaxHeight().padding(top = TvKit.SafeVertical + 62.dp, bottom = TvKit.SafeVertical)) {
            if (preview != null) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                preview.visual?.let { Box(Modifier.fillMaxWidth().padding(bottom = 8.dp)) { it() } }
                Text(preview.title, style = Type.title.copy(fontSize = 22.sp, lineHeight = 28.sp), color = Tokens.text)
                if (!preview.text.isNullOrBlank()) Text(preview.text, style = Type.body.copy(fontSize = 15.sp, lineHeight = 22.sp), color = Tokens.textDim)
            }
        }
    }
}

/** A single-pane page inside the TV safe area (the picture grid). */
@Composable
fun TvSafeColumn(title: String, subtitle: String? = null, content: @Composable BoxScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(Tokens.bg).padding(horizontal = TvKit.SafeHorizontal).padding(top = TvKit.SafeVertical)) {
        TvPageTitle(title, Modifier.padding(start = 26.dp))
        if (subtitle != null) Text(subtitle, style = Type.body.copy(fontSize = 15.sp), color = Tokens.textDim, modifier = Modifier.padding(start = 26.dp, top = 4.dp))
        Box(Modifier.fillMaxSize().padding(top = 14.dp), content = content)
    }
}

/** Soft fade at the top and bottom edge of a scrolling list. */
fun Modifier.fadingEdges(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val top = 14.dp.toPx() / size.height.coerceAtLeast(1f)
        val bottom = 40.dp.toPx() / size.height.coerceAtLeast(1f)
        drawRect(
            Brush.verticalGradient(0f to Color.Transparent, top to Color.Black, 1f - bottom to Color.Black, 1f to Color.Transparent),
            blendMode = BlendMode.DstIn,
        )
    }

/** KEYCODE_BOOKMARK → "Bookmark". */
fun keyName(code: Int): String = android.view.KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")
    .split('_').joinToString(" ") { it.lowercase().replaceFirstChar(Char::uppercase) }
