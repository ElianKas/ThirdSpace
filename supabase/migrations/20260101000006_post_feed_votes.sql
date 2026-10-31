-- =============================================================================
-- ThirdSpace backend migration: post_feed view — real votes
-- =============================================================================
-- Supersedes the score / my_vote PLACEHOLDERS from the post_feed view defined in
-- 20260101000004_post_feed_view.sql now that public.post_votes exists
-- (20260101000005). The view's OUTPUT COLUMNS are UNCHANGED — same names, order,
-- and types (id, text, created_at, author_id, spot_id, author_name, spot_name,
-- category, neighborhood, verified, comment_count, score::int, my_vote::text) and
-- the same joins + comment_count subquery — so the shared cache and the flat
-- IosPost shape that selects this view are unaffected. Only the two expressions
-- behind `score` and `my_vote` change from constants to real subqueries, which is
-- why CREATE OR REPLACE VIEW is safe here (no column added/removed/renamed/retyped).
--
-- What changed vs 000004:
--   * score:   0::int placeholder  ->  COALESCE(sum of post_votes.value, 0)::int
--   * my_vote: 'NONE'::text placeholder  ->  the CURRENT USER's own vote as
--              'UP' / 'DOWN' / 'NONE' (NONE when no row exists for that user).
--
-- security_invoker: CREATE OR REPLACE VIEW does NOT necessarily preserve view
-- options across a replace, so `WITH (security_invoker = true)` is RE-SPECIFIED
-- explicitly below. This matters doubly for my_vote: because the view runs with
-- the QUERYING user's privileges, `auth.uid()` resolves to the CALLER, so each
-- caller sees THEIR OWN my_vote (and the underlying tables' RLS still applies
-- through the view, as in 000004). The GRANT SELECT ... TO authenticated from
-- 000004 is preserved below as well (re-issued is idempotent/harmless).
--
-- Depends on: public.posts, public.post_comments, public.profiles, public.spots
-- (earlier migrations) and public.post_votes (20260101000005). All sort first by
-- filename and MUST be applied first.
-- =============================================================================

CREATE OR REPLACE VIEW public.post_feed
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
  COALESCE(
    (
      SELECT sum(v.value)
      FROM public.post_votes v
      WHERE v.post_id = p.id
    ),
    0
  )::int                            AS score,      -- real aggregate (sum of votes)
  COALESCE(
    (
      -- security_invoker => auth.uid() is the QUERYING user, so this is the
      -- caller's OWN vote. A cleared vote has no row, hence COALESCE -> 'NONE'.
      SELECT CASE
               WHEN v.value = 1  THEN 'UP'
               WHEN v.value = -1 THEN 'DOWN'
             END
      FROM public.post_votes v
      WHERE v.post_id = p.id
        AND v.user_id = auth.uid()
    ),
    'NONE'
  )::text                           AS my_vote     -- current user's own vote
FROM public.posts p
JOIN public.profiles pr ON pr.id = p.author_id
JOIN public.spots s     ON s.id = p.spot_id;

GRANT SELECT ON public.post_feed TO authenticated;
