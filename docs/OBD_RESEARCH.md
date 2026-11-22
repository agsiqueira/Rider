# EdiabasLib motorcycle support and read-only KOMBI prototype

## Implementation sources

EdiabasLib commit 8c002ee543fcfb6ed35e0f77295d4a851e063f1c includes explicit
motorcycle detection paths for BN2000 and BN2020. Detection is opt-in in Deep
OBD. Source references D_MRKOMB and G_MRKOMB; these are diagnostic groups,
not universal names for every cluster variant. RulesInfo.cs includes Preh-KOMBI.

The published X_KOMBI metadata identifies this read:

| Field | Value |
|---|---|
| Job | STATUS_LESEN |
| Arguments | ARG;WECKLEITUNG_DIGITAL_MR |
| Identifier | E11C |
| Result | STAT_WECKLEITUNG_DIGITAL_EIN |
| Service | 22, ReadDataByIdentifier |
| Value | unsigned byte, 0/1, identity scaling |

The executable X_KOMBI.PRG was not obtained or executed. The small Python
prototype uses published read metadata and source-derived single-frame addressing
rather than integrating the EdiabasLib interpreter. The ECU identity gate and
fixed diagnostic profile are in live_kombi.py. No identifier scanning, coding,
reset, security access, fault clearing, or actuator services are exposed.

## Transport

EdiabasLib EdElmInterface.Elm327CanSender constructs CAN ID 0x600 | sourceAddr,
prepends target address and a single-frame length, and sends the UDS payload.
Its receiver validates source, recipient and length. The prototype implements
only the single-frame subset with explicit addressing, temporary protocol
selection and automatic framing/flow control disabled. All configuration commands
are checked before vehicle reads. ECU identity is read before wake-up line.

A response-pending payload 7F 22 78 may precede the final response. The parser
accepts at most eight pending frames and one final frame; it rejects wrong
senders, wrong recipients, malformed lengths, conflicting final replies and
pending for another service. Pending without final data remains unknown.
No extra request or unbounded wait is introduced.

## Validation and operating limits

See [the public validation summary](LX_LIVE_VALIDATION.md) and
[Windows attended-test instructions](../tools/lx_probe/WINDOWS_TEST.md).
Original live logs are not published. Regression fixtures are synthetic examples
of the observed protocol shapes. Live diagnostic requests are read-only, but
active polling may affect network sleep behavior. A timeout or CAN ERROR never
means ignition off. Ignition is separately inferred from validated line reads.

## Sources

- [Motorcycle detection references](https://github.com/uholeschak/ediabaslib/blob/8c002ee543fcfb6ed35e0f77295d4a851e063f1c/EdiabasLib/BmwFileReader/DetectVehicleBmwBase.cs)
- [ELM/STN transport](https://github.com/uholeschak/ediabaslib/blob/8c002ee543fcfb6ed35e0f77295d4a851e063f1c/EdiabasLib/EdiabasLib/EdElmInterface.cs)
- [Response-pending handling](https://github.com/uholeschak/ediabaslib/blob/8c002ee543fcfb6ed35e0f77295d4a851e063f1c/EdiabasLib/EdiabasLib/EdInterfaceObd.cs)
- [Adapter support matrix](https://github.com/uholeschak/ediabaslib/blob/8c002ee543fcfb6ed35e0f77295d4a851e063f1c/docs/AdapterTypes.md)
- [Published X_KOMBI tables](https://emdzej.github.io/ediabasx-docs-sgbd/sgbd/x_kombi/#sg_funktionen)
- [Pinned table source](https://github.com/emdzej/ediabasx-docs-sgbd/blob/b644de8fbfbb4b207f57794e3c7894dc1dc58627/docs/sgbd/x_kombi.md)
- Secondary mapping lead: [community identification index](https://www.bimmerguide.de/prg-identifikation-tabelle/).
- [OBDLink programming manual](https://www.scantool.net/scantool/downloads/682/obdlink_frpm_f.pdf)
