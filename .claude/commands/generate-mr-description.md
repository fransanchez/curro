---
description: Generate PR description from git diff
---
Generate a Pull Request description based on the current branch changes.

Arguments: `$ARGUMENTS` (optional: base branch, default `main`)

1. Get current branch name
2. Run `git diff $base...HEAD` and `git log $base...HEAD --oneline`
3. Analyze all changes (not just latest commit)
4. Generate PR description with: Summary, Changes (categorized), Files Changed, Testing checklist, Screenshots needed
5. Output in markdown format ready to paste
