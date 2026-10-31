package com.example.thirdspace.data

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.example.thirdspace.db.ThirdSpaceDb

/**
 * Android actual for [createSpotDbDriver].
 *
 * CONTEXT NOTE (documented blocker + chosen approach): [AndroidSqliteDriver]
 * requires a [Context]. The KMP Android *library* plugin has no app entry point
 * here, and `androidApp` is a NON-FUNCTIONAL PLACEHOLDER this phase (no native
 * Android UI yet — see steering/architecture.md), so there is no clean place to
 * inject an application context.
 *
 * To keep this actual self-contained AND compiling (the binding requirement
 * this phase is "the androidMain actual must COMPILE even though androidApp is a
 * placeholder; it does not need to run in an Android app this phase"), the
 * context is resolved via a small initializer seam:
 *
 * - [SpotDbContext.application] is a nullable app-context holder. A real Android
 *   UI phase sets it once from `Application.onCreate()` (the clean path).
 * - Until then, [createSpotDbDriver] falls back to reflectively reading the
 *   current application via `android.app.ActivityThread.currentApplication()`,
 *   which is the standard "no-arg" app-context trick. This compiles with zero
 *   extra wiring and would work at runtime inside any Android process; it is
 *   only exercised when an Android UI phase actually runs this code.
 *
 * The iOS build is the one that ships this phase; this Android actual exists to
 * satisfy the expect/actual contract and compile cleanly.
 */
actual fun createSpotDbDriver(): SqlDriver {
    val context = SpotDbContext.application ?: currentApplicationContext()
    return AndroidSqliteDriver(ThirdSpaceDb.Schema, context, "thirdspace.db")
}

/**
 * Optional clean injection seam for a future Android UI phase: call
 * [SpotDbContext.init] once from `Application.onCreate()` to supply the app
 * context explicitly instead of relying on the reflective fallback.
 */
object SpotDbContext {
    @SuppressLint("StaticFieldLeak") // application context only (no activity leak)
    @Volatile
    var application: Context? = null
        private set

    /** Supply the application [context]; safe to call once at app start. */
    fun init(context: Context) {
        application = context.applicationContext
    }
}

/**
 * Reflectively resolve the current application context via
 * `ActivityThread.currentApplication()`. Returns the app [Context]; throws an
 * [IllegalStateException] with a clear message if no Android process context is
 * available (e.g. if this were ever called from a plain JVM host test — which
 * it is NOT, since host tests use the in-memory JdbcSqliteDriver directly).
 */
@SuppressLint("PrivateApi")
private fun currentApplicationContext(): Context {
    return try {
        val activityThread = Class.forName("android.app.ActivityThread")
        val method = activityThread.getMethod("currentApplication")
        (method.invoke(null) as? Application)?.applicationContext
            ?: error(
                "No Android application context available. Call SpotDbContext.init(context) " +
                    "from Application.onCreate() in the Android UI phase.",
            )
    } catch (t: Throwable) {
        throw IllegalStateException(
            "Could not resolve the Android application context for the SQLDelight driver. " +
                "Call SpotDbContext.init(context) from Application.onCreate().",
            t,
        )
    }
}
