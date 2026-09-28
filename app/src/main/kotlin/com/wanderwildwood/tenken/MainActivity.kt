package com.wanderwildwood.tenken

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mudita.mmd.ThemeMMD
import com.wanderwildwood.tenken.obd.Page
import com.wanderwildwood.tenken.ui.AdapterDialog
import com.wanderwildwood.tenken.ui.CodesScreen
import com.wanderwildwood.tenken.ui.HomeScreen
import com.wanderwildwood.tenken.ui.ReadingsScreen
import com.wanderwildwood.tenken.ui.SettingsScreen
import com.wanderwildwood.tenken.ui.monochrome

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // The screen stays on while this app is in front. It is read in a car with the
        // engine running and both hands busy, often while revving to see a number move, and
        // a screen that has gone dark by the time you look up again is a fault there.
        // The window flag, not a wake lock: no permission, and Android drops it by itself
        // the moment the window loses focus.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            ThemeMMD(colorScheme = monochrome) {
                CarCheck()
            }
        }
    }
}

private enum class Screen { HOME, PAGE, SETTINGS }

@Composable
private fun CarCheck(viewModel: CarViewModel = viewModel()) {
    val car by viewModel.car.state.collectAsStateWithLifecycle()
    val phone by viewModel.phone.collectAsStateWithLifecycle()
    var screen by remember { mutableStateOf(Screen.HOME) }
    var page by remember { mutableStateOf(Page.NONE) }
    var choosing by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.refresh() }
    val openBluetooth = {
        runCatching { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
        Unit
    }

    // Stop asking the car for anything while nobody is looking, and look again at the radio,
    // the permission and the pairings on the way back, since any of them can change behind
    // the app's back.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    viewModel.refresh()
                    viewModel.car.resume()
                }
                Lifecycle.Event.ON_PAUSE -> viewModel.car.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val home = {
        screen = Screen.HOME
        page = Page.NONE
        viewModel.open(Page.NONE)
    }
    BackHandler(enabled = screen != Screen.HOME, onBack = home)

    when (screen) {
        Screen.HOME -> HomeScreen(
            car = car,
            phone = phone,
            onSettings = { screen = Screen.SETTINGS },
            onAllow = { ask.launch(Manifest.permission.BLUETOOTH_CONNECT) },
            onOpenBluetooth = openBluetooth,
            onChooseAdapter = { viewModel.refresh(); choosing = true },
            onConnect = viewModel::connect,
            onDisconnect = viewModel::disconnect,
            onOpen = {
                page = it
                screen = Screen.PAGE
                viewModel.open(it)
            },
        )

        Screen.PAGE -> if (page == Page.CODES) {
            CodesScreen(
                car = car,
                onClose = home,
                onReadAgain = viewModel.car::readCodesAgain,
                onClear = viewModel.car::clear,
            )
        } else {
            ReadingsScreen(car = car, page = page, onClose = home)
        }

        Screen.SETTINGS -> SettingsScreen(
            phone = phone,
            onClose = { screen = Screen.HOME },
            onChooseAdapter = { viewModel.refresh(); choosing = true },
            onUnits = viewModel::toggleUnits,
        )
    }

    if (choosing) {
        AdapterDialog(
            phone = phone,
            onChoose = {
                viewModel.choose(it)
                choosing = false
            },
            onOpenBluetooth = openBluetooth,
            onDismiss = { choosing = false },
        )
    }
}
