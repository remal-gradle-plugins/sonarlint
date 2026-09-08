# Toolkit Annotations

Annotations from the toolkit repository (`name.remal.gradle_plugins.toolkit`).

## Test version filtering (`toolkit.testkit` module, package `...toolkit.testkit`)

JUnit 5 execution conditions via `@ExtendWith`. Target a test class or method, `@Inherited`:

- `@MinTestableJavaVersion(n)` / `@MaxTestableJavaVersion(n)`: run only when the test JVM Java version is in range. The test JVM runs on `java-runtime.version`. The build also bytecode-scans test classes (`gradle/test.gradle`) and excludes mismatching ones from Test task detection, so an annotated class may reference APIs absent on other Java versions.
- `@MinTestableGradleVersion("x.y")` / `@MaxTestableGradleVersion("x.y")`: same, against the Gradle version under test (`GradleVersion.current()`, driven by `gradle-api.version`). Base versions are compared.
- `@MinTestableVersion(module = "m", version = "x.y")` / `@MaxTestableVersion(...)`: repeatable. Compares against the `<module>.module-version` system property of the test JVM; the condition throws when the property is not set.

## Test wiring (`toolkit.testkit` module)

- `@ApplyPlugin("plugin-id")` or `@ApplyPlugin(type = MyPlugin.class)` on a test parameter: applies the plugin to the `Project` injected by `GradleProjectExtension`. Repeatable.
- `@ChildProjectOf("parentParameterName")` on a test parameter: injects a child project of the project injected into the named parameter.

## Compatibility markers (`toolkit` module)

Informative only. No code reads them (verified across all repos, including bytecode scans):

- `@MinCompatibleJavaVersion(n)` / `@MaxCompatibleJavaVersion(n)` / `@MinCompatibleGradleVersion("x.y")` / `@MaxCompatibleGradleVersion("x.y")`: the annotated element works only within the given Java/Gradle version range. The call site must guard with a version check.
- `@ForBackwardCompatibilityWithGradle("x.y")`: the annotated code exists for backward compatibility with the given Gradle version.

## Build-consumed markers (`toolkit-annotations` module)

Bytecode-scanned by `gradle/gradle-plugin-collect-classes-relying-on-dependencies.gradle` and recorded in `gradle-plugin-api-dependencies.txt`:

- `@ReliesOnInternalGradleApi`: the code uses Gradle internal API. The build auto-stamps classes that use internal API without the annotation.
- `@ReliesOnExternalDependency`: the code relies on an external (non-Gradle) dependency.

`@ConfigurationPhaseOnly`, `@DynamicCompatibilityCandidate`, and `internal/@Generated` are informative markers with no build consumer.
