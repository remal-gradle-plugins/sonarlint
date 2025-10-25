package name.remal.gradle_plugins.sonarlint.internal.utils;

import static name.remal.gradle_plugins.sonarlint.internal.utils.LoggingUtils.withLoggedCalls;

import java.rmi.Remote;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.experimental.SuperBuilder;
import name.remal.gradle_plugins.sonarlint.internal.client.SonarLintClient;

@SuperBuilder
@Getter
public class ClientRegistryFacade extends AbstractRegistryFacade {

    private static final AccumulatingLogger logger = new AccumulatingLogger(SonarLintClient.class);


    @SneakyThrows
    public <T extends Remote> T lookup(Class<T> interfaceClass) {
        logger.debug(
            "Looking up for a stub of %s from %s RMI registry (%s)",
            interfaceClass,
            registryName,
            socketAddress
        );

        var remote = registry.lookup(getRemoteName(interfaceClass));
        var stub = interfaceClass.cast(remote);

        logger.info(
            "Retrieved a stub of %s from %s RMI registry (%s)",
            interfaceClass,
            registryName,
            socketAddress
        );

        stub = withLoggedCalls("RMI client", interfaceClass, stub);
        stub = logger.wrapCalls(interfaceClass, stub);

        return stub;
    }

}
