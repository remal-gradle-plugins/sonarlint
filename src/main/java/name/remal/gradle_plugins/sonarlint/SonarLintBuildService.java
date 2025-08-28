package name.remal.gradle_plugins.sonarlint;

import static lombok.AccessLevel.PUBLIC;

import java.net.InetAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.inject.Inject;
import lombok.NoArgsConstructor;
import name.remal.gradle_plugins.sonarlint.internal.client.SonarLintClient;
import name.remal.gradle_plugins.sonarlint.internal.client.SonarLintClientParams;
import name.remal.gradle_plugins.sonarlint.internal.server.api.SonarLintAnalyzer;
import name.remal.gradle_plugins.toolkit.AbstractCloseablesContainer;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;

@NoArgsConstructor(access = PUBLIC, onConstructor_ = {@Inject})
abstract class SonarLintBuildService
    extends AbstractCloseablesContainer
    implements BuildService<BuildServiceParameters.None>, AutoCloseable {

    public SonarLintAnalyzer getAnalyzer(SonarLintClientParams clientParams) {
        return getClient(clientParams).getAnalyzer();
    }

    public InetAddress getClientBindAddress(SonarLintClientParams clientParams) {
        return getClient(clientParams).getBindAddress();
    }


    private final ConcurrentMap<SonarLintClientParams, SonarLintClient> clientsCache = new ConcurrentHashMap<>();

    @SuppressWarnings("resource")
    private SonarLintClient getClient(SonarLintClientParams params) {
        return clientsCache.computeIfAbsent(params, currentParams -> {
            registerCloseable(() -> clientsCache.remove(currentParams));
            return registerCloseable(new SonarLintClient(currentParams));
        });
    }

}
