package name.remal.gradle_plugins.sonarlint.communication.utils;

import java.net.InetSocketAddress;
import java.rmi.Remote;
import java.rmi.registry.Registry;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
abstract class AbstractRegistryFacade {

    protected final Registry registry;

    protected final InetSocketAddress socketAddress;


    protected static String getRemoteName(Class<? extends Remote> interfaceClass) {
        return interfaceClass.getName();
    }

}
