-- =============================================================================
-- ThirdSpace backend migration: profiles
-- =============================================================================
-- Derived from the legacy schema (legacy-project/third3_capacitor/supabase/
-- migrations/20260515234955_*.sql). The profiles table, set_updated_at(),
-- handle_new_user() and their triggers are reproduced VERBATIM because they are
-- already minimal and correct.
--
-- Intentionally DROPPED from that legacy migration (OUT OF SCOPE for now):
--   * public.friendship_status enum type
--   * public.friendships table (+ its indexes, RLS policies and trigger)
--
-- set_updated_at() is shared by both tables and is defined HERE (the first
-- migration), so it exists before the spots migration reuses it.
-- =============================================================================

-- Profiles table
CREATE TABLE public.profiles (
  id uuid PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
  name text NOT NULL,
  email text NOT NULL,
  city text NOT NULL DEFAULT 'Berlin',
  avatar_url text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX profiles_email_lower_idx ON public.profiles (lower(email));

ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Profiles are viewable by authenticated users"
  ON public.profiles FOR SELECT
  TO authenticated
  USING (true);

CREATE POLICY "Users can insert own profile"
  ON public.profiles FOR INSERT
  TO authenticated
  WITH CHECK (auth.uid() = id);

CREATE POLICY "Users can update own profile"
  ON public.profiles FOR UPDATE
  TO authenticated
  USING (auth.uid() = id);

-- Shared updated-at trigger function (reused by the spots migration).
CREATE OR REPLACE FUNCTION public.set_updated_at()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$;

CREATE TRIGGER profiles_set_updated_at
BEFORE UPDATE ON public.profiles
FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

-- Auto-create a profile row on signup. Reproduces the legacy signup -> profile
-- flow: name/city/avatar_url are read from the new user's raw_user_meta_data,
-- with sensible fallbacks (name -> local part of email, city -> 'Berlin').
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  INSERT INTO public.profiles (id, name, email, city, avatar_url)
  VALUES (
    NEW.id,
    COALESCE(NEW.raw_user_meta_data->>'name', split_part(NEW.email, '@', 1)),
    NEW.email,
    COALESCE(NEW.raw_user_meta_data->>'city', 'Berlin'),
    NEW.raw_user_meta_data->>'avatar_url'
  );
  RETURN NEW;
END;
$$;

CREATE TRIGGER on_auth_user_created
AFTER INSERT ON auth.users
FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();
