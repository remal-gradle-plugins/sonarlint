package name.remal.gradle_plugins.sonarlint.internal.utils;

import static java.rmi.server.UnicastRemoteObject.exportObject;
import static lombok.AccessLevel.NONE;
import static name.remal.gradle_plugins.sonarlint.internal.utils.JacocoUtils.withDumpJacocoDataOnEveryCall;
import static name.remal.gradle_plugins.sonarlint.internal.utils.LoggingUtils.withLoggedCalls;
import static name.remal.gradle_plugins.sonarlint.internal.utils.RemoteObjectUtils.unexportObject;

import java.rmi.Remote;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.experimental.SuperBuilder;
import name.remal.gradle_plugins.sonarlint.internal.client.SonarLintClient;
import name.remal.gradle_plugins.toolkit.CloseablesContainer;

@SuperBuilder
@Getter
public class ServerRegistryFacade extends AbstractRegistryFacade implements AutoCloseable {

    private static final AccumulatingLogger logger = new AccumulatingLogger(SonarLintClient.class);


    @NonNull
    private final RmiSocketFactory socketFactory;


    @SneakyThrows
    public <T extends Remote> void bind(Class<T> interfaceClass, T implementation) {
        implementation = withLoggedCalls("RMI server", interfaceClass, implementation);
        implementation = logger.wrapCalls(interfaceClass, implementation);

        implementation = withDumpJacocoDataOnEveryCall(interfaceClass, implementation);
        keepHardReferenceOnImplementation(implementation);

        logger.debug(
            "Exporting an %s RMI stub of %s at %s on any available port",
            registryName,
            interfaceClass,
            socketFactory.getBindAddr()
        );

        var stub = exportObject(implementation, 0, socketFactory, socketFactory);

        logger.info(
            "Exported an %s RMI stub of %s at %s on port %s",
            registryName,
            interfaceClass,
            socketFactory.getBindAddr(),
            socketFactory.getLastUsedPort()
        );

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

    private void keepHardReferenceOnImplementation(Remote remote) {
        closeables.registerCloseable(() -> {
            if (remote instanceof AutoCloseable) {
                ((AutoCloseable) remote).close();
            }
        });
    }

}
