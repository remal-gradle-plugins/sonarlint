package name.remal.gradle_plugins.sonarlint.internal.utils;

import static java.lang.System.currentTimeMillis;

import java.util.Optional;
import org.immutables.value.Value;
import org.slf4j.event.Level;

@Value.Immutable
@Value.Style(
    stagedBuilder = true,
    optionalAcceptNullable = true
)
public interface LogMessage {

    @Value.Default
    default long getTimestamp() {
        return currentTimeMillis();
    }

    @Value.Default
    default String getThreadName() {
        return Thread.currentThread().getName();
    }

    String getLoggerName();

    Level getLevel();

    String getMessage();

    Optional<Throwable> getCause();

}
