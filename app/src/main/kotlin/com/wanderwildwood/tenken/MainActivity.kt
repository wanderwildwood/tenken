package com.wanderwildwood.tenken

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.core.app.ActivityCompat
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
import com.wanderwildwood.tenken.ui.houseTypography
import com.wanderwildwood.tenken.ui.monochrome
import com.wanderwildwood.tenken.ui.ArmedScope
import com.wanderwildwood.tenken.ui.DarkSelection

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
            ThemeMMD(colorScheme = monochrome, typography = houseTypography) { DarkSelection {
                ArmedScope { CarCheck() }
            } }
        }
    }
}

private enum class Screen { HOME, PAGE, SETTINGS }

@Composable
private fun CarCheck(viewModel: CarViewModel = viewModel()) {
    val car by viewModel.car.state.collectAsStateWithLifecycle()
    val phone by viewModel.phone.collectAsStateWithLifecycle()
    // Saveable, so a window built again opens where it was, on the page the car is still asked for.
    var screen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(Screen.HOME) }
    var page by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(Page.NONE) }
    var choosing by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // What Android said before asking, and when it was asked: a prompt only dismissed also
    // comes back with no rationale, but not at once, and not after a "no" that had one.
    val asked = remember { longArrayOf(0L, 0L) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val activity = context as? ComponentActivity
        val rationale = activity != null &&
            ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.BLUETOOTH_CONNECT)
        val hadRationale = asked[0] == 1L
        val atOnce = System.currentTimeMillis() - asked[1] < 500
        viewModel.permissionAnswered(granted, rationale || !(hadRationale || atOnce))
    }
    val allow = {
        // Once Android has stopped asking, the launcher answers "no" at once and shows
        // nothing, which reads as a button that does nothing. Go to the settings page instead.
        if (viewModel.phone.value.blocked) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
                )
            }
        } else {
            val activity = context as? ComponentActivity
            asked[0] = if (activity != null &&
                ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.BLUETOOTH_CONNECT)) 1L else 0L
            asked[1] = System.currentTimeMillis()
            ask.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
        Unit
    }
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
            onAllow = allow,
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
            onAllow = allow,
            onOpenBluetooth = openBluetooth,
            onDismiss = { choosing = false },
        )
    }
}
