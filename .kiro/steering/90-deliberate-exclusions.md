---
inclusion: always
---

# Deliberate exclusions (current state)

> Split out of the former `architecture.md`.

- Auth, spot persistence, the feed (posts/comments/votes), AND the profile are all backed by Supabase. `SampleData` no longer backs any live screen; it now only provides the OFFLINE SEED for the feed/spots caches (so they are not empty pre-login) plus the stable report-reason / map-filter keys.
- The profile edit page's "Konto" tab auth operations (change email, change password, delete account) and the avatar/banner upload are still placeholder/no-op — deferred.
- The one-directional follow graph IS implemented; the broader social graph (friendships (symmetric)/notifications/moments/DMs/moderation), Realtime, and spot update/delete sync remain out of scope.
- Personal spot collections ARE implemented (private, owner-scoped — see the Collections seam above); public/shared collections and an offline collection cache remain deferred.
- The spot model still lacks `description` and a photo strip (both intentionally absent for now); `SpotSheetView` uses placeholder content for those fields. Coordinates (`lat`/`lng`) do now exist.
