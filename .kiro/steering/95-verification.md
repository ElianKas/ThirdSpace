---
inclusion: always
---

# Verification

> Split out of the former `architecture.md`. This section complements the separate, more detailed `verification-strategy.md` steering doc (which governs the binding, proportional technical-verification matrix); this file is the short architecture-level pointer.

Automated/technical verification is governed by `verification-strategy.md`. iOS simulator Debug builds ARE runnable in this environment via `xcodebuild` and should be used to verify iOS/Swift changes; visual/layout QA remains the developer's responsibility. Shared backend logic (auth mapping, caching/queue) is unit-tested on both targets — including an in-memory SQLite round-trip on the Android host target — while real Supabase round-trips require keys + migrations + a logged-in session.
