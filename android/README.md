# Android prototypes

These are source overlays for an existing Android Studio Empty Activity / Compose
project, package com.agsiqueira.rider, minimum SDK 31. They are not standalone
Gradle projects. The supplied project already built successfully on the user's
Windows/S24 setup; its dependencies and theme should be preserved.

- bootstrap: paired-device selection and LX identity/voltage check. User-tested
  on the S24/LX; the app reported a verified adapter and disconnection.
- step2: adds a single read-only KOMBI identity/wake-up-line check. Pure Kotlin
  tests pass; Android diagnostic hardware validation remains pending.
- tests: standalone synthetic Kotlin protocol checks; no Android runtime needed.

Each overlay has INSTALL.md and app/src/main files to merge into the existing
project. No firmware flashing or new cloud service is involved. Paired-device
access uses BLUETOOTH_CONNECT and a secure RFCOMM socket with the standard SPP UUID.

References:
- https://developer.android.com/develop/connectivity/bluetooth/bt-permissions
- https://developer.android.com/develop/connectivity/bluetooth/connect-bluetooth-devices
