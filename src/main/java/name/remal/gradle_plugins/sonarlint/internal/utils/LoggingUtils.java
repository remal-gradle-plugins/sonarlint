package name.remal.gradle_plugins.sonarlint.internal.utils;

import static lombok.AccessLevel.PRIVATE;

import lombok.NoArgsConstructor;
import org.slf4j.Logger;
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

}
