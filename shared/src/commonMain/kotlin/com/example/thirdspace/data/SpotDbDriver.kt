package com.example.thirdspace.data

import app.cash.sqldelight.db.SqlDriver

/**
 * Platform factory for the SQLDelight [SqlDriver] backing the offline spot
 * cache ([com.example.thirdspace.db.ThirdSpaceDb]).
 *
 * BOUNDARY RULE (see steering/architecture.md): the [SqlDriver] and the
 * generated SQLDelight types stay INTERNAL to the shared module — they never
 * cross the flat FeedBridge boundary to Swift. Only flat [Spot] / String / Int
 * / Bool / Double values ever reach the bridge.
 *
 * Actuals:
 * - iosMain:     `NativeSqliteDriver(ThirdSpaceDb.Schema, "thirdspace.db")`.
 * - androidMain: `AndroidSqliteDriver`, which needs a `Context`. Because
 *   `androidApp` is a non-functional placeholder this phase, the Android actual
 *   resolves the application context reflectively via `ActivityThread` so it
 *   COMPILES and is self-contained (no Context-injection wiring in the
 *   placeholder app). A real Android UI phase should replace this with an
 *   explicit context initializer. See the androidMain actual for details.
 *
 * Host/common TESTS do NOT use this expect/actual at all: they build an
 * in-memory `JdbcSqliteDriver` directly (see LocalSpotCacheTest), so the cache
 * and queue logic is unit-testable without a device or emulator.
 */
expect fun createSpotDbDriver(): SqlDriver
