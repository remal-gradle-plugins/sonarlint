package name.remal.gradle_plugins.sonarlint.communication.server;

import org.slf4j.event.Level;

@FunctionalInterface
interface LogMessageConsumer {

    void accept(Level level, String message) throws Exception;

}
