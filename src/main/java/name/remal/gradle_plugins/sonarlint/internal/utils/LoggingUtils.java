package name.remal.gradle_plugins.sonarlint.internal.utils;

import static lombok.AccessLevel.PRIVATE;
import static name.remal.gradle_plugins.sonarlint.internal.utils.AopUtils.withWrappedCalls;
import static name.remal.gradle_plugins.sonarlint.internal.utils.SimpleLoggingEventBuilder.newLoggingEvent;
import static name.remal.gradle_plugins.toolkit.InTestFlags.isInTest;
import static name.remal.gradle_plugins.toolkit.ThrowableUtils.unwrapException;
import static org.slf4j.event.Level.DEBUG;
import static org.slf4j.event.Level.WARN;

import java.time.LocalTime;
import lombok.NoArgsConstructor;
import org.slf4j.LoggerFactory;

@NoArgsConstructor(access = PRIVATE)
public abstract class LoggingUtils {

    @SuppressWarnings({"java:S2139", "AssignmentToCatchBlockParameter", "JavaTimeDefaultTimeZone"})
    public static <T> T withLoggedCalls(Class<T> interfaceClass, T object) {
        return withWrappedCalls(interfaceClass, object, realMethod -> {
            var logger = LoggerFactory.getLogger(interfaceClass);
            newLoggingEvent(DEBUG, isInTest() ? WARN : null).message(
                "%s: Calling %s",
                LocalTime.now(),
                realMethod
            ).log(logger);

            try {
                var result = realMethod.call();
                newLoggingEvent(DEBUG, isInTest() ? WARN : null).message(
                    "%s: Successfully called %s",
                    LocalTime.now(),
                    realMethod
                ).log(logger);
                return result;

            } catch (Throwable exception) {
                exception = unwrapException(exception);
                newLoggingEvent(WARN).message(
                    "%s: Exception was thrown by calling %s",
                    LocalTime.now(),
                    realMethod
                ).cause(exception).log(logger);
                throw exception;
            }
        });
    }

}
