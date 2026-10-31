plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    // Supabase backend integration (Phase A): serialization + SQLDelight plugins
    // declared here so the :shared module can apply them.
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.sqldelight) apply false
}