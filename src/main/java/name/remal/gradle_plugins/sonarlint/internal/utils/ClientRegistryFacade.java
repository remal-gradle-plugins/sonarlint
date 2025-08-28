package name.remal.gradle_plugins.sonarlint.internal.utils;

import java.rmi.NotBoundException;
import java.rmi.Remote;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
public class ClientRegistryFacade extends AbstractRegistryFacade {

    @SneakyThrows
    public <T extends Remote> T lookup(Class<T> interfaceClass) throws NotBoundException {
        var remote = registry.lookup(getRemoteName(interfaceClass));
        return interfaceClass.cast(remote);
    }

}
