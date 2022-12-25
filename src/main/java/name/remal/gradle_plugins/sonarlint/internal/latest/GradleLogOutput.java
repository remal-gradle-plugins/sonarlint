package name.remal.gradle_plugins.sonarlint.internal.latest;

import static java.util.Collections.newSetFromMap;
import static org.sonarsource.sonarlint.core.commons.log.ClientLogOutput.Level.DEBUG;
import static org.sonarsource.sonarlint.core.commons.log.ClientLogOutput.Level.ERROR;
import static org.sonarsource.sonarlint.core.commons.log.ClientLogOutput.Level.TRACE;
import static org.sonarsource.sonarlint.core.commons.log.ClientLogOutput.Level.WARN;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.CustomLog;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;

@CustomLog
class GradleLogOutput implements ClientLogOutput {

    private static final Set<String> LOGGED_MESSAGE = newSetFromMap(new ConcurrentHashMap<>());

    @Override
    public void log(String formattedMessage, Level level) {
        if (!LOGGED_MESSAGE.add(formattedMessage)) {
            return;
        }

        if (formattedMessage.equals("No workDir in SonarLint")) {
            level = DEBUG;
        } else if (formattedMessage.startsWith("Plugin '") && formattedMessage.endsWith(". Skip loading it.")) {
            level = WARN;
        } else if (formattedMessage.endsWith(". Enable DEBUG mode to see them.")) {
            level = TRACE;
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
