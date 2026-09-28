package com.wanderwildwood.tenken.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.text.TextMMD

/** A 48dp press in the top bar. */
@Composable
internal fun BarButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(48.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** A row that is pressed: a label, and a second line only where it tells you something. */
@Composable
internal fun Door(title: String, note: String? = null, bold: Boolean = false, onClick: (() -> Unit)?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(vertical = 14.dp),
    ) {
        TextMMD(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else null,
        )
        if (note != null) TextMMD(text = note, style = MaterialTheme.typography.labelSmall)
    }
}

/** A plain sentence about where things stand. */
@Composable
internal fun Said(text: String, modifier: Modifier = Modifier) {
    TextMMD(text = text, style = MaterialTheme.typography.bodySmall, modifier = modifier.padding(vertical = 10.dp))
}

@Composable
internal fun WideButton(text: String, onClick: () -> Unit) {
    OutlinedButtonMMD(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) { TextMMD(text = text, style = MaterialTheme.typography.bodySmall) }
}
