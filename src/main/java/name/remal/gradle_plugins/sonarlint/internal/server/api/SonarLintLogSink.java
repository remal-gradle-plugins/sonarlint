package name.remal.gradle_plugins.sonarlint.internal.server.api;

import java.rmi.Remote;
import java.rmi.RemoteException;
import org.slf4j.event.Level;

@FunctionalInterface
public interface SonarLintLogSink extends Remote {

    void onMessage(Level level, String message) throws RemoteException;

}
