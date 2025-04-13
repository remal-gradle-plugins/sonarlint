package name.remal.gradle_plugins.sonarlint;

import static lombok.AccessLevel.PRIVATE;

import lombok.NoArgsConstructor;
import org.gradle.api.JavaVersion;

@NoArgsConstructor(access = PRIVATE)
abstract class SonarLintConstants {

    public static final JavaVersion MIN_SUPPORTED_SONAR_RUNTIME_JAVA_VERSION = JavaVersion.VERSION_17;

}
