**Tested on Java LTS versions from <!--property:java-runtime.min-version-->11<!--/property--> to <!--property:java-runtime.max-version-->25<!--/property-->.**

**Tested on Gradle versions from <!--property:gradle-api.min-version-->7.5<!--/property--> to <!--property:gradle-api.max-version-->9.2.0-rc-1<!--/property-->.**

# `name.remal.sonarlint` plugin

[![configuration cache: supported from v3.2](https://img.shields.io/static/v1?label=configuration%20cache&message=supported+from+v3.2&color=success)](https://docs.gradle.org/current/userguide/configuration_cache.html)

Usage:

<!--plugin-usage:name.remal.sonarlint-->
```groovy
plugins {
    id 'name.remal.sonarlint' version '7.0.0-rc-2'
}
```
<!--/plugin-usage-->

&nbsp;

This plugin executes [SonarLint](https://www.sonarlint.org/) inspections without connecting to a SonarQube server.

The plugin uses these Sonar plugins:
<!--sonar-plugins-list-->

* [Azure Resource Manager](https://rules.sonarsource.com/azureresourcemanager/)
* [CloudFormation](https://rules.sonarsource.com/cloudformation/)
* [CSS](https://rules.sonarsource.com/css/)
* [Docker](https://rules.sonarsource.com/docker/)
* [Java](https://rules.sonarsource.com/java/)
* [JavaScript](https://rules.sonarsource.com/javascript/)
* [JSON](https://rules.sonarsource.com/json/)
* JSP
* [Kotlin](https://rules.sonarsource.com/kotlin/)
* [Kubernetes](https://rules.sonarsource.com/kubernetes/)
* [Scala](https://rules.sonarsource.com/scala/)
* [Secrets](https://rules.sonarsource.com/secrets/)
* [Terraform](https://rules.sonarsource.com/terraform/)
* [Text](https://rules.sonarsource.com/text/)
* [TypeScript](https://rules.sonarsource.com/typescript/)
* [HTML](https://rules.sonarsource.com/web/)
* [XML](https://rules.sonarsource.com/xml/)
* [YAML](https://rules.sonarsource.com/yaml/)

<!--/sonar-plugins-list-->

For every [`SourceSet`](https://docs.gradle.org/current/javadoc/org/gradle/api/tasks/SourceSet.html), a SonarLint task is created by default.

## Infra languages are excluded by default

Infra languages are excluded by default:
<!--iterable-property:infraLanguageNames-->
* Azure Resource Manager
* CloudFormation
* Docker
* JSON
* Kubernetes
* Terraform
* YAML
<!--/iterable-property-->

Infra code is often stored in the same repository as microservices, but typically outside the `src` directory.
By default, this plugin generates `sonarlint*` tasks only for `src/*` directories.

Even if infra languages were included, they wouldn't be checked unless infra code is placed inside a `src/*` directory.
For this reason, infra languages are excluded by default.

To include infra languages, use this configuration: `sonarLint.languages.includeInfra = true`

Also, a single infra language can be included like this: `sonarLint.languages.include('CloudFormation')`

## Frontend languages are excluded by default

Frontend languages are excluded by default:
<!--iterable-property:frontendLanguageNames-->
* CSS
* HTML
* JSP
* JavaScript
* TypeScript
<!--/iterable-property-->

Frontend code is usually built with Node.js, not Gradle.
Additionally, SonarLint checks for frontend languages are slow when run on single files, making incremental verification inefficient.

For these reasons, frontend languages are excluded by default.

To include frontend languages, use this configuration: `sonarLint.languages.includeFrontend = true`

Also, a single frontend language can be included like this: `sonarLint.languages.include('JavaScript')`

## Configuration

```groovy
sonarLint {
  isGeneratedCodeIgnored = false // `true` by default, set to `false` to validate generated code (code inside `./build/`)

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

  languages {
    include('java', 'kotlin') // Enable Java and Kotlin languages only, all other languages become disabled
    exclude('java', 'kotlin') // Disable Java and Kotlin languages, all other languages remain enabled

    includeInfra = true // Include infra languages (like CloudFormation) that are excluded by default
    includeFrontend = true // Include frontend languages (like JavaScript) that are excluded by default
  }

  sonarProperty('sonar.html.file.suffixes', '.custom-html') // Configure `sonar.html.file.suffixes` Sonar property
  sonarProperties = ['sonar.html.file.suffixes': '.custom-html'] // `sonarProperties` - a mutable map of Sonar properties

  ignoredPaths.add('**/dto/**') // Ignore all files which relative path matches `**/dto/**` glob for all rules
  rules {
    rule('java:S100') {
      ignoredPaths.add('**/dto/**') // Ignore all files which relative path matches `**/dto/**` glob for rule `java:S100`
    }
  }

  // Which source sets contain test sources.
  // Source sets created by plugins like `jvm-test-suite`, `java-test-fixtures`, or `name.remal.test-source-sets` are automatically handled.
  // Most likely, you don't have to configure anything yourself.
  testSourceSets = sourceSets.matching { true }

  logging {
    withDescription = false // Hide rule descriptions from console output
  }
}
```

For every property value (for both Sonar properties and rule properties) you can use `project.provider { ... }` to set a lazy value that will be calculated when a SonarLint task is executed. For example:

```groovy
sonarLint {
  sonarProperty('sonar.html.file.suffixes', project.provider { '.custom-html' })
}
```

## Help tasks

Two additional help tasks are created:

1. `sonarLintProperties` - displays Sonar properties that can be configured via `sonarLint.sonarProperties`
   Properties of plugins for disabled languages are not shown.
2. `sonarLintRules` - displays all Sonar rules available, their description and their properties.
   Rules of disabled languages are not shown.

# Migration guide

## Version 6.* to 7.*

No API changes were made, no migration needed.

Version 7 improves overall build time if the build contains multiple SonarLint tasks.

Now, the plugin starts an instance of SonarLint "server" - a process that executes checks in parallel.
It gives a performance boost because we don't need to initialize SonarLint infra again and again.

## Version 5.* to 6.*

* Min Gradle version was raised to 7.5 (from 7.1)
* `sonarLint` extension no longer extends [`CodeQualityExtension`](https://docs.gradle.org/current/javadoc/org/gradle/api/plugins/quality/CodeQualityExtension.html)
* `sonarLint.nodeJs` was removed
* `sonarLint.logging.hideWarnings` was removed
* Infra languages (like CloudFormation) are disabled by default.
  Infra languages can be enabled all at the time by using `sonarLint.languages.includeInfra = true`
  or individually (e.g. `sonarLint.languages.include('CloudFormation')`)
* Frontend languages (like JavaScript) are disabled by default.
  Frontend languages can be enabled all at the time by using `sonarLint.languages.includeFrontend = true`
  or individually (e.g. `sonarLint.languages.include('JavaScript')`)
* Node.js detection was removed.
  Instead, [JavaScript](https://rules.sonarsource.com/javascript/) plugin with embedded Node.js is loaded.
* `SonarLint` task was reworked.
  If you use this type directly in your Gradle file, you'll need to apply some changes.
  Consider using `sonarLint` extension only.

## Version 4.* to 5.*

The minimum Java version is 11 (from 8).

## Version 3.* to 4.*

Gradle 6 support was removed.

## Version 2.* to 3.*

Package name was changed from `name.remal.gradleplugins.sonarlint` to `name.remal.gradle_plugins.sonarlint`.
