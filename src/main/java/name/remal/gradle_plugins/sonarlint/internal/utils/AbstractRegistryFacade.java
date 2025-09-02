package name.remal.gradle_plugins.sonarlint.internal.utils;

import static java.lang.String.format;

import java.net.InetSocketAddress;
import java.rmi.Remote;
import java.rmi.registry.Registry;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
abstract class AbstractRegistryFacade {

    @NonNull
    protected final String registryName;

    @NonNull
    protected final Registry registry;

    @NonNull
    protected final InetSocketAddress socketAddress;


    protected static String getRemoteName(Class<? extends Remote> interfaceClass) {
        return interfaceClass.getName();
    }


    @Override
    public String toString() {
        return format(
            "%s[registryName=%s, socketAddress=%s]",
            getClass().getSimpleName(),
            registryName,
            socketAddress
        );
    }

}
