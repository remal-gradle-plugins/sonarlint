package name.remal.gradle_plugins.sonarlint.communication.server.api;

import java.rmi.Remote;
import java.rmi.RemoteException;
import org.slf4j.event.Level;

@FunctionalInterface
public interface SonarLintLogSink extends Remote {

    void onMessage(String loggerName, Level level, String message) throws RemoteException;

}
