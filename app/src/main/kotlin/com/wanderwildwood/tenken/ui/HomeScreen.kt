package com.wanderwildwood.tenken.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import com.wanderwildwood.tenken.PhoneState
import com.wanderwildwood.tenken.R
import com.wanderwildwood.tenken.obd.CarState
import com.wanderwildwood.tenken.obd.Failure
import com.wanderwildwood.tenken.obd.Link
import com.wanderwildwood.tenken.obd.Page

/**
 * Where the connection stands, the one thing to press next, and — once the car is
 * answering — the four things it can be asked for.
 *
 * The four are not shown until then. A row that opened onto "not talking to the car" would
 * be a press that tells you only what this screen already says.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    car: CarState,
    phone: PhoneState,
    onSettings: () -> Unit,
    onAllow: () -> Unit,
    onOpenBluetooth: () -> Unit,
    onChooseAdapter: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onOpen: (Page) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBarMMD(
                title = { TextMMD(text = stringResource(R.string.app_name)) },
                actions = { BarButton(Icons.Settings, stringResource(R.string.cd_settings), onSettings) },
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
            item { Connection(car, phone, onAllow, onOpenBluetooth, onChooseAdapter, onConnect, onDisconnect) }

            if (car.link == Link.READY) {
                item { Spacer(Modifier.height(12.dp)) }
                item { HorizontalDividerMMD() }
                item { Door(stringResource(R.string.home_codes)) { onOpen(Page.CODES) } }
                item { Door(stringResource(R.string.home_live)) { onOpen(Page.LIVE) } }
                item { Door(stringResource(R.string.home_freeze)) { onOpen(Page.FREEZE) } }
                item { Door(stringResource(R.string.home_info)) { onOpen(Page.INFO) } }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun Connection(
    car: CarState,
    phone: PhoneState,
    onAllow: () -> Unit,
    onOpenBluetooth: () -> Unit,
    onChooseAdapter: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val name = car.adapter ?: phone.adapterName

    // Each of these is the first thing in the way, and only the first is said. Three reasons
    // at once would leave the reader to work out which one to fix.
    when {
        !phone.permitted -> {
            Said(stringResource(R.string.home_no_permission))
            WideButton(stringResource(R.string.home_allow), onAllow)
        }
        !phone.bluetoothOn -> {
            Said(stringResource(R.string.home_bluetooth_off))
            WideButton(stringResource(R.string.home_open_bluetooth), onOpenBluetooth)
        }
        phone.adapter.isEmpty() -> {
            Said(stringResource(R.string.home_no_adapter))
            WideButton(stringResource(R.string.home_choose_adapter), onChooseAdapter)
        }
        car.link == Link.OFFLINE -> {
            Said(
                when (car.failure) {
                    Failure.COULD_NOT_CONNECT -> stringResource(R.string.home_failed, name)
                    Failure.LOST -> stringResource(R.string.home_lost, name)
                    null -> stringResource(R.string.home_not_connected, name)
                },
            )
            WideButton(stringResource(R.string.home_connect), onConnect)
        }
        else -> {
            Said(
                when {
                    car.link == Link.CONNECTING -> stringResource(R.string.home_connecting, name)
                    car.link == Link.READY -> stringResource(R.string.home_ready, name)
                    car.carSilent -> stringResource(R.string.home_silent, name)
                    else -> stringResource(R.string.home_asking, name)
                },
            )
            WideButton(stringResource(R.string.home_disconnect), onDisconnect)
        }
    }
}
