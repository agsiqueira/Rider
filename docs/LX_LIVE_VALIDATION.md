# Read-only LX / KOMBI validation summary

This public summary records tested behavior. Original diagnostic logs and vehicle
identification screenshots remain outside the repository. Test fixtures are
synthetic; the repository contains no original live-monitor captures.

## Profile and scope

The prototype was tested on one BMW R1200RT K52 using an OBDLink LX and a Windows
Bluetooth serial port. This does not establish support for other vehicles,
firmware versions, Windows hosts, or unattended operation. The code's
profile_validation=user_tested_single_bike describes this limited history,
not a guarantee that each new run is validated.

## User-reported hardware observations

| Attended test | Observed result |
|---|---|
| Key on, engine off | Wake-up line on |
| Key off shortly afterward | Wake-up line off |
| Key on again | Wake-up line on |
| Key off after longer idle | CAN ERROR; state unknown |
| Key on after idle | Communication recovered; wake-up line on |
| Foreground on-to-off monitor | Two on reads established on; two off reads emitted ignition_off; monitor stopped automatically |

All four reads in the successful foreground on-to-off run closed the diagnostic
protocol successfully. That run had no reported host or CAN errors.

Earlier monitoring attempts exposed a Windows COM-open timeout and a valid
UDS response-pending message preceding the final read response. The monitor now
reuses its host serial handle between samples, closes the diagnostic protocol
after each read, and accepts a bounded pending-then-final sequence. Raw data,
unknown state, and last-confirmed state remain separate.

## Transition rules

Two successive valid reads within the freshness window are required to confirm
state. Initial state establishment is not an ignition edge. Missing access
immediately makes current state unknown, retaining last-confirmed state only as
history. Recovery to the same state is state_recovered; a difference after a
communication gap is change_observed_after_gap. Neither becomes ignition_on.
Only a directly observed confirmed change emits ignition_on or ignition_off.

## Pending verification

The dedicated off-to-on mode has synthetic local tests only until its next
hardware run. Starting already on cannot pass; a change across a missing-data
interval cannot pass. Sleep state, wake effects, power draw, engine cranking,
long-term reliability, Android behavior, and background operation remain
unverified. There are no garage, voice, or external actions attached to events.
