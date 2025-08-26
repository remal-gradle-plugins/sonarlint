package name.remal.gradle_plugins.sonarlint.communication.server.api;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface SonarLintLifecycle extends Remote {

    void stop() throws RemoteException;

}
