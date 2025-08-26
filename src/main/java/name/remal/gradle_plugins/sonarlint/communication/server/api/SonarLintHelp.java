package name.remal.gradle_plugins.sonarlint.communication.server.api;

import java.rmi.Remote;
import java.rmi.RemoteException;
import name.remal.gradle_plugins.sonarlint.internal.PropertiesDocumentation;
import name.remal.gradle_plugins.sonarlint.internal.RulesDocumentation;

public interface SonarLintHelp extends Remote {

    PropertiesDocumentation getPropertiesDocumentation() throws RemoteException;

    RulesDocumentation getRulesDocumentation() throws RemoteException;

}
