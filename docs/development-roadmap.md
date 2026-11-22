# RIDER development roadmap

The first complete journey is leaving home on the 2014 BMW R1200RT. While the motorcycle is parked at home, key-on prompts RIDER to ask, “Want me to open the garage?” The rider can answer aloud or use Open and Later on the X4 Pro. The S24 Ultra coordinates the interaction.

## Verified baseline

- The X4 Pro and S24 Ultra exchanged BLE writes and notifications using nRF Connect. The image-based portrait prototype displayed Parked Action, and its Go tap reached the phone as `CONFIRM` when launched with `session create python display0 /rider/rider_ble_two_way.py`.
- The existing phone-to-ESP32 garage command was tested. A command acknowledgment establishes receipt. Door position requires a separate observation or sensor.
- On the 2014 R1200RT (K52), Alex confirmed in MotoScan via OBDLink LX that KOMBI Live Data → Wake-up line follows an `off → on → off` ignition cycle with the engine stopped. The Bosch BMS-X also returned live temperatures with ignition on. KOMBI is the primary key-on candidate. Direct read-only access from RIDER on Android and one event per cycle remain to be verified.

## Phases and exit criteria

| Phase | Build | Exit criteria |
| --- | --- | --- |
| 1. Detect key-on | Collect read-only observations for key off, key on with engine stopped, and key off again. Compare adapter connectivity with ECU responsiveness. | Repeated cycles identify a reliable pre-start signal and yield one key-on event per cycle. |
| 2. Connect the phone and display | Use the existing BLE baseline and the Figma Parked Home and Parked Action designs. Start with a simulated key-on on Android. | Parked Home shows “Ready when you are,” Garage, and Memories. A simulated key-on while parked at home shows Open and Later. Both choices reach the phone. |
| 3. Add voice | Route the spoken question through the helmet headset and open a short listening window after speech completes. Use the physical Home button to start rider-initiated exchanges. Keep relay actuation simulated during this phase. | The rider can answer “Open the garage” or “Later” without a button press. The interaction handles silence and unclear replies without choosing Open. |
| 4. Integrate the departure | Combine real key-on, parked-at-home checks, Parked Action, voice, and the existing opener command. | RIDER asks once per ignition cycle. A deliberate Open choice triggers the opener once. Later, silence, and unclear replies leave the door as it is. The rider can see the door before authorizing the existing toggle command. |
| 5. Verify daily use | Test with the phone locked, the display waking, the headset reconnecting, and the bike starting to move. Transition to the Figma Presence screen when riding. | The routine works without PuTTY, WinSCP, or nRF Connect. Connection loss and retries do not repeat an opener command. |
| 6. Extend to maintenance and memory | Add the six-month tire reminder and phone photo checklist, then connect Memories to saved rides and observations. | RIDER can ask about a due check, handle Start check and Later, and record the completion date. |

Phase 1 now moves from the confirmed MotoScan observation to a direct Android read-only probe. Check fresh KOMBI readings across repeated key cycles and treat a timeout or disconnect as unknown. Phase 2 can proceed against simulated input while that evidence is collected.
