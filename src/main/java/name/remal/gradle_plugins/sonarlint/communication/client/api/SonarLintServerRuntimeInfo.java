package name.remal.gradle_plugins.sonarlint.communication.client.api;

import java.net.InetSocketAddress;
import java.rmi.Remote;
import java.rmi.RemoteException;

public interface SonarLintServerRuntimeInfo extends Remote {

    void reportServerRegistrySocketAddress(InetSocketAddress socketAddress) throws RemoteException;

}
