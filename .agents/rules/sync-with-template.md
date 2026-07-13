# Sync with Template

Files matching `includes`/`excludes` in `.github/sync-with-template.yml` are synced automatically from the template repository through PRs (label `sync-with-template`, auto-merged) created by the [sync-with-template action](https://github.com/remal-github-actions/sync-with-template). The template is the repository variable `TEMPLATE_REPOSITORY` (an `owner/repo` value, never an org variable), or GitHub's "generated from" relationship when it is unset. The root of all template chains is [remal/oss-template](https://github.com/remal/oss-template). Files that exist only in the current repo are never touched.

**IMPORTANT: treat every repo as a generated one; do not edit synced files.** The next sync reverts local edits. Before committing, read `.github/sync-with-template.yml` and check which changed files are synced. For changes to synced files, suggest making them in the template project(s) instead, or use an escape hatch:

- `.github/sync-with-template-local-transformations.yml` (never synced itself): per-file `ignore`/`delete`/replace/`script` transformations, see the [schema](https://github.com/remal-github-actions/sync-with-template/blob/main/local-transformations.schema.json). This is also how repos extend the sync config itself, since the config file is synced too.
- Modifiable sections: lines between `$$$sync-with-template-modifiable: <name> $$$` and `$$$sync-with-template-modifiable-end$$$` markers keep their local content across syncs. Keep the markers. Marker syntax ([modifiableSections.ts](https://github.com/remal-github-actions/sync-with-template/blob/main/src/internal/modifiableSections.ts)):

Notes:

- Closing a sync PR does not reject the change; the PR is recreated.
- Deleting or renaming a synced file in the template does not remove it downstream. Add the old path to the template's `.github/sync-with-template-delete.list`.
- Keep the `# sync-with-template: adjust` comment on `cron:` lines. The action deliberately shifts such crons per repo, so values differ from the template.
- Rule files live in `.agents/rules/` (`.claude/rules` is a symlink to it) and are synced, so add or edit rules in the template.
