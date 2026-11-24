# RIDER Android step 1 — adapter connection check

This is an overlay for the existing Android Studio project, package
com.agsiqueira.rider, minimum SDK 31. It uses the project's existing Compose
and activity dependencies. The supplied app/build.gradle.kts and version catalog
need no edits. It is not a complete standalone Gradle project.

## Install the three source files

1. Stop the running app in Android Studio.
2. Keep backup copies of the existing MainActivity.kt and AndroidManifest.xml
   outside app/src before replacing them.
3. Extract this package. Copy the included **app** folder into the existing
   **RIDER** project folder:
   %USERPROFILE%\AndroidStudioProjects\RIDER
   Merge the folders and replace the matching files. Keep existing resources,
   generated theme files and all other project files.

Exact destinations relative to the existing RIDER project:

| Included file | Destination |
|---|---|
| AndroidManifest.xml | app/src/main/AndroidManifest.xml |
| MainActivity.kt | app/src/main/java/com/agsiqueira/rider/MainActivity.kt |
| AdapterProtocol.kt | app/src/main/java/com/agsiqueira/rider/AdapterProtocol.kt |

4. Back in Android Studio, select the Samsung and click Run. The first screen
   should say RIDER / Connection check. If the build fails, share the first
   compiler error from the Build window rather than changing dependency versions.

## First phone test

1. Turn Bluetooth on. The LX must be powered and reachable. Close MotoScan and
   exit all Windows LX tools so another client is not using the adapter.
2. Pair the LX in Android's Bluetooth settings if it is not already paired.
3. Open RIDER and tap Allow Bluetooth access. Grant Nearby devices access.
4. Tap Refresh paired devices, select the LX, then Check LX connection.
5. Expected: STN1155 firmware, ELM identity, supply voltage and Adapter verified.
   The check disconnects when finished. Share a screenshot of the result.

This version lists already paired devices; it does not scan. It requests only
BLUETOOTH_CONNECT. Pairing and Bluetooth enablement happen in system settings.
Permission denial, Bluetooth off, no paired devices, timeout and cancellation
have visible messages. Discovery started by another app may interfere with
connection; leave the system Bluetooth settings screen before testing.

Only ATI, STI and ATRV are available to the transport. No bike diagnostics or
ignition detection are implemented in this step. Supply voltage is not decoded
as ignition. The motorcycle ignition may remain off if the LX remains powered.

The RFCOMM connection uses the standard SPP service UUID. Blocking connection
and reads run on a worker thread, with a 12-second connection deadline and a
four-second deadline per query. Timeout closes the socket. Leaving the app or
pressing Cancel closes the socket and invalidates late results. A successful
result also closes the connection; there is no automatic reconnect or background
service in step 1. Device selection is not persisted, and no diagnostic log is
uploaded or written by the app.

## Verification limits

The protocol component has standalone Kotlin checks for ELM echoes, expected
firmware, voltage, error replies, EOF, response size and the query allowlist.
A complete Android build and real S24/LX run must be verified in Android Studio;
the development workspace does not have this project's Android SDK/toolchain.

References:
- https://developer.android.com/develop/connectivity/bluetooth/bt-permissions
- https://developer.android.com/develop/connectivity/bluetooth/connect-bluetooth-devices
