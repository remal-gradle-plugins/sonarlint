package name.remal.gradle_plugins.sonarlint.internal.impl;

import org.slf4j.event.Level;

@FunctionalInterface
public interface LogMessageConsumer {

    void accept(Level level, String message) throws Exception;

}
