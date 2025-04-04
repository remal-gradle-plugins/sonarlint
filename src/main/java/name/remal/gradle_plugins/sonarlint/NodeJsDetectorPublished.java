package name.remal.gradle_plugins.sonarlint;

import static java.lang.String.format;
import static java.util.Collections.emptySet;
import static name.remal.gradle_plugins.sonarlint.PublishedNodeJs.PUBLISHED_NODEJS_OS;
import static name.remal.gradle_plugins.sonarlint.SonarLintPluginBuildInfo.SONARLINT_PLUGIN_ARTIFACT_ID;
import static name.remal.gradle_plugins.sonarlint.SonarLintPluginBuildInfo.SONARLINT_PLUGIN_GROUP;
import static name.remal.gradle_plugins.sonarlint.SonarLintPluginBuildInfo.SONARLINT_PLUGIN_VERSION;
import static name.remal.gradle_plugins.toolkit.InTestFlags.isInTest;

import com.tisonkun.os.core.OS;
import javax.annotation.Nullable;
import javax.inject.Inject;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import name.remal.gradle_plugins.sonarlint.internal.NodeJsFound;
import name.remal.gradle_plugins.sonarlint.internal.NodeJsNotFound;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.dsl.DependencyHandler;

@RequiredArgsConstructor(onConstructor_ = {@Inject})
abstract class NodeJsDetectorPublished extends NodeJsDetector {

    @Nullable
    @Override
    @SneakyThrows
    @SuppressWarnings("Slf4jFormatShouldBeConst")
    public NodeJsFound detectNodeJsExecutable() {
        var os = osDetector.getDetectedOs().os;
        var arch = osDetector.getDetectedOs().arch;
        var isPublished = PUBLISHED_NODEJS_OS.getOrDefault(os, emptySet()).contains(arch);
        if (!isPublished) {
            return null;
        }

        var dependency = getDependencies().create(format(
            "%s:%s:%s:%s@%s",
            SONARLINT_PLUGIN_GROUP,
            SONARLINT_PLUGIN_ARTIFACT_ID,
            SONARLINT_PLUGIN_VERSION,
            os + "-" + arch,
            os == OS.windows ? "exe" : ""
        ));
        var configuration = getConfigurations().detachedConfiguration(dependency);
        var targetFile = configuration.getFiles().iterator().next();

        var info = nodeJsInfoRetriever.getNodeJsInfo(targetFile);

        if (info instanceof NodeJsNotFound) {
            var error = ((NodeJsNotFound) info).getError();
            if (isInTest()) {
                throw error;
            } else {
                var message = format(
                    "Downloaded Node.js from the plugin artifacts can't be used: %s",
                    error
                );
                logger.warn(message, error);
            }
            return null;
        }

        return (NodeJsFound) info;
    }


    @Override
    public int getOrder() {
        return 100;
    }


    @Inject
    protected abstract ConfigurationContainer getConfigurations();

    @Inject
    protected abstract DependencyHandler getDependencies();

}
