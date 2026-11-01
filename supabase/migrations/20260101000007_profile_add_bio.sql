-- Add a profile bio column (shown on the native profile screen; previously
-- only in the dummy SampleData.profile). Idempotent + NOT NULL DEFAULT '' so
-- existing rows and the signup trigger (which does not set bio) are unaffected.
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS bio text NOT NULL DEFAULT '';
