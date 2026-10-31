-- =============================================================================
-- ThirdSpace backend migration: post_votes (voting)
-- =============================================================================
-- Derived and TRIMMED from the legacy schema (legacy-project/third3_capacitor/
-- supabase/migrations/20260612133045_*.sql, the public.post_votes table). This
-- reproduces the legacy voting table with SELF-CONTAINED RLS ("read all" for any
-- signed-in user, write-own only), so the feed view can compute a real score and
-- the current user's own vote (see the next migration).
--
-- A cleared vote is represented by DELETING the row, NOT by a 0 value — the CHECK
-- is exactly IN (-1, 1), matching the legacy table and the UP/DOWN/NONE shape the
-- app expects (NONE = no row).
--
-- Intentionally DROPPED from that legacy migration (OUT OF SCOPE, consistent with
-- the posts/comments migrations):
--   * GRANT ... TO anon / service_role (the app only ever talks as authenticated)
--   * ALTER PUBLICATION supabase_realtime ... (realtime wiring)
--
-- Depends on: public.posts (init_posts migration) and auth.users. Both sort
-- earlier by filename and MUST be applied first.
-- =============================================================================

-- Post votes table (one row per (post, user); the row's existence + sign is the
-- vote; absence of a row == no vote / NONE).
CREATE TABLE public.post_votes (
  post_id uuid NOT NULL REFERENCES public.posts(id) ON DELETE CASCADE,
  user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  value smallint NOT NULL CHECK (value IN (-1, 1)),
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (post_id, user_id)
);

-- Supports the per-post score aggregate in the post_feed view.
CREATE INDEX post_votes_post_idx ON public.post_votes(post_id);

GRANT SELECT, INSERT, UPDATE, DELETE ON public.post_votes TO authenticated;

ALTER TABLE public.post_votes ENABLE ROW LEVEL SECURITY;

-- v1 policies: all votes readable by any signed-in user (needed for the score
-- aggregate); write-own only (a user can only cast/change/clear their own vote).
CREATE POLICY "post_votes: read all"
  ON public.post_votes FOR SELECT TO authenticated
  USING (true);

CREATE POLICY "post_votes: insert own"
  ON public.post_votes FOR INSERT TO authenticated
  WITH CHECK (user_id = auth.uid());

CREATE POLICY "post_votes: update own"
  ON public.post_votes FOR UPDATE TO authenticated
  USING (user_id = auth.uid())
  WITH CHECK (user_id = auth.uid());

CREATE POLICY "post_votes: delete own"
  ON public.post_votes FOR DELETE TO authenticated
  USING (user_id = auth.uid());
