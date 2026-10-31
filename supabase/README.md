# ThirdSpace — Supabase backend

These are the database migrations for the ThirdSpace backend. They define the
Postgres schema the app runs against.

## Scope (current)

Only two tables are in scope right now:

- **`profiles`** — one row per auth user, auto-created on signup.
- **`spots`** — user-created spots, mapped 1:1 onto the app's shared `Spot`
  model.

The broader legacy social graph (friendships, votes, comments, posts,
notifications, reports, roles/admin, etc.) is **intentionally out of scope** and
is not reproduced here. The migrations are derived and trimmed from the legacy
schema under `legacy-project/third3_capacitor/supabase/`.

## Migrations

| File | Contents |
|---|---|
| `migrations/20260101000000_init_profiles.sql` | `profiles` table, `set_updated_at()` (shared), `handle_new_user()`, their triggers, and `profiles` RLS. |
| `migrations/20260101000001_init_spots.sql` | Trimmed `spots` table, indexes, `spots_set_updated_at` trigger (reuses `set_updated_at()`), self-contained `spots` RLS and grants. |

Apply them in filename order: profiles first (it defines the shared
`set_updated_at()` function), then spots.

## Applying these migrations

1. Set your own project ref in `config.toml` (`project_id`). Do **not** reuse
   any legacy project ref — point at a Supabase project you own, and supply your
   own API keys/secrets in your environment.
2. Link and push with the Supabase CLI:

   ```sh
   supabase link --project-ref <your-project-ref>
   supabase db push
   ```

   Alternatively, paste the contents of each migration (in order) into the
   Supabase dashboard SQL editor.

3. (Optional) Validate locally if you have the Supabase CLI and Docker:

   ```sh
   supabase db lint
   supabase db reset   # applies all migrations against a local shadow db
   ```
