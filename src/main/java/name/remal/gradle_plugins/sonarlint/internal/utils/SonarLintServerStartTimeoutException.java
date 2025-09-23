package name.remal.gradle_plugins.sonarlint.internal.utils;

import static java.lang.String.format;

import java.time.Duration;

public class SonarLintServerStartTimeoutException extends SonarLintClientServerException {

    public SonarLintServerStartTimeoutException(Duration startTimeout, String debugInfo) {
        super(format(
            "SonarLint server couldn't start within %s.%n%s",
            startTimeout,
            debugInfo
        ));
    }

}
