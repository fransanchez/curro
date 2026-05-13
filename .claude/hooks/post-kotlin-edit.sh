#!/bin/bash
# Post-edit hook: run ktlint format on edited Kotlin files
# This runs after every Edit/Write on Kotlin files

FILE="$CLAUDE_FILE_PATH"

if [[ "$FILE" == *.kt || "$FILE" == *.kts ]]; then
    # Only format if ktlint is available via Gradle
    if [ -f "./gradlew" ]; then
        ./gradlew ktlintFormat -q 2>/dev/null || true
    fi
fi
