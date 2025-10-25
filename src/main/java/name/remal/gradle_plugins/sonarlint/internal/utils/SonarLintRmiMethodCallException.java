package name.remal.gradle_plugins.sonarlint.internal.utils;

import static java.lang.String.format;

import name.remal.gradle_plugins.sonarlint.internal.utils.AopUtils.RealMethod;

public class SonarLintRmiMethodCallException extends SonarLintClientServerException {

    public SonarLintRmiMethodCallException(RealMethod realMethod, String debugInfo) {
        super(
            format(
                "An exception occurred while calling for an RMI method: %s"
                    + "%n%s",
                realMethod,
                debugInfo
            )
        );
    }

}
