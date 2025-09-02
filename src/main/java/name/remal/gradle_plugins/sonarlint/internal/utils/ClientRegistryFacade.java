package name.remal.gradle_plugins.sonarlint.internal.utils;

import static name.remal.gradle_plugins.sonarlint.internal.utils.LoggingUtils.withLoggedCalls;

import java.rmi.Remote;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.experimental.SuperBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuperBuilder
@Getter
public class ClientRegistryFacade extends AbstractRegistryFacade {

    private static final Logger logger = LoggerFactory.getLogger(ClientRegistryFacade.class);


    @SneakyThrows
    public <T extends Remote> T lookup(Class<T> interfaceClass) {
        logger.debug(
            "Looking up for a stub of {} from {} RMI registry ({})",
            interfaceClass,
            registryName,
            socketAddress
        );

        var remote = registry.lookup(getRemoteName(interfaceClass));
        var stub = interfaceClass.cast(remote);

        logger.info(
            "Retrieved a stub of {} from {} RMI registry ({})",
            interfaceClass,
            registryName,
            socketAddress
        );

        stub = withLoggedCalls(interfaceClass, stub);

        return stub;
    }

}
