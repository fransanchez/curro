---
description: Build the Android project
---
Build the project using Gradle.

Arguments: `$ARGUMENTS` (optional: `debug`, `release`, `clean`)

If no argument or `debug`: `./gradlew assembleDebug`
If `release`: `./gradlew assembleRelease`
If `clean`: `./gradlew clean assembleDebug`

Report build result (success/failure with error summary).
