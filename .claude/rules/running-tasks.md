# Running Tests

- To run tests against a specific Java or Gradle version, first compile all classes with the default version, then run the tests with compilation disabled:
  1. `./gradlew assemble allClasses`
  2. `./gradlew test -Pdisable-compilation=true -Pjava-runtime.version=N -Pgradle-api.version=M`
- Either `-Pjava-runtime.version` or `-Pgradle-api.version` can be omitted.
