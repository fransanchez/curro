// Root build file — declares plugins with apply false (versions come from libs.versions.toml).
// The :app module applies the plugins it actually needs.
//
// Plugin application order in :app must follow Architect's notes A2:
//   android.application → kotlin.android → kotlin.compose → ksp → hilt → android-junit5 → ktlint → detekt
//
// Reserved plugins (SF-0.8 uncomments these):
//   alias(libs.plugins.google.services) apply false
//   alias(libs.plugins.firebase.crashlytics.plugin) apply false

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.android.junit5) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
}
