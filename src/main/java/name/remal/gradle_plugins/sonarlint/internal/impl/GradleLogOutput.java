package name.remal.gradle_plugins.sonarlint.internal.impl;

import static java.util.Collections.newSetFromMap;
import static java.util.Collections.unmodifiableMap;
import static lombok.AccessLevel.PRIVATE;
import static org.sonarsource.sonarlint.core.commons.log.ClientLogOutput.Level.DEBUG;
import static org.sonarsource.sonarlint.core.commons.log.ClientLogOutput.Level.ERROR;
import static org.sonarsource.sonarlint.core.commons.log.ClientLogOutput.Level.TRACE;
import static org.sonarsource.sonarlint.core.commons.log.ClientLogOutput.Level.WARN;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import lombok.CustomLog;
import lombok.NoArgsConstructor;
import lombok.val;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;

@CustomLog
@NoArgsConstructor(access = PRIVATE)
class GradleLogOutput implements ClientLogOutput {

    public static final GradleLogOutput GRADLE_LOG_OUTPUT = new GradleLogOutput();


    private static final Map<Pattern, Level> MESSAGE_LEVELS;

    static {
        Map<Pattern, Level> messageLevels = new LinkedHashMap<>();

        messageLevels.put(
            Pattern.compile("No workDir in SonarLint"),
            TRACE
        );

        messageLevels.put(
            Pattern.compile(".+\\. Enable DEBUG mode to see them\\."),
            TRACE
        );

        messageLevels.put(
            Pattern.compile("Plugin '[^']+' is excluded because"
                + " (none of languages '[^']+' are|language '[^']+' is not) enabled"
                + "\\. Skip loading it\\."
            ),
            DEBUG
        );

        messageLevels.put(
            Pattern.compile("Plugin '[^']+' is excluded .+\\. Skip loading it\\."),
            WARN
        );

        MESSAGE_LEVELS = unmodifiableMap(messageLevels);
    }


    private final Set<String> loggedMessages = newSetFromMap(new ConcurrentHashMap<>());

    @Override
    @SuppressWarnings("Slf4jFormatShouldBeConst")
    public void log(String formattedMessage, Level level) {
        formattedMessage = formattedMessage.trim();

        if (!loggedMessages.add(formattedMessage)) {
            return;
        }

        for (val entry : MESSAGE_LEVELS.entrySet()) {
            if (entry.getKey().matcher(formattedMessage).matches()) {
                level = entry.getValue();
                break;
            }
        }

        if (level == ERROR) {
            logger.error(formattedMessage);
        } else if (level == WARN) {
            logger.warn(formattedMessage);
        } else if (level == DEBUG) {
            logger.debug(formattedMessage);
        } else if (level == TRACE) {
            logger.trace(formattedMessage);
        } else {
            logger.info(formattedMessage);
        }
    }

}
