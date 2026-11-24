# RIDER Android step 3 — attended ignition transition tests

This source overlay adds a bounded foreground monitor to the existing Android
Studio project. The single-read Android identity and wake-up-line path passed
user tests for both key positions in step 2. The new monitor, lifecycle controls
and file-sharing UI still require full Android build and live verification.

## Install the update

1. Stop RIDER in Android Studio. Keep a copy of the working step-2 source.
2. Extract this ZIP. Copy its **app** folder into the existing **RIDER** project
   folder, merging folders and replacing matching files. Preserve resources,
   theme files and Gradle configuration not included in this overlay.
3. Copy the entire supplied app folder: it includes a new provider entry in
   AndroidManifest.xml and res/xml/rider_file_paths.xml needed to share logs.
4. Select the Samsung and click Run. No new dependency or permission is required.
   Share the first compiler error if Android Studio reports one.

## First test: ON to OFF

1. Close MotoScan and Windows LX tools. Keep the bike parked with engine off.
2. Turn ignition on, open RIDER and select the LX.
3. Leave the direction set to **ON → OFF** and tap **Start ignition test**.
4. Keep this screen open. After two matching reads, the large card says:
   **IGNITION ON CONFIRMED — TURN THE KEY OFF NOW.** Turn the key off.
5. Keep the key off until the app confirms the off transition and stops.
6. Tap **Share last test log**. Use Android's share menu to send the JSONL file
   to this chat (or save it first and attach it here). Send that log for review.

If the test stops with pending, ambiguity, or another error, share the log instead
of repeatedly rerunning. There is no need to watch scrolling diagnostic JSON;
the card shows short state messages and the raw traffic is recorded in the file.

## Second test reference: OFF to ON

Do this after the first monitor result has been reviewed. Briefly turn ignition
on then off, so an off baseline can still be read before the network idles.
Select **OFF → ON**, tap **Start ignition test**, and wait for:
**IGNITION OFF CONFIRMED — TURN THE KEY ON NOW.** Turn it on with engine off.
The app should confirm one ignition-on transition and stop. Share that test log.
Starting already on does not pass as an off-to-on transition.

## Session behavior

- Up to 12 samples, with a five-second pause after each successful read. Command
  execution adds time. Failed access with successful protocol closure backs off
  up to 30 seconds; this is a bounded attempt count, not a fixed session duration.
- One host Bluetooth connection is kept open for the session. Adapter identity
  and supply voltage are read at the start; the voltage displayed is that initial
  snapshot. Every sample checks the cluster identity before reading the line and
  attempts ATPC to close the vehicle diagnostic protocol.
- There is no automatic host reconnect in this Android monitor. A failed or
  unsynchronized connection requiring review ends the session. The user can
  start a new attended test after reviewing its log.
- Two matching valid reads within the freshness window establish state. Initial
  state establishment never emits a new ignition edge. Missing or invalid access
  makes current ignition unknown and retains historical state separately in logs.
- Recovery to the same state is state_recovered. A difference across a gap is
  change_observed_after_gap; neither passes a requested ignition transition.
- Pending-only replies end the session before another diagnostic read. Conflicting
  or duplicate final replies, identity mismatch, malformed data, negative replies,
  failed cleanup, or an interrupted stream stop for review. The existing bounded
  pending-then-final response parser remains in use.
- Press **Stop test**, leave the activity, lock the phone, or rotate it to cancel
  the test. Leaving the foreground closes the socket and prevents further reads.
  The activity keeps the screen on while the attended operation is active.
- The two single-shot buttons remain available. A single observation does not
  establish ignition. The monitor attaches no garage, speech or external action.
- Active diagnostics may affect vehicle sleep. Sleep behavior and power impact
  remain unverified; this is not unattended monitoring.

## Logs and sharing

Logs are written in app-private diagnostics storage. Commands/replies, read
results, inferred events, UTC timestamps and the stop reason are recorded. Device
MAC, VIN and ECU serial number are not requested for the diagnostic log. No log
is sent by the app until the user explicitly chooses Share last test log.

While writing, the file has a .partial suffix. Only a closed, finalized .jsonl
file is offered for sharing. A failed or killed write cannot appear as a finished
log. Each file has a unique name and a size limit of 2 MB; previous logs are not
overwritten. After returning to the app, the latest finalized log remains
available. There is no automatic deletion or log upload.

FileProvider is not exported and exposes only the diagnostics folder through
explicit temporary URI read permission. Sharing uses Android's chooser and keeps
the .jsonl filename. No storage permission is requested.

## Verification

Kotlin 2.2.10 compiled the state engine and journal checks. The local checks cover
state confirmation, both transition directions, startup state, noise, staleness,
missing access, recovery, gaps, cancellation, bounded backoff, clear prompts,
JSON escaping, log completion, unique filenames, size limits and directory errors.
The 37 prior adapter/KOMBI protocol checks remain applicable; their protocol
source files are unchanged from the verified step-2 overlay.

Full Android UI build, socket cancellation, screen behavior and share-sheet
integration still need validation in Android Studio and on the S24.

References:
- https://developer.android.com/training/secure-file-sharing/share-file
- https://developer.android.com/reference/android/view/WindowManager.LayoutParams#FLAG_KEEP_SCREEN_ON
