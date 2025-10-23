package name.remal.gradle_plugins.sonarlint.internal.utils;

import static name.remal.gradle_plugins.sonarlint.internal.utils.SimpleLoggingEventBuilder.newLoggingEvent;

import java.rmi.RemoteException;
import name.remal.gradle_plugins.sonarlint.internal.server.api.SonarLintLogSink;
import org.gradle.api.Task;
import org.slf4j.Logger;

public class SonarLintTaskLogSink implements SonarLintLogSink {

    private final Logger logger;

    public SonarLintTaskLogSink(Task task) {
        this.logger = task.getLogger();
    }

    @Override
    public void onMessage(String levelName, String message) throws RemoteException {
        var level = org.slf4j.event.Level.valueOf(levelName);
        newLoggingEvent(level).message(message).log(logger);
    }

}
