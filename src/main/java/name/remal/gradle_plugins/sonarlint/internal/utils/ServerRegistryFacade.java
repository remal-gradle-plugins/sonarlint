package name.remal.gradle_plugins.sonarlint.internal.utils;

import static java.rmi.server.UnicastRemoteObject.exportObject;
import static lombok.AccessLevel.NONE;
import static name.remal.gradle_plugins.sonarlint.internal.utils.JacocoUtils.withDumpJacocoDataOnEveryCall;
import static name.remal.gradle_plugins.sonarlint.internal.utils.RemoteObjectUtils.unexportObject;

import java.rmi.Remote;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.experimental.SuperBuilder;
import name.remal.gradle_plugins.toolkit.CloseablesContainer;

@SuperBuilder
@Getter
public class ServerRegistryFacade extends AbstractRegistryFacade implements AutoCloseable {

    private final RmiSocketFactory socketFactory;


    @SneakyThrows
    public <T extends Remote> void bind(Class<T> interfaceClass, T implementation) {
        implementation = withDumpJacocoDataOnEveryCall(interfaceClass, implementation);
        var stub = exportObject(implementation, 0, socketFactory, socketFactory);
        registry.bind(getRemoteName(interfaceClass), stub);
        closeables.registerCloseable(() -> unexportObject(stub));
    }


    @Getter(NONE)
    private final CloseablesContainer closeables = new CloseablesContainer();

    @Override
    @SneakyThrows
    public void close() {
        try {
            closeables.close();

        } finally {
            unexportObject(registry);
        }
    }

}
