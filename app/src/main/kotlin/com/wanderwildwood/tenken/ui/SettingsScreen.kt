package com.wanderwildwood.tenken.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import com.wanderwildwood.tenken.Paired
import com.wanderwildwood.tenken.PhoneState
import com.wanderwildwood.tenken.R

/**
 * The two things there are to set: which adapter, and which units.
 *
 * Units cycle in place, since both values fit on the row already. The adapter is a picker,
 * because its list is whatever this phone happens to be paired with.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    phone: PhoneState,
    onClose: () -> Unit,
    onChooseAdapter: () -> Unit,
    onUnits: () -> Unit,
) {
    var aboutOpen by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBarMMD(
                title = { TextMMD(text = stringResource(R.string.settings_title)) },
                navigationIcon = { BarButton(Icons.Close, stringResource(R.string.cd_close), onClose) },
                actions = { BarButton(Icons.Info, stringResource(R.string.cd_about)) { aboutOpen = true } },
            )
        },
    ) { contentPadding ->
        LazyColumnMMD(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 20.dp),
        ) {
            item { Spacer(Modifier.height(12.dp)) }
            item {
                Door(
                    title = stringResource(R.string.settings_adapter),
                    note = phone.adapterName.ifEmpty { stringResource(R.string.settings_not_chosen) },
                    onClick = onChooseAdapter,
                )
            }
            item {
                Door(
                    title = stringResource(R.string.settings_units),
                    note = stringResource(if (phone.usUnits) R.string.settings_units_us else R.string.settings_units_metric),
                    onClick = onUnits,
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (aboutOpen) AboutDialog(onDismiss = { aboutOpen = false })
}

/**
 * The phone's paired devices, adapters first.
 *
 * Only what is already paired. Pairing is left to the phone's own settings, which does it
 * properly and asks for the PIN; doing it here would mean scanning, and scanning on Android
 * 12 means asking for a permission this app has no other use for.
 */
@Composable
fun AdapterDialog(
    phone: PhoneState,
    onChoose: (Paired) -> Unit,
    onOpenBluetooth: () -> Unit,
    onDismiss: () -> Unit,
) {
    EInkDialog(onDismiss = onDismiss) {
        TextMMD(text = stringResource(R.string.adapter_title), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(10.dp))

        if (phone.paired.isEmpty()) {
            TextMMD(text = stringResource(R.string.adapter_none), style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(14.dp))
            WideButton(stringResource(R.string.home_open_bluetooth), onOpenBluetooth)
        } else {
            // A phone can be paired with a great many things, so the list is MMD's own and
            // steps rather than running off the foot of the dialog.
            LazyColumnMMD(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                phone.paired.forEach { device ->
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onChoose(device) }
                                .padding(vertical = 12.dp),
                        ) {
                            TextMMD(
                                text = if (device.address == phone.adapter) {
                                    stringResource(R.string.adapter_in_use, device.name)
                                } else {
                                    device.name
                                },
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        WideButton(stringResource(R.string.adapter_close), onDismiss)
    }
}
