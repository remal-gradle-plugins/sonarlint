package name.remal.gradle_plugins.sonarlint.internal.impl;

import static lombok.AccessLevel.PRIVATE;

import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NoArgsConstructor(access = PRIVATE)
public class LogOutputViaSlf4j extends AbstractLogOutput {

    public static final LogOutputViaSlf4j LOG_OUTPUT_VIA_SLF4J = new LogOutputViaSlf4j();


    private static final Logger logger = LoggerFactory.getLogger(LogOutputViaSlf4j.class);

    @Override
    protected void logImpl(String formattedMessage, org.slf4j.event.Level slf4jLevel) {
        logger.atLevel(slf4jLevel).log(formattedMessage);
    }

}
