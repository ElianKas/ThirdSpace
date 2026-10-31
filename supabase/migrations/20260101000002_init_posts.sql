-- =============================================================================
-- ThirdSpace backend migration: posts (feed)
-- =============================================================================
-- Derived and TRIMMED from the legacy schema (legacy-project/third3_capacitor/
-- supabase/migrations/20260612133045_*.sql). The legacy posts table carried
-- visibility/friends semantics and an admin override in its RLS; this migration
-- keeps ONLY the minimal columns the current app's feed uses and applies
-- SELF-CONTAINED RLS ("read all" for any signed-in user, write-own).
--
-- Intentionally DROPPED from that legacy migration (OUT OF SCOPE for now):
--   * posts columns: photo_url, visibility (+ its CHECK)
--   * helper functions referenced by the legacy RLS: public.is_friend_of(),
--     public.is_super_admin()
--   * the sibling public.post_votes table (voting writes are out of scope;
--     the feed exposes a score=0 placeholder instead — see the view migration)
--   * GRANT ... TO anon / service_role (the app only ever talks as authenticated)
--   * ALTER PUBLICATION supabase_realtime ... (realtime wiring)
--
-- Depends on: public.spots (init_spots migration), auth.users, and
-- public.set_updated_at() (defined in the profiles migration). All of those
-- sort earlier by filename and MUST be applied first.
-- =============================================================================

-- Posts table (trimmed: only the fields the current feed needs).
CREATE TABLE public.posts (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  spot_id uuid NOT NULL REFERENCES public.spots(id) ON DELETE CASCADE,
  author_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  text text NOT NULL DEFAULT '',
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX posts_spot_idx ON public.posts(spot_id);
CREATE INDEX posts_author_idx ON public.posts(author_id);
CREATE INDEX posts_created_idx ON public.posts(created_at DESC);

GRANT SELECT, INSERT, UPDATE, DELETE ON public.posts TO authenticated;

ALTER TABLE public.posts ENABLE ROW LEVEL SECURITY;

-- v1 policies: all posts readable by any signed-in user; write-own only.
CREATE POLICY "posts: read all"
  ON public.posts FOR SELECT TO authenticated
  USING (true);

CREATE POLICY "posts: insert own"
  ON public.posts FOR INSERT TO authenticated
  WITH CHECK (author_id = auth.uid());

CREATE POLICY "posts: update own"
  ON public.posts FOR UPDATE TO authenticated
  USING (author_id = auth.uid())
  WITH CHECK (author_id = auth.uid());

CREATE POLICY "posts: delete own"
  ON public.posts FOR DELETE TO authenticated
  USING (author_id = auth.uid());

CREATE TRIGGER posts_set_updated_at
  BEFORE UPDATE ON public.posts
  FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();
