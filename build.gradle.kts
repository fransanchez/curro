// Root build file — declares plugins with apply false (versions come from libs.versions.toml).
// The :app module applies the plugins it actually needs.
//
// Plugin application order in :app must follow Architect's notes A2:
//   android.application → kotlin.android → kotlin.compose → ksp → hilt → android-junit5 → ktlint → detekt
//
// Firebase Gradle plugins are declared here with apply false; the :app module applies them
// conditionally on google-services.json presence per US-008 Q3-Resolved.

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.android.junit5) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
    // SF-0.8 (US-008) — declared here apply false; :app applies them conditionally (Q3-Resolved).
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics.plugin) apply false
}
