import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    // Supabase backend integration: supabase-kt models are @Serializable
    // and SQLDelight backs the offline cache.
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
            // SQLDelight's native-driver (SQLiter) calls into the system SQLite
            // C library; the consuming iOS binary must link libsqlite3.
            // Exporting the linker flag on the framework means the iosApp Xcode
            // target needs NO manual "Other Linker Flags" change.
            linkerOpts("-lsqlite3")
        }
    }
    android {
       namespace = "com.example.thirdspace.shared"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
       withDeviceTestBuilder {
           sourceSetTreeName = "test"
       }.configure {
           instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
       }
    }
    sourceSets {
        commonMain.dependencies {
            // --- Supabase backend integration ---
            // BOM governs all supabase + transitively the expected versions.
            implementation(project.dependencies.platform(libs.supabase.bom))
            implementation(libs.supabase.auth)
            implementation(libs.supabase.postgrest)
            // Ktor engine-agnostic client core (platform engines added below).
            implementation(libs.ktor.client.core)
            // supabase-kt models are @Serializable.
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            // SQLDelight runtime (backs the offline cache).
            implementation(libs.sqldelight.runtime)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.sqldelight.android.driver)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.sqldelight.native.driver)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        // The SQLDelight in-memory JVM driver backs the real
        // LocalSpotCache round-trip tests on the Android HOST (JVM) test target.
        // It is JVM-only, so it cannot live in commonTest (which also compiles
        // for the iOS native target); the DB-backed tests therefore run on the
        // host target, while pure-logic/caching tests stay in commonTest and run
        // on BOTH targets against an in-memory fake SpotCache.
        getByName("androidHostTest").dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }
        getByName("androidDeviceTest").dependencies {
            implementation(libs.androidx.testRunner)
        }
    }
}

// --- Supabase backend integration: SQLDelight database ---
// SQLDelight generates the typed DB interface from the declared database. The
// offline-cache schema (cachedSpots / pendingSpotInserts / cachedPosts /
// cachedComments / pendingCommentInserts) lives at
// shared/src/commonMain/sqldelight/com/example/thirdspace/db/ThirdSpaceDb.sq.
sqldelight {
    databases {
        create("ThirdSpaceDb") {
            packageName.set("com.example.thirdspace.db")
        }
    }
}
