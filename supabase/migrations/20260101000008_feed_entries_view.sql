-- =============================================================================
-- ThirdSpace backend migration: feed_entries view (feed = posts + bare spots)
-- =============================================================================
-- Phase 4 (final): the feed must surface public SPOTS as well as public POSTS.
-- A freshly created spot that has no post yet still appears in the feed,
-- alongside every post. "Public" here == "all rows": there is NO `visibility`
-- column on `spots` or `posts` (both dropped it), and the RLS on both is the
-- self-contained "read all to authenticated" policy, so this migration adds NO
-- new column and changes NO RLS. The task reduces to ALSO surfacing spots that
-- have no post.
--
-- This is a NEW, READ-ONLY view that UNION ALLs two branches into one flat row
-- shape so a SINGLE wire DTO can decode both:
--   * kind = 'post' : every row of the existing `public.post_feed` view (see
--     20260101000004 + the real-votes 20260101000006), carrying the post id.
--   * kind = 'spot' : every `public.spots` row that has NO posts, rendered as a
--     bare spot entry (empty text, 0 comments, 0 score, my_vote 'NONE').
--
-- STABLE entry id: `entry_id` is the ForEach/primary identity the client uses.
-- Post entries use the post uuid as-is; bare-spot entries use `'spot:' || id`
-- so a spot entry can never collide with a post uuid.
--
-- COLUMN CONTRACT: the two branches have the IDENTICAL column list, order, and
-- types, so one DTO (FeedEntryDto) decodes both. `id` / `entry_id` are cast to
-- text in BOTH branches so the UNION ALL column type is unambiguously text
-- (branch 1's uuid id would otherwise govern and reject branch 2's text
-- 'spot:'-prefixed id). Columns (in order):
--   kind text, entry_id text, id text, text text, created_at timestamptz,
--   author_id uuid, spot_id uuid, author_name text, spot_name text,
--   category text, neighborhood text, verified bool, comment_count int,
--   score int, my_vote text
-- (author_id/spot_id stay uuid — aligned in both branches: post_feed's uuids vs
--  spots.owner_id / spots.id; PostgREST serializes uuids as JSON strings, which
--  the String DTO fields decode cleanly. This mirrors post_feed's output columns
--  plus the two new leading columns `kind` / `entry_id`.)
--
-- NO ORDER BY in the view: the caller (SupabaseFeedRepository.fetchFeed) orders
-- newest-first via `order("created_at", DESCENDING)`, exactly like it already
-- does against `post_feed`.
--
-- Column existence confirmed by reading the earlier migrations:
--   * post_feed columns (20260101000004 / ...0006): id, text, created_at,
--     author_id, spot_id, author_name, spot_name, category, neighborhood,
--     verified, comment_count, score, my_vote.
--   * spots columns (20260101000001): id, owner_id, name, category,
--     neighborhood, verified, created_at (+ lat/lng/updated_at, unused here).
--   * profiles.name (20260101000000) for the owner's display name, mirroring
--     post_feed's `JOIN public.profiles pr`.
--   * posts.spot_id (20260101000002) for the NOT EXISTS "has no post" test.
--
-- Depends on: public.post_feed, public.posts, public.spots, public.profiles —
-- all defined by migrations that sort EARLIER by filename and MUST be applied
-- first. This migration ONLY CREATEs a new view; it ALTERS no table, trigger,
-- or RLS policy.
--
-- security_invoker = true: like post_feed, the view runs with the QUERYING
-- user's privileges so the underlying tables' RLS still applies through it (and
-- post_feed's own `my_vote` auth.uid() resolution keeps working for the post
-- branch). GRANT SELECT TO authenticated, consistent with post_feed.
-- =============================================================================

CREATE VIEW public.feed_entries
WITH (security_invoker = true) AS
-- Branch 1: every post, straight from post_feed (kind = 'post').
SELECT
  'post'::text                      AS kind,
  pf.id::text                       AS entry_id,   -- post uuid as the stable id (text)
  pf.id::text                       AS id,
  pf.text                           AS text,
  pf.created_at                     AS created_at,
  pf.author_id                      AS author_id,
  pf.spot_id                        AS spot_id,
  pf.author_name                    AS author_name,
  pf.spot_name                      AS spot_name,
  pf.category                       AS category,
  pf.neighborhood                   AS neighborhood,
  pf.verified                       AS verified,
  pf.comment_count                  AS comment_count,
  pf.score                          AS score,
  pf.my_vote                        AS my_vote
FROM public.post_feed pf

UNION ALL

-- Branch 2: every spot that has NO posts, as a bare-spot entry (kind = 'spot').
SELECT
  'spot'::text                      AS kind,
  ('spot:' || s.id::text)           AS entry_id,   -- 'spot:'-prefixed, distinct
  s.id::text                        AS id,
  ''::text                          AS text,        -- a bare spot has no caption
  s.created_at                      AS created_at,
  s.owner_id                        AS author_id,   -- the spot's owner is the "author"
  s.id                              AS spot_id,
  pr.name                           AS author_name,  -- mirror post_feed's author join
  s.name                            AS spot_name,
  s.category                        AS category,
  s.neighborhood                    AS neighborhood,
  s.verified                        AS verified,
  0::int                            AS comment_count,
  0::int                            AS score,
  'NONE'::text                      AS my_vote
FROM public.spots s
JOIN public.profiles pr ON pr.id = s.owner_id
WHERE NOT EXISTS (
  SELECT 1 FROM public.posts p WHERE p.spot_id = s.id
);

GRANT SELECT ON public.feed_entries TO authenticated;
