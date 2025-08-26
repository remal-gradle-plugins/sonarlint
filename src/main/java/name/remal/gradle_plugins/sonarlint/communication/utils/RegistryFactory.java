package name.remal.gradle_plugins.sonarlint.communication.utils;

import static java.lang.String.format;
import static lombok.AccessLevel.PRIVATE;
import static name.remal.gradle_plugins.sonarlint.communication.utils.AvailablePorts.getAvailablePort;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.ExportException;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import name.remal.gradle_plugins.sonarlint.communication.server.SonarLintServerException;

@NoArgsConstructor(access = PRIVATE)
public abstract class RegistryFactory {

    private static final int REGISTRY_START_ATTEMPTS = 25;
    private static final String REGISTRY_START_FAILED_MESSAGE = format(
        "Failed to bind RMI Registry after %s attempts",
        REGISTRY_START_ATTEMPTS
    );


    @SneakyThrows
    public static ServerRegistryFacade createRegistryOnAvailablePort(InetAddress address) {
        var socketFactory = new SocketFactory(address);

        for (var attempt = 1; attempt <= REGISTRY_START_ATTEMPTS; attempt++) {
            var port = getAvailablePort(socketFactory.getBindAddr());
            try {
                var registry = LocateRegistry.createRegistry(port, socketFactory, socketFactory);
                var socketAddress = new InetSocketAddress(socketFactory.getBindAddr(), port);
                return ServerRegistryFacade.builder()
                    .registry(registry)
                    .socketAddress(socketAddress)
                    .socketFactory(socketFactory)
                    .build();

            } catch (ExportException e) {
                if (attempt >= REGISTRY_START_ATTEMPTS) {
                    throw new SonarLintServerException(REGISTRY_START_FAILED_MESSAGE, e);
                }
            }
        }

        throw new SonarLintServerException(REGISTRY_START_FAILED_MESSAGE);
    }


    @SneakyThrows
    public static ClientRegistryFacade connectToRegistry(InetSocketAddress socketAddress) {
        var registry = LocateRegistry.getRegistry(
            socketAddress.getAddress().getHostAddress(),
            socketAddress.getPort()
        );
        return ClientRegistryFacade.builder()
            .registry(registry)
            .socketAddress(socketAddress)
            .build();
    }

}
