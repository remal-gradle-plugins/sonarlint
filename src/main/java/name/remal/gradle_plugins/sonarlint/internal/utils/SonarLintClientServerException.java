package name.remal.gradle_plugins.sonarlint.internal.utils;

import static java.lang.String.format;
import static name.remal.gradle_plugins.build_time_constants.api.BuildTimeConstants.getStringProperty;

import java.time.LocalTime;
import org.jspecify.annotations.Nullable;

@SuppressWarnings("JavaTimeDefaultTimeZone")
public abstract class SonarLintClientServerException extends RuntimeException {

    private static String enrichMessage(String message) {
        return format(
            "This exception is unexpected. Please report it here: %s/issues ."
                + " OS: %s %s %s."
                + " Local time: %s. %s",
            getStringProperty("repository.html-url"),
            System.getProperty("os.name"),
            System.getProperty("os.version"),
            System.getProperty("os.arch"),
            LocalTime.now(),
            message
        );
    }

    protected SonarLintClientServerException(String message) {
        super(enrichMessage(message));
    }

    protected SonarLintClientServerException(String message, @Nullable Throwable cause) {
        super(enrichMessage(message), cause);
    }

}
