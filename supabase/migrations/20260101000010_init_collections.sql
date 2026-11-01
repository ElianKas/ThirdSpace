-- =============================================================================
-- ThirdSpace backend migration: collections (personal spot collections)
-- =============================================================================
-- Introduces PERSONAL, owner-scoped spot collections: a user creates named
-- collections ("Schöne Orte am Wasser", "Wochenendtrips") and saves spots into
-- them. Two tables:
--   * public.collections       — one row per named collection, owned by a user.
--   * public.collection_spots  — the many-to-many join between a collection and
--                                the spots saved into it.
--
-- Design:
--   * collections.owner_id FK auth.users ON DELETE CASCADE — deleting the user
--     removes their collections (and, via the join's cascade, their membership
--     rows). The set_updated_at trigger (reused from the profiles migration)
--     keeps updated_at fresh on rename.
--   * collection_spots has a COMPOSITE PRIMARY KEY (collection_id, spot_id) that
--     guarantees uniqueness and makes a re-add idempotent (a repeat insert hits
--     the PK conflict). Both FKs cascade: dropping a collection or a spot prunes
--     the corresponding membership rows automatically. There is NO UPDATE policy
--     on the join — membership is insert/delete only (unassign = DELETE).
--   * Two single-column indexes on the join serve the two read directions:
--     "spots in collection X" (filter collection_id) and "collections containing
--     spot Y" (filter spot_id). collections_owner_idx backs the owner's list.
--   * OWNER-SCOPED RLS because these are PRIVATE, personal collections (unlike
--     the read-all spots/follows tables): a user may only see/edit their own
--     collections. The join table's RLS has no owner_id column of its own, so it
--     gates every operation via an EXISTS against the PARENT collection's
--     ownership — you may only touch membership rows of collections you own.
--     Public/shared collections are DEFERRED.
--
-- Intentionally DROPPED (OUT OF SCOPE, consistent with the spots/follows
-- migrations):
--   * GRANT ... TO anon / service_role (the app only ever talks as authenticated)
--   * ALTER PUBLICATION supabase_realtime ... (realtime wiring)
--   * an UPDATE policy on collection_spots (a membership row is never edited)
--   * public/shared collections and any sharing/visibility model
--
-- Depends on public.set_updated_at() (defined in the profiles migration) and
-- public.spots (defined in the spots migration), both of which sort earlier and
-- MUST be applied first. Sorts LAST by filename among the current migrations
-- (…0009 is the latest), so it applies after everything else.
-- =============================================================================

-- Named personal collections, one owner each.
CREATE TABLE public.collections (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  name text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

GRANT SELECT, INSERT, UPDATE, DELETE ON public.collections TO authenticated;

ALTER TABLE public.collections ENABLE ROW LEVEL SECURITY;

-- Personal collections: a user may only read their OWN collections.
CREATE POLICY "collections: read own"
  ON public.collections FOR SELECT TO authenticated
  USING (owner_id = auth.uid());

CREATE POLICY "collections: insert own"
  ON public.collections FOR INSERT TO authenticated
  WITH CHECK (owner_id = auth.uid());

CREATE POLICY "collections: update own"
  ON public.collections FOR UPDATE TO authenticated
  USING (owner_id = auth.uid())
  WITH CHECK (owner_id = auth.uid());

CREATE POLICY "collections: delete own"
  ON public.collections FOR DELETE TO authenticated
  USING (owner_id = auth.uid());

CREATE INDEX collections_owner_idx ON public.collections(owner_id);

CREATE TRIGGER collections_set_updated_at
  BEFORE UPDATE ON public.collections
  FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

-- Many-to-many join: which spots are saved into which collection.
CREATE TABLE public.collection_spots (
  collection_id uuid NOT NULL REFERENCES public.collections(id) ON DELETE CASCADE,
  spot_id uuid NOT NULL REFERENCES public.spots(id) ON DELETE CASCADE,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (collection_id, spot_id)
);

-- "spots in collection X": filter by collection_id
CREATE INDEX collection_spots_collection_idx ON public.collection_spots(collection_id);
-- "collections containing spot Y": filter by spot_id
CREATE INDEX collection_spots_spot_idx ON public.collection_spots(spot_id);

GRANT SELECT, INSERT, DELETE ON public.collection_spots TO authenticated;

ALTER TABLE public.collection_spots ENABLE ROW LEVEL SECURITY;

-- The join has no owner column of its own; every operation is gated via an
-- EXISTS against the PARENT collection's ownership.
CREATE POLICY "collection_spots: read own"
  ON public.collection_spots FOR SELECT TO authenticated
  USING (EXISTS (SELECT 1 FROM public.collections c WHERE c.id = collection_id AND c.owner_id = auth.uid()));

CREATE POLICY "collection_spots: insert own"
  ON public.collection_spots FOR INSERT TO authenticated
  WITH CHECK (EXISTS (SELECT 1 FROM public.collections c WHERE c.id = collection_id AND c.owner_id = auth.uid()));

CREATE POLICY "collection_spots: delete own"
  ON public.collection_spots FOR DELETE TO authenticated
  USING (EXISTS (SELECT 1 FROM public.collections c WHERE c.id = collection_id AND c.owner_id = auth.uid()));
