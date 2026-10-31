# ThirdSpace — Supabase backend

These are the database migrations for the ThirdSpace backend. They define the
Postgres schema the app runs against.

## Scope (current)

In scope right now:

- **`profiles`** — one row per auth user, auto-created on signup.
- **`spots`** — user-created spots, mapped 1:1 onto the app's shared `Spot`
  model.
- **`posts`** + **`post_comments`** — the feed. Trimmed to the minimal columns
  the app's feed uses.
- **`post_feed`** — a read-only view that assembles the flat feed row (post +
  author name + spot fields + comment count) the app selects directly.

The broader legacy social graph (friendships, votes, notifications, reports,
roles/admin, etc.) is **intentionally out of scope** and is not reproduced here.
In particular, post voting (`post_votes`), comment threading (`parent_id`), post
visibility/friends filtering, `photo_url`, and realtime publications are
deliberately left out; the feed view exposes `score = 0` / `my_vote = 'NONE'`
placeholders so the flat shape stays forward-compatible when voting lands later.
The migrations are derived and trimmed from the legacy schema under
`legacy-project/third3_capacitor/supabase/`.

## Migrations

| File | Contents |
|---|---|
| `migrations/20260101000000_init_profiles.sql` | `profiles` table, `set_updated_at()` (shared), `handle_new_user()`, their triggers, and `profiles` RLS. |
| `migrations/20260101000001_init_spots.sql` | Trimmed `spots` table, indexes, `spots_set_updated_at` trigger (reuses `set_updated_at()`), self-contained `spots` RLS and grants. |
| `migrations/20260101000002_init_posts.sql` | Trimmed `posts` table (feed), indexes (incl. `created_at DESC`), `posts_set_updated_at` trigger (reuses `set_updated_at()`), self-contained `posts` RLS and grants. |
| `migrations/20260101000003_init_post_comments.sql` | Flat `post_comments` table (no threading), indexes, self-contained RLS (read-all / insert-own / delete-own) and grants. |
| `migrations/20260101000004_post_feed_view.sql` | Read-only `post_feed` view (`security_invoker = true`) assembling the flat feed row; `score`/`my_vote` placeholders for deferred voting. |

Apply them in filename order: profiles first (it defines the shared
`set_updated_at()` function), then spots, then posts, post_comments, and finally
the `post_feed` view (which depends on all of the above).

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
