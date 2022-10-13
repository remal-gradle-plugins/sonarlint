**Min supported Gradle version: <!--property:gradle-api.min-version-->6.7<!--/property-->**

# `name.remal.sonarlint` plugin

This plugin executes [SonarLint](https://www.sonarlint.org/) inspections without connecting to a SonarQube server. SonarLint's core JARs and plugins' JARs are downloaded via `sonarlintCore` and `sonarlintPlugins` configurations accordingly.

By default, the plugin uses these Sonar plugins:

* [`java`](https://redirect.sonarsource.com/plugins/java.html)
* [`kotlin`](https://redirect.sonarsource.com/plugins/kotlin.html)
* [`html`](https://redirect.sonarsource.com/plugins/html.html)
* [`javascript`](https://redirect.sonarsource.com/plugins/javascript.html)
* [`xml`](https://redirect.sonarsource.com/plugins/xml.html)

&nbsp;

If [`java`](https://docs.gradle.org/current/userguide/java_plugin.html) Gradle plugin is applied, `sonarlint*` task is created for each [`SourceSet`](https://docs.gradle.org/current/javadoc/org/gradle/api/tasks/SourceSet.html).
