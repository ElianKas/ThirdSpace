-- =============================================================================
-- ThirdSpace backend migration: follows (social graph)
-- =============================================================================
-- Introduces a one-directional FOLLOW graph: "follower_id follows following_id".
-- The legacy init_profiles migration DROPPED a legacy symmetric friendships
-- table as OUT OF SCOPE; this table is the leaner, intentional reintroduction of
-- the social graph as a DIRECTED follow relationship (A following B does NOT
-- imply B following A) rather than symmetric friendships.
--
-- Design:
--   * PRIMARY KEY (follower_id, following_id) guarantees uniqueness and makes a
--     re-follow idempotent — a repeat insert hits the PK conflict (ON CONFLICT
--     DO NOTHING on the caller side), so there is never a duplicate follow row.
--   * CHECK (follower_id <> following_id) prevents self-follow at the DB level.
--   * Two single-column indexes serve the two list queries and their COUNT(EXACT)
--     companions: followers of X  = filter following_id;
--                 X's following   = filter follower_id.
--   * NO UPDATE policy — a follow is insert/delete only; there is nothing to edit
--     (unfollow = DELETE the row).
--   * Read-all SELECT mirrors post_votes / post_comments so any authenticated
--     user can view any profile's follower/following lists and counts.
--
-- Intentionally DROPPED (OUT OF SCOPE, consistent with the posts/comments/votes
-- migrations):
--   * GRANT ... TO anon / service_role (the app only ever talks as authenticated)
--   * ALTER PUBLICATION supabase_realtime ... (realtime wiring)
--   * an UPDATE policy (a follow row is never edited)
--
-- Depends on: auth.users. Sorts LAST by filename among the current migrations
-- (…0008 is the latest), so it applies after everything else.
-- =============================================================================

-- Directed follow edges (one row per (follower, following) pair).
CREATE TABLE public.follows (
  follower_id  uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  following_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  created_at   timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (follower_id, following_id),
  CONSTRAINT follows_no_self CHECK (follower_id <> following_id)
);

-- "who follows X" (followers of X): filter by following_id
CREATE INDEX follows_following_idx ON public.follows(following_id);
-- "who X follows" (X's following): filter by follower_id
CREATE INDEX follows_follower_idx  ON public.follows(follower_id);

GRANT SELECT, INSERT, DELETE ON public.follows TO authenticated;

ALTER TABLE public.follows ENABLE ROW LEVEL SECURITY;

-- Anyone signed in can read the social graph (needed to show any user's
-- follower/following lists + counts).
CREATE POLICY "follows: read all"
  ON public.follows FOR SELECT TO authenticated
  USING (true);

-- You may only create a follow where YOU are the follower.
CREATE POLICY "follows: insert own"
  ON public.follows FOR INSERT TO authenticated
  WITH CHECK (follower_id = auth.uid());

-- You may only delete (unfollow) your OWN follow rows.
CREATE POLICY "follows: delete own"
  ON public.follows FOR DELETE TO authenticated
  USING (follower_id = auth.uid());
