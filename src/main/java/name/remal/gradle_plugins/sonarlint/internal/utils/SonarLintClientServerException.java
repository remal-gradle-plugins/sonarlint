package name.remal.gradle_plugins.sonarlint.internal.utils;

import static java.lang.String.format;
import static name.remal.gradle_plugins.build_time_constants.api.BuildTimeConstants.getStringProperty;

import java.time.LocalTime;

@SuppressWarnings("JavaTimeDefaultTimeZone")
public abstract class SonarLintClientServerException extends RuntimeException {

    private static String enrichMessage(String message) {
        return format(
            "This exception is unexpected. Please report it here: %s/issues%n"
                + "Local time: %s.%n"
                + "OS: %s, OS version: %s, arch: %s.%n"
                + "%s",
            getStringProperty("repository.html-url"),
            LocalTime.now(),
            System.getProperty("os.name"),
            System.getProperty("os.version"),
            System.getProperty("os.arch"),
            message
        );
    }

    protected SonarLintClientServerException(String message) {
        super(enrichMessage(message));
    }

}
