package name.remal.gradle_plugins.sonarlint.communication.utils;

import static java.rmi.server.UnicastRemoteObject.exportObject;
import static java.rmi.server.UnicastRemoteObject.unexportObject;
import static lombok.AccessLevel.NONE;

import java.rmi.Remote;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.experimental.SuperBuilder;
import name.remal.gradle_plugins.toolkit.ClosablesContainer;

@SuperBuilder
@Getter
public class ServerRegistryFacade extends AbstractRegistryFacade implements AutoCloseable {

    private final SocketFactory socketFactory;


    @SneakyThrows
    public <T extends Remote> void bind(Class<T> interfaceClass, T implementation) {
        var stub = exportObject(implementation, 0, socketFactory, socketFactory);
        registry.bind(getRemoteName(interfaceClass), stub);
        closeables.registerCloseable(() -> unexportObject(stub, true));
    }


    @Getter(NONE)
    private final ClosablesContainer closeables = new ClosablesContainer();

    @Override
    @SneakyThrows
    public void close() {
        try {
            closeables.close();

        } finally {
            unexportObject(registry, true);
        }
    }

}
