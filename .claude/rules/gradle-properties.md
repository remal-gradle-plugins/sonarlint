# Gradle Properties

Set via `gradle.properties`, `-P` flags, system properties, or environment variables.

## Build properties

- `java-runtime.min-version` - Min supported Java version.
- `gradle-api.min-version` - Min supported Gradle version.
- `gradle-api.version` - Sets Gradle version for compilation and test execution.
- `java-runtime.version` - Sets Java version for compilation and test execution.
- `disable-compilation` - Disables all compile, delombok, and processResources tasks. Used for cross-version testing with pre-compiled classes.
