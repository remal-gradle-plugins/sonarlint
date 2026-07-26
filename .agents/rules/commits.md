# Commits

- Prefer committing directly to `main` unless asked otherwise.
- **Before writing the message, check the commit's target, BLOCKING.** Committing to a PR/feature branch: no prefix, ever. Committing directly to `main` (not via PR): one of the prefixes below is mandatory.
- Direct commits to `main` (not via PR) must start with one of these prefixes (the trailing space is part of the prefix):
  - `[release-later] ` - appears in the changelog
  - `[doc] ` - pure docs
  - `[no-changelog] ` - skipped
- A direct commit to `main` without one of these prefixes breaks automatic release: the next release has to be done manually. This is sometimes wanted, so honor an explicit instruction to omit the prefix.
- The prefix is NOT used on PR titles or feature-branch commits.
- Default to `[release-later]` for direct-to-`main` commits. All Gradle code is user-facing, so it uses `[release-later]`. Use `[no-changelog]` when only test code is changed. Ask if unsure.
- Commit messages MUST be a single line, no body.
