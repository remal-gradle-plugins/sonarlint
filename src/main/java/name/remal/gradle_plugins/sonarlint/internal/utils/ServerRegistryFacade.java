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
import name.remal.gradle_plugins.toolkit.CloseablesContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuperBuilder
@Getter
public class ServerRegistryFacade extends AbstractRegistryFacade implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(ServerRegistryFacade.class);


    @NonNull
    private final RmiSocketFactory socketFactory;


    @SneakyThrows
    public <T extends Remote> void bind(Class<T> interfaceClass, T implementation) {
        implementation = withLoggedCalls(interfaceClass, implementation);
        implementation = withDumpJacocoDataOnEveryCall(interfaceClass, implementation);

        logger.debug(
            "Exporting an {} RMI stub of {} at {} on any available port",
            registryName,
            interfaceClass,
            socketFactory.getBindAddr()
        );

        var stub = exportObject(implementation, 0, socketFactory, socketFactory);

        logger.info(
            "Exported an {} RMI stub of {} at {} on port {}",
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

}
