package name.remal.gradle_plugins.sonarlint.internal.impl;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

@RequiredArgsConstructor
public class LogOutputViaConsumer extends AbstractLogOutput {

    private final LogMessageConsumer consumer;

    @Override
    @SneakyThrows
    protected void logImpl(String formattedMessage, org.slf4j.event.Level slf4jLevel) {
        consumer.accept(slf4jLevel, formattedMessage);
    }

}
