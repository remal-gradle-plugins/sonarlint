package name.remal.gradle_plugins.sonarlint.internal.impl;

import static lombok.AccessLevel.PRIVATE;
import static org.sonarsource.sonarlint.core.commons.log.LogOutput.Level.DEBUG;
import static org.sonarsource.sonarlint.core.commons.log.LogOutput.Level.ERROR;
import static org.sonarsource.sonarlint.core.commons.log.LogOutput.Level.OFF;
import static org.sonarsource.sonarlint.core.commons.log.LogOutput.Level.TRACE;
import static org.sonarsource.sonarlint.core.commons.log.LogOutput.Level.WARN;

import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NoArgsConstructor(access = PRIVATE)
class LogOutputViaSlf4j extends AbstractLogOutput {

    public static final LogOutputViaSlf4j LOG_OUTPUT_VIA_SLF4J = new LogOutputViaSlf4j();


    private static final Logger logger = LoggerFactory.getLogger(LogOutputViaSlf4j.class);

    @Override
    protected void logImpl(String formattedMessage, Level level) {
        if (level == OFF) {
            // do nothing
        } else if (level == ERROR) {
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
