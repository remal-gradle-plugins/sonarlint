package name.remal.gradle_plugins.sonarlint.internal.server.api;

import java.rmi.Remote;
import java.rmi.RemoteException;

@FunctionalInterface
public interface SonarLintLogSink extends Remote {

    void onMessage(String levelName, String message) throws RemoteException;

}
