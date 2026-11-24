# RIDER Android step 2 — one read-only KOMBI observation

The S24/LX adapter identity check passed in step 1. This overlay adds the fixed
KOMBI identity and wake-up-line reads previously tested by the Windows probe.
The new Android diagnostic path has not yet been tested on the motorcycle.

## Install

1. Stop RIDER in Android Studio. Back up your current MainActivity.kt and
   AdapterProtocol.kt outside the project's app/src directory.
2. Extract this ZIP. Copy its **app** folder into the existing Android Studio
   **RIDER** project folder. Merge folders and replace matching files. Preserve
   all other files, resources, theme and Gradle configuration.
3. The source directory app/src/main/java/com/agsiqueira/rider should contain
   MainActivity.kt, AdapterProtocol.kt and the new KombiProtocol.kt. The manifest
   requests only BLUETOOTH_CONNECT, as in step 1. No new dependencies are needed.
4. Select the Samsung in Android Studio and click Run. Share the first build
   error if any; do not change dependency versions to work around one.

## First attended bike check

1. Close MotoScan and stop all Windows LX tools. Keep the bike parked.
2. Turn the ignition on and leave the engine off.
3. Open RIDER, refresh paired devices if necessary and select the LX.
4. Tap **Read bike status once**. Do not tap repeatedly while it is running.
5. Expected result: cluster 0F8800, wake-up line ON, status decoded, diagnostic
   protocol closed yes. The app disconnects after the check. Send a screenshot.

This is a single observation, so the screen keeps ignition awaiting confirmation.
The next stage can add repeated-read confirmation after this Android path passes.
Supply voltage is not treated as ignition. Do not run an off or sleep test until
the first on-state response is reviewed.

For a failed check, tap **Copy diagnostic details** and paste the contents into
the chat. This explicitly copies commands and adapter replies to the clipboard;
it does not upload anything. The app does not request VIN or ECU serial number.

## Behavior

Adapter check reads ATI, STI and ATRV. Bike check first does those adapter reads,
then checks each fixed setup reply, reads the expected KOMBI identity, and only
if it matches reads E11C. Only two fixed UDS service-22 requests are exposed:

- 60 03 22 F1 50 00 00 00 — cluster definition identity.
- 60 03 22 E1 1C 00 00 00 — wake-up line.

Each command has a four-second timeout; connect has a twelve-second timeout.
There is no polling, service reset, fault clearing, actuator command, security
access, ECU session switching or automatic retry. Read-only diagnostic traffic
can still affect the vehicle network's sleep behavior; power impact is untested.

Pending-only, unavailable, negative, invalid or ambiguous data remain unknown.
At most eight matching pending single frames may precede one final frame.
Duplicate final replies stay ambiguous. Responses validate source, recipient,
length, service, identifier and enum. Cleanup attempts ATPC only when the stream
has a complete prompt and the activity is still active. A closure failure prevents
a valid wake-up-line display. Cancel or leaving the activity closes the socket
and invalidates late UI results. The Bluetooth connection also closes after a
successful result. No background monitoring or garage/voice action is attached.

## Verification

The pure Kotlin transport and KOMBI logic compiled using Kotlin 2.2.10 and passed
13 adapter checks plus 24 KOMBI checks. Tests use synthetic replies covering on,
off, identity mismatch, pending, duplicate finals, wrong source/recipient/DID,
invalid values, negative replies, no data, CAN errors, setup failure, interrupted
streams and failed cleanup. Full Android build and hardware validation remain
on the user's Android Studio/S24 setup.
