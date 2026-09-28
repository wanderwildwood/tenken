package com.wanderwildwood.tenken.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import com.wanderwildwood.tenken.R
import com.wanderwildwood.tenken.obd.CarState
import com.wanderwildwood.tenken.obd.Link
import com.wanderwildwood.tenken.obd.Page
import com.wanderwildwood.tenken.obd.Reading
import kotlinx.coroutines.delay

/**
 * A page of numbers from the car: what the engine is doing now, what it was doing when its
 * code was set, or what the car says about itself.
 *
 * Numbers only, no gauges and no charts. A needle that moves is a smear on this panel; a
 * number that changes once a second is a small repaint that can be read.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingsScreen(car: CarState, page: Page, onClose: () -> Unit) {
    // Until the car has had a few seconds to answer, an empty page means "not yet" rather
    // than "none", and says so.
    var patient by remember(page) { mutableStateOf(true) }
    LaunchedEffect(page) {
        delay(5000)
        patient = false
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBarMMD(
                title = { TextMMD(text = stringResource(titleOf(page))) },
                navigationIcon = { BarButton(Icons.Close, stringResource(R.string.cd_close), onClose) },
            )
        },
    ) { contentPadding ->
        LazyColumnMMD(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 20.dp),
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            when {
                car.link != Link.READY -> item { Said(stringResource(R.string.not_talking)) }
                car.readings.isEmpty() && patient -> item { Said(stringResource(R.string.readings_waiting)) }
                car.readings.isEmpty() -> item { Said(stringResource(emptyOf(page))) }
                else -> {
                    if (page == Page.FREEZE) item { Said(stringResource(R.string.freeze_note)) }
                    car.readings.forEachIndexed { index, reading ->
                        if (index > 0) item { HorizontalDividerMMD() }
                        item { Line(reading) }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/**
 * A label on the left, the number and its unit on the right — or, where what is read is a
 * phrase rather than a number, the label above it. The readiness monitors answer in two
 * lines of words, and set beside the label they squeezed it to a letter a line.
 */
@Composable
private fun Line(reading: Reading) {
    if ('\n' in reading.value || reading.value.length > 12) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            TextMMD(text = reading.label, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(2.dp))
            TextMMD(
                text = listOf(reading.value, reading.unit).filter { it.isNotBlank() }.joinToString(" "),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        TextMMD(
            text = reading.label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f).padding(end = 12.dp),
        )
        Row(verticalAlignment = Alignment.Bottom) {
            TextMMD(text = reading.value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            if (reading.unit.isNotBlank()) {
                Spacer(Modifier.size(5.dp))
                TextMMD(text = reading.unit, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun titleOf(page: Page) = when (page) {
    Page.FREEZE -> R.string.home_freeze
    Page.INFO -> R.string.home_info
    else -> R.string.home_live
}

private fun emptyOf(page: Page) = when (page) {
    Page.FREEZE -> R.string.freeze_none
    Page.INFO -> R.string.info_none
    else -> R.string.live_none
}
