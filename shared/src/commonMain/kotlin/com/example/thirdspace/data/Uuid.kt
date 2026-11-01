package com.example.thirdspace.data

/**
 * Shared, dependency-free UUID-shape validation — the SINGLE SOURCE OF TRUTH
 * for "is this id a server-persistable Postgres `uuid`?" used by both the
 * shared [CollectionRepository] guard and the flat iOS bridge helper that the
 * collection picker consults.
 *
 * WHY THIS EXISTS: the `collection_spots.spot_id` (and the other id columns the
 * backend uses) are Postgres `uuid` columns with FKs to `public.spots`. A spot
 * id that is NOT a valid uuid — e.g. a [SampleData] OFFLINE-SEED spot
 * (historically `spot-bonanza` etc.) or any not-yet-persisted local spot — can
 * NEVER be a legal `collection_spots.spot_id`: sending it triggers a Postgres
 * `22P02` ("invalid input syntax for type uuid") 400. Validating the shape
 * shared-side lets every caller reject such ids BEFORE a remote call, with one
 * implementation rather than a Swift-side copy.
 *
 * NOTE (honest scope): a valid-uuid SHAPE is necessary but NOT sufficient for a
 * successful INSERT — the id must also be an existing `public.spots` row (the
 * FK). This guard only removes the syntax-level foot-gun; a syntactically valid
 * uuid that is not a DB row still fails the FK at the backend. That remote
 * success path is BLOCKED-until-session.
 */
internal object Uuid {

    /**
     * The canonical 8-4-4-4-12 lowercase/uppercase hex form, case-insensitive.
     * No version/variant nibble is enforced (any hex in those positions is
     * accepted) because Postgres `uuid` accepts any canonical-shaped hex, not
     * only RFC-4122 v4 — matching what the backend will actually accept.
     */
    private val UUID_REGEX = Regex(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
    )

    /**
     * Whether [id] is a non-blank, canonical-shaped uuid string. A blank /
     * `temp-…` / `spot-…` / any non-uuid id returns `false`.
     */
    fun isValid(id: String): Boolean = UUID_REGEX.matches(id)
}
