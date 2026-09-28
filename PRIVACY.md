# Privacy

Car Check reads what your car says about itself and never sends it anywhere, because it has
no way to send anything anywhere.

That is the whole policy. The rest of this page is the evidence for it, because a privacy
policy that cannot be checked is just a promise.

## One permission

`app/src/main/AndroidManifest.xml` declares exactly one:

```
android.permission.BLUETOOTH_CONNECT
```

That is what Android 12 asks of an app that talks to a device already paired with the
phone, and the adapter is exactly that. Pairing is done in the phone's own settings, so the
app never scans for devices and never asks for location, which scanning would need.

There is **no `INTERNET` permission**. Without it Android will not let the app open a
network connection, so nothing the car says can leave the phone even by accident, and no
promise from me is load-bearing.

## What happens to what the car says

It is turned into the words and numbers on the screen and then thrown away. The app keeps
no history, no log of codes or readings, and no file of any kind holding them — including
the car's identity number, which the vehicle information page shows and does not store.

The only things written to storage are the adapter you chose (its Bluetooth name and
address) and whether you want metric or US units, in `SharedPreferences`; see
`CarViewModel.kt`.

## No analytics

No crash reporting, no telemetry, no advertising identifier, no third-party SDK of any
kind. The dependency list in `app/build.gradle.kts` is AndroidX, Jetpack Compose, Mudita's
MMD component library and AndrOBD's protocol library, which is in this repository as
source.

## Checking any of this for yourself

The source is here in full. If you would rather not read it:

```
aapt2 dump badging tenken.apk | grep uses-permission
```

That prints every permission the built app actually carries. It prints two lines:

```
uses-permission: name='android.permission.BLUETOOTH_CONNECT'
uses-permission: name='com.wanderwildwood.tenken.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'
```

The first is the one described above. The second is not mine: AndroidX defines it
automatically for every app, it is signature-level and scoped to this package so only this
app can hold it, and it exists so a runtime-registered broadcast receiver is not exported
to other apps. It grants access to nothing.

There is no `INTERNET` in that list, which is the claim above without having to trust me.
