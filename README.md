**Min supported Gradle version: <!--property:gradle-api.min-version-->6.7<!--/property-->**

# `name.remal.sonarlint` plugin

This plugin executes [SonarLint](https://www.sonarlint.org/) inspections without connecting to a SonarQube server.

The plugin uses these Sonar plugins:
<!--sonar-plugins-list-->

* [`java`](https://rules.sonarsource.com/java)
* [`kotlin`](https://rules.sonarsource.com/kotlin)
* [`ruby`](https://rules.sonarsource.com/ruby)
* [`scala`](https://rules.sonarsource.com/scala)
* [`xml`](https://rules.sonarsource.com/xml)
* [`javascript`](https://rules.sonarsource.com/javascript)

<!--/sonar-plugins-list-->

For every [`SourceSet`](https://docs.gradle.org/current/javadoc/org/gradle/api/tasks/SourceSet.html) a SonarLint task is created by default.

## Configuration

```groovy
sonarLint {
  rules {
    enable(
      'java:S100', // Enable `java:S100` rule (that is disabled by default)
      'java:S101', // Enable `java:S101` rule (that is disabled by default)
    )
    enabled = ['java:S100'] // `enabled` - a mutable collection of enabled rules

    disable(
      'java:S1000', // Disable `java:S1000` rule
      'java:S1001', // Disable `java:S1001` rule
    )
    disabled = ['java:S1000'] // `disabled` - a mutable collection of disabled rules

    rule('java:S100') {
      property('format', '^[a-z][a-zA-Z0-9]*$') // Configure `format` property for `java:S100` rule
      properties = ['format': '^[a-z][a-zA-Z0-9]*$'] // `properties` - a mutable map of rule properties
    }
  }

  sonarProperty('sonar.nodejs.executable', '/usr/bin/node') // configure Node.js executable path via `sonar.nodejs.executable` Sonar property
  sonarProperties = ['sonar.nodejs.executable': '/usr/bin/node'] // `sonarProperties` - a mutable map of Sonar properties

  testSourceSets = sourceSets.matching { true } // Which source-sets contain test sources. Source-sets created by plugins like `name.remal.test-source-sets` are automatically integrated. Most probably, you don't have to configure anything yourself.

  // `sonarLint` extension extends `CodeQualityExtension` (see https://docs.gradle.org/current/javadoc/org/gradle/api/plugins/quality/CodeQualityExtension.html).
  // You can use all fields of `CodeQualityExtension` the same way as for `checkstyle`, for example.
}
```

For every property value (for both Sonar properties and rule properties) you can use `project.provider { ... }` to set a lazy value that will be calculated when a SonarLint task is executed. For example:

```groovy
sonarLint {
  sonarProperty('sonar.nodejs.executable', project.provider { '/usr/bin/node' })
}
```

## Help tasks

Two additional help tasks a created:

1. `sonarLintProperties` - displays Sonar properties that can be configured via `sonarLint.sonarProperties`.
2. `sonarLintRules` - displays all Sonar rules available, their description and their properties.
