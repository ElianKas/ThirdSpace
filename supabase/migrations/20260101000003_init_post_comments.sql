-- =============================================================================
-- ThirdSpace backend migration: post_comments (feed)
-- =============================================================================
-- Derived and TRIMMED from the legacy schema (legacy-project/third3_capacitor/
-- supabase/migrations/20260612133045_*.sql). The legacy post_comments table
-- supported threaded replies (parent_id) and an admin delete override; this
-- migration keeps a flat comment list with SELF-CONTAINED RLS.
--
-- Intentionally DROPPED from that legacy migration (OUT OF SCOPE for now):
--   * post_comments columns: parent_id (no comment threading in v1)
--   * helper functions referenced by the legacy RLS: public.is_super_admin()
--   * GRANT ... TO anon / service_role (the app only ever talks as authenticated)
--   * ALTER PUBLICATION supabase_realtime ... (realtime wiring)
--   * an UPDATE policy — comments are not edited in v1 (create/read/delete only)
--
-- Depends on: public.posts (init_posts migration) and auth.users. Both sort
-- earlier by filename and MUST be applied first.
-- =============================================================================

-- Flat comments table (no threading).
CREATE TABLE public.post_comments (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  post_id uuid NOT NULL REFERENCES public.posts(id) ON DELETE CASCADE,
  author_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  text text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX post_comments_post_idx ON public.post_comments(post_id);
-- Supports the feed's per-post, chronologically ordered comment reads.
CREATE INDEX post_comments_post_created_idx ON public.post_comments(post_id, created_at);

GRANT SELECT, INSERT, UPDATE, DELETE ON public.post_comments TO authenticated;

ALTER TABLE public.post_comments ENABLE ROW LEVEL SECURITY;

-- v1 policies: all comments readable by any signed-in user; insert/delete own.
CREATE POLICY "post_comments: read all"
  ON public.post_comments FOR SELECT TO authenticated
  USING (true);

CREATE POLICY "post_comments: insert own"
  ON public.post_comments FOR INSERT TO authenticated
  WITH CHECK (author_id = auth.uid());

CREATE POLICY "post_comments: delete own"
  ON public.post_comments FOR DELETE TO authenticated
  USING (author_id = auth.uid());
