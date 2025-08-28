package name.remal.gradle_plugins.sonarlint.internal.server;

import org.slf4j.event.Level;

@FunctionalInterface
interface LogMessageConsumer {

    void accept(Level level, String message) throws Exception;

}
