package com.example.thirdspace.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.example.thirdspace.db.ThirdSpaceDb

/**
 * iOS actual: a native SQLite driver persisting to `thirdspace.db` in the app's
 * standard SQLite location. Uses the generated [ThirdSpaceDb.Schema] so the
 * schema is created/migrated on first open.
 */
actual fun createSpotDbDriver(): SqlDriver =
    NativeSqliteDriver(ThirdSpaceDb.Schema, "thirdspace.db")
