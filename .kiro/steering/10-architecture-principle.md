---
inclusion: always
---

# Architecture principle

> Split out of the former `architecture.md`. This file records the long-lived architecture and native-UI principle for the ThirdSpace project; related concerns live in the sibling `NN-*.md` steering files.

- iOS UI is fully native SwiftUI (in `iosApp/iosApp/`). There is no Compose-Multiplatform UI.
- The `shared` module is Kotlin Multiplatform LOGIC/DATA ONLY — no Compose dependency. The Android target remains, but `androidApp` is currently a non-functional placeholder; a native Android UI comes in a later phase. Business/shared logic stays in the shared module so Android can adopt it later.
