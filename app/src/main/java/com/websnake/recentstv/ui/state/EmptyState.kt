package com.websnake.recentstv.ui.state

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.websnake.recentstv.ui.overlay.KeyHint
import com.websnake.recentstv.ui.overlay.SettingsButton
import com.websnake.recentstv.ui.theme.Tokens
import com.websnake.recentstv.ui.theme.Type

/**
 * Surface 4 — no recent apps and no pins. The same frame as the overlay: title at the top left, the Settings button
 * at the top centre (the only thing to press, so it is focused), key hints at the bottom.
 */
@Composable
fun EmptyState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier.align(Alignment.TopStart).padding(start = 48.dp, top = 36.dp).height(38.dp),
            contentAlignment = Alignment.CenterStart,
        ) { Text("Recent apps", style = Type.title, color = Tokens.text, maxLines = 1) }
        Box(Modifier.align(Alignment.TopCenter).padding(top = 36.dp)) { SettingsButton(true) }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // dashed-square-in-circle glyph
            Box(
                Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .border(1.5.dp, Tokens.text.copy(alpha = 0.3f), RoundedCornerShape(percent = 50)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(23.dp)
                        .border(1.5.dp, Tokens.text.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                )
            }
            Text("No recent apps", style = Type.title, color = Tokens.text)
            Text(
                "Apps you open will appear here.",
                style = Type.body, color = Tokens.textDim, textAlign = TextAlign.Center,
            )
        }
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 34.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { KeyHint("OK", "Customize"); KeyHint("Back", "Close") }
    }
}
