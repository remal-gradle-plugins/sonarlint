package name.remal.gradle_plugins.sonarlint;

import static lombok.AccessLevel.PRIVATE;

import lombok.NoArgsConstructor;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
@NoArgsConstructor(access = PRIVATE)
public abstract class SonarLintConstants {

    public static final int MIN_SUPPORTED_SONAR_RUNTIME_JAVA_VERSION = 21;

}
