package name.remal.gradle_plugins.sonarlint.internal.server;

import static lombok.AccessLevel.PRIVATE;
import static name.remal.gradle_plugins.sonarlint.internal.utils.SimpleLoggingEventBuilder.newLoggingEvent;

import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NoArgsConstructor(access = PRIVATE)
class LogOutputViaSlf4j extends AbstractLogOutput {

    public static final LogOutputViaSlf4j LOG_OUTPUT_VIA_SLF4J = new LogOutputViaSlf4j();


    private static final Logger logger = LoggerFactory.getLogger(LogOutputViaSlf4j.class);

    @Override
    protected void logImpl(String formattedMessage, org.slf4j.event.Level slf4jLevel) {
        newLoggingEvent(slf4jLevel).message(formattedMessage).log(logger);
    }

}
