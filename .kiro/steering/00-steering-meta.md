---
inclusion: always
---

# Steering Conventions

Keep steering documents small and focused: one concern per file, overview-friendly. When a steering doc grows long, split it into multiple focused files under `.kiro/steering/` (flat, NN-prefixed for order; `inclusion: always` unless a doc is genuinely file-scoped). Do not let a single steering file become very long. Existing content must be preserved when splitting.

## Current steering file set

- `00-steering-meta.md` — this file; steering conventions + the map below.
- `10-architecture-principle.md` — native SwiftUI iOS + KMP logic/data-only `shared` principle (was `architecture.md` → "Architecture principle").
- `20-module-boundaries.md` — module/package boundaries + the flat Swift↔Kotlin `FeedBridge` boundary (was → "Module & package boundaries").
- `30-native-ui-conventions.md` — native UI conventions, tokens, sheet pattern (was → "Native UI conventions").
- `35-ui-components.md` — reusable token-styled SwiftUI components (Workstream-B convention).
- `40-i18n.md` — shared i18n layer + live language switching (was → "Internationalization (i18n)").
- `50-navigation.md` — `TabView` shell, push vs. sheet navigation (was → "Navigation").
- `60-map-mapbox.md` — native Mapbox v11 map engine + interactions (was → "Map (Mapbox)").
- `70-backend-supabase.md` — Supabase backend: auth, feed, profile, follow, collections, caching (was → "Backend (Supabase)").
- `80-spots-offline-seam.md` — `Spot`/`SpotCategory` + cache-first offline seam (was → "Spots & offline seam").
- `90-deliberate-exclusions.md` — current deferred/out-of-scope features (was → "Deliberate exclusions (current state)").
- `95-verification.md` — architecture-level verification pointer; complements `verification-strategy.md`.
- `verification-strategy.md` — the binding, proportional technical-verification matrix (separate pre-existing doc; not part of the split).

The files `10-` through `95-` were split out of a single former long `architecture.md`, with content preserved verbatim.
