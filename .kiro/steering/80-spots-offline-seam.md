---
inclusion: always
---

# Spots & offline seam

> Split out of the former `architecture.md`.

- `Spot` (commonMain) carries `lat`/`lng` (Double) and remains extensible for later fields (e.g. `description`, photo).
- `SpotCategory` (commonMain) is the single source of truth for the fixed category list, each category's map-marker color (`#RRGGBB`), and the Mapbox maki/POI-class → category mapping. Category LABELS are dynamic content and are NOT part of the i18n `Strings` layer (consistent with the existing not-translated convention for spot categories).
- The offline seam is now IMPLEMENTED. `SpotRepository` (commonMain) is backed by a `CachingSpotRepository` — the `Spots` provider builds it by default; `InMemorySpotRepository` remains a fallback/test impl. `CachingSpotRepository` composes a SQLDelight local cache (`cachedSpots` + a `pendingSpotInserts` outbound queue) with a `SupabaseSpotRepository` (Postgrest). Offline is a shared/cross-platform concern, so this lives in the shared module (Android can adopt it later).
- Reads are cache-first and synchronous, so `FeedBridge.spots()`/`addSpot` stay flat and unchanged and the SwiftUI map/create UI is untouched. A background coroutine does remote fetch → replace-all → flush the pending queue. Creates write through to the cache immediately and enqueue for remote push; offline / unconfigured / unauthenticated leaves items queued and never crashes. `owner_id` comes from the session. UI-only fields (`checkInCount`, `placeholderColorArgb`) are NOT persisted — they are re-derived on read via `SpotMapping`.
- Flat refresh hooks `FeedBridge.refreshSpots` / `observeSpots` exist (no-arg, main-thread) for live refresh; currently optional/unwired on the Swift side (the map reflects remote changes on next appearance). Real remote persistence requires the user's keys + applied migrations + a logged-in session.
