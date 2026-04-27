# Commits

- Commits to the `main` branch must have one of these prefixes (the trailing space is part of the prefix):
  - `[release-later] ` - appears in the changelog
  - `[doc] ` - pure docs
  - `[no-changelog] ` - skipped
- Default to `[release-later]`. All Gradle code is user-facing, so it uses `[release-later]`. Ask if unsure.
- Commit messages MUST be a single line, no body.
