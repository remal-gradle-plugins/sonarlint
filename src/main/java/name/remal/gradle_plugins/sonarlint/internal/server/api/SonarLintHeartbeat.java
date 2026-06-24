package name.remal.gradle_plugins.sonarlint.internal.server.api;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface SonarLintHeartbeat extends Remote {

    void ping() throws RemoteException;

}
