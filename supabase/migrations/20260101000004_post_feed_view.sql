-- =============================================================================
-- ThirdSpace backend migration: post_feed view (feed)
-- =============================================================================
-- A READ-ONLY view that assembles the flat row the app's feed needs, so the
-- bridge can SELECT a single shape (the flat IosPost) directly instead of
-- joining client-side. There is no legacy equivalent — the legacy app built the
-- feed row in client/edge code. This is new, derived from the TRIMMED posts /
-- post_comments / profiles / spots tables defined in the earlier migrations.
--
-- Intentionally NOT reproduced here (OUT OF SCOPE for now, consistent with the
-- posts/comments migrations):
--   * real per-user votes / aggregated score (public.post_votes) — the view
--     exposes score = 0 and my_vote = 'NONE' placeholders so the flat shape is
--     forward-compatible when voting lands later
--   * visibility / friends filtering, photo_url, parent_id threading
--
-- Depends on: public.posts, public.post_comments, public.profiles, public.spots
-- (all defined in earlier migrations that sort first by filename).
--
-- RLS + views: a view normally runs with the view OWNER's privileges, which can
-- BYPASS the underlying tables' row-level security. `security_invoker = true`
-- (PostgreSQL 15+, which Supabase runs) makes the view execute with the
-- QUERYING user's privileges, so the posts/post_comments/profiles/spots RLS
-- still applies through the view. Even though every underlying table currently
-- has a permissive "read all to authenticated" SELECT policy, we set it anyway
-- as the safe, future-proof default (so tightening a base-table policy later
-- automatically flows through the view).
-- =============================================================================

CREATE VIEW public.post_feed
WITH (security_invoker = true) AS
SELECT
  p.id                              AS id,
  p.text                            AS text,
  p.created_at                      AS created_at,
  p.author_id                       AS author_id,
  p.spot_id                         AS spot_id,
  pr.name                           AS author_name,
  s.name                            AS spot_name,
  s.category                        AS category,
  s.neighborhood                    AS neighborhood,
  s.verified                        AS verified,
  (
    SELECT count(*)
    FROM public.post_comments c
    WHERE c.post_id = p.id
  )::int                            AS comment_count,
  0::int                            AS score,      -- voting deferred (placeholder)
  'NONE'::text                      AS my_vote     -- per-user vote deferred (placeholder)
FROM public.posts p
JOIN public.profiles pr ON pr.id = p.author_id
JOIN public.spots s     ON s.id = p.spot_id;

GRANT SELECT ON public.post_feed TO authenticated;
