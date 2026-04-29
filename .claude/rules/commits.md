# Commits

- Direct commits to `main` (not via PR) must start with one of these prefixes (the trailing space is part of the prefix):
  - `[release-later] ` - appears in the changelog
  - `[doc] ` - pure docs
  - `[no-changelog] ` - skipped
- The prefix is NOT used on PR titles or feature-branch commits.
- Default to `[release-later]`. All Gradle code is user-facing, so it uses `[release-later]`. Ask if unsure.
- Commit messages MUST be a single line, no body.
