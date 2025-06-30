package name.remal.gradle_plugins.sonarlint.internal.impl;

import static org.sonarsource.sonarlint.core.commons.log.LogOutput.Level.DEBUG;
import static org.sonarsource.sonarlint.core.commons.log.LogOutput.Level.ERROR;
import static org.sonarsource.sonarlint.core.commons.log.LogOutput.Level.OFF;
import static org.sonarsource.sonarlint.core.commons.log.LogOutput.Level.TRACE;
import static org.sonarsource.sonarlint.core.commons.log.LogOutput.Level.WARN;

import java.util.function.Consumer;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class LogOutputViaConsumer extends AbstractLogOutput {

    @NonNull
    private final Consumer<String> logMessagesConsumer;

    @Override
    protected void logImpl(String formattedMessage, Level level) {
        if (level == OFF) {
            // do nothing
        } else if (level == ERROR) {
            logMessagesConsumer.accept(formattedMessage);
        } else if (level == WARN) {
            logMessagesConsumer.accept(formattedMessage);
        } else if (level == DEBUG) {
            // do nothing
        } else if (level == TRACE) {
            // do nothing
        } else {
            logMessagesConsumer.accept(formattedMessage);
        }
    }

}
