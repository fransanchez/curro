---
description: Run tests
---
Run project tests.

Arguments: `$ARGUMENTS` (optional: `unit`, `ui`, `all`, or a specific test class)

If no argument or `all`: `./gradlew test`
If `unit`: `./gradlew test`
If `ui`: `./gradlew connectedAndroidTest`
If specific class: `./gradlew test --tests "*$ARGUMENTS*"`

Report test results summary.
