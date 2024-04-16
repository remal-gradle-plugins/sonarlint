package name.remal.gradle_plugins.sonarlint;

import static name.remal.gradle_plugins.sonarlint.internal.NodeJsNotFound.nodeJsNotFound;
import static name.remal.gradle_plugins.toolkit.CrossCompileServices.loadCrossCompileService;
import static name.remal.gradle_plugins.toolkit.FileUtils.normalizeFile;
import static name.remal.gradle_plugins.toolkit.TimeoutUtils.withTimeout;

import java.io.File;
import java.time.Duration;
import javax.inject.Inject;
import lombok.RequiredArgsConstructor;
import lombok.val;
import name.remal.gradle_plugins.sonarlint.internal.NodeJsInfo;
import org.gradle.api.provider.ProviderFactory;

@RequiredArgsConstructor(onConstructor_ = {@Inject})
abstract class NodeJsInfoRetriever {

    private static final NodeJsExecutableMethods METHODS = loadCrossCompileService(NodeJsExecutableMethods.class);

    public NodeJsInfo getNodeJsInfo(File file) {
        try {
            val normalizedFile = normalizeFile(file);
            return withTimeout(Duration.ofSeconds(5), () ->
                METHODS.getNodeJsInfo(getProviders(), normalizedFile)
            );

        } catch (Throwable e) {
            return nodeJsNotFound(e);
        }
    }


    @Inject
    protected abstract ProviderFactory getProviders();

}
