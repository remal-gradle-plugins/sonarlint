package name.remal.gradle_plugins.sonarlint.internal.utils;

import static java.lang.String.format;
import static lombok.AccessLevel.PRIVATE;
import static name.remal.gradle_plugins.sonarlint.internal.utils.AvailablePorts.getAvailablePort;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.ExportException;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import name.remal.gradle_plugins.sonarlint.internal.client.SonarLintClient;

@NoArgsConstructor(access = PRIVATE)
public abstract class RegistryFactory {

    private static final int REGISTRY_START_ATTEMPTS = 25;
    private static final String REGISTRY_START_FAILED_MESSAGE = format(
        "Failed to bind RMI Registry after %s attempts",
        REGISTRY_START_ATTEMPTS
    );


    private static final AccumulatingLogger logger = new AccumulatingLogger(SonarLintClient.class);


    @SneakyThrows
    public static ServerRegistryFacade createRegistryOnAvailablePort(String registryName, InetAddress address) {
        logger.debug("Creating %s RMI registry at %s on any available port", registryName, address);
        var socketFactory = new RmiSocketFactory(address);

        for (var attempt = 1; attempt <= REGISTRY_START_ATTEMPTS; attempt++) {
            var port = getAvailablePort(socketFactory.getBindAddr());
            try {
                var registry = LocateRegistry.createRegistry(port, socketFactory, socketFactory);
                var socketAddress = new InetSocketAddress(socketFactory.getBindAddr(), port);
                logger.info("%s RMI registry created at %s", registryName, socketAddress);
                return ServerRegistryFacade.builder()
                    .registryName(registryName)
                    .registry(registry)
                    .socketAddress(socketAddress)
                    .socketFactory(socketFactory)
                    .build();

            } catch (ExportException e) {
                if (attempt >= REGISTRY_START_ATTEMPTS) {
                    throw new RegistryFactoryException(REGISTRY_START_FAILED_MESSAGE, e);
                }
            }
        }

        throw new RegistryFactoryException(REGISTRY_START_FAILED_MESSAGE);
    }


    @SneakyThrows
    public static ClientRegistryFacade connectToRegistry(String registryName, InetSocketAddress socketAddress) {
        logger.debug("Connecting to %s RMI registry at %s", registryName, socketAddress);
        var registry = LocateRegistry.getRegistry(
            socketAddress.getAddress().getHostAddress(),
            socketAddress.getPort()
        );

        logger.info("Connected to %s RMI registry at %s", registryName, socketAddress);
        return ClientRegistryFacade.builder()
            .registryName(registryName)
            .registry(registry)
            .socketAddress(socketAddress)
            .build();
    }

}
