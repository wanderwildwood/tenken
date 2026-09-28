package com.wanderwildwood.tenken.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import com.wanderwildwood.tenken.R
import com.wanderwildwood.tenken.core.CodeKind
import com.wanderwildwood.tenken.core.Refusal
import com.wanderwildwood.tenken.core.TroubleCode
import com.wanderwildwood.tenken.obd.CarState
import com.wanderwildwood.tenken.obd.Cleared
import com.wanderwildwood.tenken.obd.Link
import kotlinx.coroutines.delay

/**
 * The car's trouble codes, each with what kind it is said in words, and the two things to do
 * about them: read them again, or clear them.
 *
 * Pending is spelled out rather than drawn. AndrOBD marked it with a small clock, and on
 * this panel a pending P2647 sat beside a stored P0139 looking like its equal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodesScreen(
    car: CarState,
    onClose: () -> Unit,
    onReadAgain: () -> Unit,
    onClear: () -> Unit,
) {
    var clearArmed by remember { mutableStateOf(false) }

    // An armed row disarms itself, so a stray tap leaves nothing live for whoever picks the
    // phone up next.
    LaunchedEffect(clearArmed) {
        if (clearArmed) {
            delay(4000)
            clearArmed = false
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBarMMD(
                title = { TextMMD(text = stringResource(R.string.home_codes)) },
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

            if (car.link != Link.READY) {
                item { Said(stringResource(R.string.not_talking)) }
                return@LazyColumnMMD
            }

            car.milOn?.let { on ->
                item {
                    Said(stringResource(if (on) R.string.codes_lamp_on else R.string.codes_lamp_off))
                }
            }

            when {
                car.readingCodes && car.codes.isEmpty() ->
                    item { Said(stringResource(R.string.codes_reading)) }
                car.codes.isEmpty() ->
                    item { Said(stringResource(R.string.codes_none)) }
                else -> car.codes.forEach { code ->
                    item { HorizontalDividerMMD() }
                    item { Code(code) }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
            item { HorizontalDividerMMD() }

            car.cleared?.let { cleared -> item { Said(outcome(cleared)) } }

            item {
                Door(
                    title = stringResource(if (car.readingCodes) R.string.codes_reading else R.string.codes_read_again),
                    onClick = if (car.readingCodes || car.clearing) null else onReadAgain,
                )
            }

            // Destructive last, and it asks in its own face rather than in a dialog. The
            // warning is the one thing a person clearing codes before an inspection needs
            // to know, and learns otherwise only at the inspection.
            item {
                Door(
                    title = when {
                        car.clearing -> stringResource(R.string.codes_clearing)
                        clearArmed -> stringResource(R.string.codes_clear_armed)
                        else -> stringResource(R.string.codes_clear)
                    },
                    bold = clearArmed,
                    onClick = if (car.clearing || car.readingCodes) {
                        null
                    } else {
                        {
                            if (clearArmed) {
                                clearArmed = false
                                onClear()
                            } else {
                                clearArmed = true
                            }
                        }
                    },
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun Code(code: TroubleCode) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        TextMMD(text = code.code, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (code.description.isNotBlank()) {
            TextMMD(text = code.description, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(2.dp))
        TextMMD(
            text = stringResource(
                when (code.kind) {
                    CodeKind.STORED -> R.string.codes_kind_stored
                    CodeKind.PENDING -> R.string.codes_kind_pending
                    CodeKind.PERMANENT -> R.string.codes_kind_permanent
                },
            ),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun outcome(cleared: Cleared): String = when (cleared) {
    Cleared.Done -> stringResource(R.string.codes_cleared)
    Cleared.NoAnswer -> stringResource(R.string.codes_no_answer)
    is Cleared.Refused -> when (cleared.reason) {
        Refusal.CONDITIONS_NOT_CORRECT -> stringResource(R.string.codes_refused_running)
        Refusal.NOT_SUPPORTED -> stringResource(R.string.codes_refused_unsupported)
        else -> stringResource(R.string.codes_refused_other, "0x%02X".format(cleared.reason))
    }
}
