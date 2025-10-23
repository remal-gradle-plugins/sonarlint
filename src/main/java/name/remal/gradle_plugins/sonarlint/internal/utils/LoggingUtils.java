package name.remal.gradle_plugins.sonarlint.internal.utils;

import static lombok.AccessLevel.PRIVATE;
import static name.remal.gradle_plugins.sonarlint.internal.utils.AopUtils.withWrappedCalls;
import static name.remal.gradle_plugins.sonarlint.internal.utils.SimpleLoggingEventBuilder.newLoggingEvent;
import static name.remal.gradle_plugins.toolkit.ThrowableUtils.unwrapException;
import static org.slf4j.event.Level.DEBUG;
import static org.slf4j.event.Level.WARN;

import lombok.NoArgsConstructor;

@NoArgsConstructor(access = PRIVATE)
public abstract class LoggingUtils {

    @SuppressWarnings({"java:S2139", "AssignmentToCatchBlockParameter", "JavaTimeDefaultTimeZone"})
    public static <T> T withLoggedCalls(Class<T> interfaceClass, T object) {
        return withWrappedCalls(interfaceClass, object, realMethod -> {
            var logger = new AccumulatingLogger(interfaceClass);
            newLoggingEvent(DEBUG).message(
                "Calling %s",
                realMethod
            ).log(logger);

            try {
                var result = realMethod.call();
                newLoggingEvent(DEBUG).message(
                    "Successfully called %s",
                    realMethod
                ).log(logger);
                return result;

            } catch (Throwable exception) {
                exception = unwrapException(exception);
                newLoggingEvent(WARN).message(
                    "Exception was thrown by calling %s",
                    realMethod
                ).cause(exception).log(logger);
                throw exception;
            }
        });
    }

}
