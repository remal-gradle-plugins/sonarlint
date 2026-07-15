# Gradle Repositories

- In every Gradle `repositories {}` block that declares `mavenCentral()` or `gradlePluginPortal()`, add the CI-gated Google Maven Central mirror as the first repository:
  ```
  if (System.getenv('CI') == 'true') {
      maven {
          name = "googleMavenCentralMirror"
          url = uri("https://maven-central.storage-download.googleapis.com/maven2/")
          mavenContent { releasesOnly() }
      }
  }
  ```
- The mirror is not a full mirror of Maven Central. It is synced on a regular basis, so very new artifacts can be missing. Keep `mavenCentral()` as a fallback for that reason.
- Declare `gradlePluginPortal()` before `mavenCentral()`.
- In project-scope blocks where `project.isRunningOnCi` is available, guard the mirror with `if (project.isRunningOnCi)` instead. In `buildscript {}` and `settings` blocks, use `System.getenv('CI') == 'true'`.
- In Kotlin DSL (`*.gradle.kts`), use `if (System.getenv("CI") == "true")` with the same `maven { ... }` body.
- In functional tests (Gradle TestKit), add Maven Central to a generated build file by calling `MavenCentralRepositoryUtils.addMavenCentralRepository(content)` (in the `toolkit.testkit` module) instead of writing `mavenCentral()` and the mirror by hand. It emits the CI-gated mirror followed by `mavenCentral()`.
- In unit/component tests, add Maven Central to a project's `RepositoryHandler` by calling `RepositoryHandlerUtils.addMavenCentralRepository(repositories)` (in the `toolkit.testkit` module) instead of configuring the mirror and `mavenCentral()` by hand. It adds the CI-gated mirror followed by `mavenCentral()`.
