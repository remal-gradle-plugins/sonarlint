package name.remal.gradle_plugins.sonarlint.communication.server;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

@RequiredArgsConstructor
class LogOutputViaConsumer extends AbstractLogOutput {

    private final LogMessageConsumer consumer;

    @Override
    @SneakyThrows
    protected void logImpl(String formattedMessage, org.slf4j.event.Level slf4jLevel) {
        consumer.accept(slf4jLevel, formattedMessage);
    }

}
