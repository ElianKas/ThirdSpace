-- =============================================================================
-- ThirdSpace backend migration: spots
-- =============================================================================
-- Derived and TRIMMED from the legacy schema (legacy-project/third3_capacitor/
-- supabase/migrations/20260611150532_*.sql). The legacy spots table had 16
-- columns and its RLS referenced an out-of-scope friends/admin system; this
-- migration keeps ONLY what the current app's shared Spot model persists and
-- uses SELF-CONTAINED RLS.
--
-- Intentionally DROPPED from that legacy migration (OUT OF SCOPE for now):
--   * spots columns: description, address, vibe, photos, visibility, geheimtipp
--   * helper functions referenced by the legacy RLS: public.is_friend_of(),
--     public.has_role() (they belong to the friends/admin system)
--   * tables: public.spot_votes, public.spot_comments
--   * the legacy "read visible"/admin RLS variants
--   * ALTER PUBLICATION supabase_realtime ... (realtime wiring)
--
-- Depends on public.set_updated_at(), defined in the profiles migration, which
-- MUST be applied first (its timestamp sorts earlier).
-- =============================================================================

-- Spots table (trimmed: only the persisted shared Spot fields).
CREATE TABLE public.spots (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  name text NOT NULL,
  category text NOT NULL,
  neighborhood text NOT NULL DEFAULT '',
  lat double precision NOT NULL,
  lng double precision NOT NULL,
  verified boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

GRANT SELECT, INSERT, UPDATE, DELETE ON public.spots TO authenticated;

ALTER TABLE public.spots ENABLE ROW LEVEL SECURITY;

-- v1 policy: all spots are readable by any signed-in user.
CREATE POLICY "spots: read all"
  ON public.spots FOR SELECT TO authenticated
  USING (true);

CREATE POLICY "spots: insert own"
  ON public.spots FOR INSERT TO authenticated
  WITH CHECK (owner_id = auth.uid());

CREATE POLICY "spots: update own"
  ON public.spots FOR UPDATE TO authenticated
  USING (owner_id = auth.uid())
  WITH CHECK (owner_id = auth.uid());

CREATE POLICY "spots: delete own"
  ON public.spots FOR DELETE TO authenticated
  USING (owner_id = auth.uid());

CREATE INDEX spots_category_idx ON public.spots(category);
CREATE INDEX spots_owner_idx ON public.spots(owner_id);
CREATE INDEX spots_latlng_idx ON public.spots(lat, lng);

CREATE TRIGGER spots_set_updated_at
  BEFORE UPDATE ON public.spots
  FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();
