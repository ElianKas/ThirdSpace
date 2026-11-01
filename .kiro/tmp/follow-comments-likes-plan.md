# Implementation Plan: Comments-in-DB, Likes, Follower/Following Lists, Follow + Other-Profile Pages

## 0. Grounded current-state findings
- COMMENTS: already fully DB-backed. `post_comments` table (migration ...0003) with self-contained RLS (read-all, insert-own, delete-own). `SupabaseFeedRepository.fetchComments` reads `post_comments` durably, author names via a PostgREST `profiles(name)` embed. `CommentRepository` is offline-first (pure cache read + explicit `loadComments` refresh + optimistic `addComment` write-through + `pendingCommentInserts` queue). `cachedComments` persists across relaunch. Nothing missing for "comments fully in the DB." Verification-only.
- LIKES: already fully functional end-to-end. `post_votes` table (...0005, CHECK value IN (-1,1), composite PK, write-own RLS). `post_feed` view's real `score`/`my_vote` via migration ...0006 (`sum(value)` + caller's own vote under `security_invoker`). `VoteRepository` has the six-transition toggle, optimistic update, `pendingVotes` latest-intent queue, upsert(±1)/delete(clear). `VoteControlView`/`NativeFeedView` wired. Keep the EXISTING ±1 up/down model (no rewrite to up-only). Verification-only.
- FOLLOW/FOLLOWERS: genuinely new. `followerCount`/`followingCount` hardcoded 0. No `follows` table, no follow repo, no other-profile page, no list UI.
- author_id boundary gap: `post_feed`/`feed_entries` expose `author_id` server-side but it is NOT carried to Swift (not in `PostFeedDto`→`FeedPost`→`IosPost`→`UIPost`, nor comments). Must add a flat String `authorUserId` across the boundary to enable author taps.

## 1. Risk
Overall MEDIUM-HIGH. Comments verify LOW, Likes verify LOW, `follows` table+RLS (F1) HIGH (security-sensitive RLS), follow repo+counts+author_id (F2-F3) MEDIUM, SwiftUI (F4-F5) MEDIUM.

## 2. Decisions
1. Keep ±1 vote model (likes already work).
2. Follower/following counts = live DB count query (Count.EXACT head), not derived from lists. Own posts/spots counts stay derived.
3. Self-follow prevention + uniqueness DB-enforced: PK (follower_id, following_id), CHECK (follower_id <> following_id). RLS insert/delete own (follower_id = auth.uid()). UI also hides follow button on own profile.
4. Follow entry points: follower/following list rows; post author name/avatar in feed + profile posts tab; comment author in comments sheet. Not from bare-spot entries unless real author present. All require authorId at the UI -> add author_id to the flat boundary.
5. "Comments fully in DB": already satisfied; Phase C is verification-only.
6. author_id crosses as a new non-null flat String `authorUserId` on IosPost/IosComment (+ internal FeedPost/FeedComment, DTOs, SQLDelight cachedPosts/cachedComments). Empty = unknown/hide-follow.

## 3. Phases (sequential; Builder->Reviewer each)
- Phase C - Comments DB verification (LOW, no code change). Confirm durable read + author names + cross-relaunch cache. Optional commonTest only.
- Phase L - Likes verification (LOW, no code change). Confirm six toggle transitions + optimistic + ±1 upsert/clear-delete + real aggregate. Ensure a `VoteRepository.resolve` commonTest covers all six transitions; add if absent.
- Phase F1 - `follows` table + RLS migration (HIGH; user applies via `supabase db push`, orchestrator will NOT push). New file `supabase/migrations/20260101000009_init_follows.sql`:
    CREATE TABLE public.follows (follower_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE, following_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE, created_at timestamptz NOT NULL DEFAULT now(), PRIMARY KEY (follower_id, following_id), CONSTRAINT follows_no_self CHECK (follower_id <> following_id));
    CREATE INDEX follows_following_idx ON public.follows(following_id);
    CREATE INDEX follows_follower_idx ON public.follows(follower_id);
    GRANT SELECT, INSERT, DELETE ON public.follows TO authenticated;
    ALTER TABLE public.follows ENABLE ROW LEVEL SECURITY;
    POLICY read-all (SELECT USING true), insert own (WITH CHECK follower_id = auth.uid()), delete own (USING follower_id = auth.uid()). No UPDATE policy.
  Live apply BLOCKED-until-session.
- Phase F2 - Follow repository seam (shared, MEDIUM). CREATE FollowModels.kt (flat FollowUser: id, name, username, city, isFollowedByMe), FollowRepository.kt (+ Follows provider, in-memory cache, background Dispatchers.Default scope, onChanged), SupabaseFollowRepository.kt (FollowRemoteSource + supabase-kt impl + FollowDto/FollowInsertDto; lists resolve counterpart profile via embed or helper view; counts via Count.EXACT head; isFollowedByMe via intersect with caller's following). BEST-EFFORT offline/unauth -> empty/0/false, never throws. Tests: FollowRepositoryTest + FollowMappingTest against a fake remote. No SQLDelight.
- Phase F3 - author_id across boundary + real social counts + the ONE SQLDelight bump. Modify FeedModels.kt (authorUserId on FeedPost/FeedComment), SupabaseFeedRepository.kt (author_id in PostFeedDto/FeedEntryDto/CommentDto + mapping), FeedCache.kt + ThirdSpaceDb.sq (author_user_id column on cachedPosts/cachedComments + queries + LocalFeedCache/InMemoryFeedCache), NEW migration 3.sqm (v3->v4): ALTER TABLE cachedPosts ADD COLUMN author_user_id TEXT NOT NULL DEFAULT ''; ALTER TABLE cachedComments ADD COLUMN author_user_id TEXT NOT NULL DEFAULT ''; ProfileRepository/SupabaseProfileRepository/ProfileMapping (fold real follower/following counts from FollowRepository during refresh; replace the two hardcoded 0s). FeedBridge.kt (authorUserId on IosPost/IosComment; profile() real counts). UIPost.swift (authorUserId on UIPost/UIComment). Tests: extend FeedDbMigrationTest with v3->v4 case (Schema.version == 4, columns added, backfill ''), update schemaVersionIsThree -> four; adjust mapping tests.
- Phase F4 - Bridge follow hooks + Swift stores (iOS, MEDIUM). FeedBridge.kt flat Main-thread hooks: followUser/unfollowUser(targetId, completion:(Bool)), isFollowing(targetId):Bool + loadFollowState, followers(userId)/following(userId):[IosFollowUser] + loadFollowers/loadFollowing, otherProfile(userId):IosProfile + loadOtherProfile, observeFollows; NEW IosFollowUser(id,name,username,city,isFollowedByMe). Generalize SupabaseProfileRepository to fetchProfile(userId). CREATE FollowStore.swift (@Observable, mirrors ProfileStore). Extend UIPost.swift with UIFollowUser + FeedData passthroughs. BUILD gate (no iOS test target); shared additions get commonTests.
- Phase F5 - SwiftUI other-profile page + searchable lists + entry points (iOS, MEDIUM). CREATE OtherProfileView.swift (pushed; reuses ProfileView layout via otherProfile/loadOtherProfile; Follow/Unfollow button hidden on self; no globe/edit), FollowListView.swift (pushed, .searchable UIFollowUser rows, parameterized followers/following, tap -> OtherProfileView). Modify ProfileView.swift (Follower/Folge-ich StatColumns tappable -> push FollowListView), PostCardView.swift (author tap -> OtherProfileView via optional onAuthorTap, suppress for bare spot/empty/self), CommentsSheetView.swift (comment author tap -> present OtherProfileView as a nested SHEET, since the comments sheet is not in a NavigationStack), RootTabView.swift/iOSApp.swift (inject FollowStore). i18n: add typed keys in De+En + IosStrings + from(): followButton, unfollowButton, followListFollowersTitle, followListFollowingTitle, followSearchPlaceholder, followEmpty. StringsTest must stay green. BUILD gate.

## 4. BLOCKED-until-session vs provable now
Provable now: follow toggle/optimistic logic (fake remote), mappings + count wiring (fake remote), 3.sqm v3->v4 migration + author_id round-trip (FeedDbMigrationTest), i18n completeness (StringsTest), all four targets BUILD. BLOCKED-until-session: follows migration apply, real follow/unfollow round-trips, real lists/counts, RLS guarantees observed live.

## 5. Steering facts made stale (update after)
- "followerCount/followingCount stay 0 (social graph deferred)" -> real counts.
- Deliberate-exclusions "broader social graph (friendships/follows/...) out of scope" -> follows now implemented (narrow exclusion to friendships/notifications/moments/DMs/moderation).
- SQLDelight version 3 -> 4 (3.sqm).
- Flat boundary gains author_user_id / IosFollowUser + follow callback hooks.
- SupabaseProfileRepository now fetches any user's profile (not only current).

## 6. Order
C, L (either first) -> F1 -> F2 -> F3 (SQLDelight bump) -> F4 -> F5. Strictly serial.
