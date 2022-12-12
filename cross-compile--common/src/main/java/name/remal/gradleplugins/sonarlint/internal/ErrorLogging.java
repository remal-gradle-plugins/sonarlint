package name.remal.gradleplugins.sonarlint.internal;

import static java.util.Collections.newSetFromMap;
import static lombok.AccessLevel.PRIVATE;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.CustomLog;
import lombok.NoArgsConstructor;

@CustomLog
@NoArgsConstructor(access = PRIVATE)
abstract class ErrorLogging {

    private static final Set<String> LOGGED_MESSAGES = newSetFromMap(new ConcurrentHashMap<>());

    public static void logError(String message) {
        if (LOGGED_MESSAGES.add(message)) {
            logger.error(message);
        }
    }

}
