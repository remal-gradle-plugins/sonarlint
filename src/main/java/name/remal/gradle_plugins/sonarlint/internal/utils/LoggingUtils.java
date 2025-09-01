package name.remal.gradle_plugins.sonarlint.internal.utils;

import static lombok.AccessLevel.PRIVATE;
import static name.remal.gradle_plugins.sonarlint.internal.utils.AopUtils.withWrappedCalls;
import static name.remal.gradle_plugins.toolkit.ThrowableUtils.unwrapException;

import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

@NoArgsConstructor(access = PRIVATE)
public abstract class LoggingUtils {

    /**
     * Replaces {@link Logger#atLevel(Level)} which is only available from slf4j version >= 2.
     */
    public static void logAtLevel(Logger logger, Level level, String message) {
        switch (level) {
            case ERROR:
                logger.error(message);
                break;
            case WARN:
                logger.warn(message);
                break;
            case DEBUG:
                logger.debug(message);
                break;
            case TRACE:
                logger.trace(message);
                break;
            default:
                logger.info(message);
        }
    }

    /**
     * Replaces {@link Logger#atLevel(Level)} which is only available from slf4j version >= 2.
     */
    public static void logAtLevel(Logger logger, Level level, String message, Throwable exception) {
        switch (level) {
            case ERROR:
                logger.error(message, exception);
                break;
            case WARN:
                logger.warn(message, exception);
                break;
            case DEBUG:
                logger.debug(message, exception);
                break;
            case TRACE:
                logger.trace(message, exception);
                break;
            default:
                logger.info(message, exception);
        }
    }

    @SuppressWarnings({"java:S2139", "StringConcatenationArgumentToLogCall"})
    public static <T> T withLoggedCalls(Class<T> interfaceClass, T object) {
        return withWrappedCalls(interfaceClass, object, realMethod -> {
            var logger = LoggerFactory.getLogger(interfaceClass);
            logger.warn("Calling {}", realMethod);

            try {
                var result = realMethod.call();
                logger.warn("Successfully called {}", realMethod);
                return result;

            } catch (Throwable exception) {
                exception = unwrapException(exception);
                logger.warn(
                    "Exception was thrown by calling " + realMethod,
                    exception
                );
                throw exception;
            }
        });
    }

}
