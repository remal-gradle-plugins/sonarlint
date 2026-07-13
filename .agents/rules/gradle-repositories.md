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
- Declare `gradlePluginPortal()` before `mavenCentral()`.
- In project-scope blocks where `project.isRunningOnCi` is available, guard the mirror with `if (project.isRunningOnCi)` instead. In `buildscript {}` and `settings` blocks, use `System.getenv('CI') == 'true'`.
- In Kotlin DSL (`*.gradle.kts`), use `if (System.getenv("CI") == "true")` with the same `maven { ... }` body.
