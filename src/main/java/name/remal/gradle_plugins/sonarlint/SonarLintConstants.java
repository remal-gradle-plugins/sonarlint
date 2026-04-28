package name.remal.gradle_plugins.sonarlint;

import static lombok.AccessLevel.PRIVATE;

import lombok.NoArgsConstructor;

@NoArgsConstructor(access = PRIVATE)
abstract class SonarLintConstants {

    public static final int MIN_SUPPORTED_SONAR_RUNTIME_JAVA_VERSION = 17;

}
