package name.remal.gradle_plugins.sonarlint;

import static java.lang.String.format;
import static java.util.Collections.emptySet;
import static name.remal.gradle_plugins.sonarlint.NodeJsVersions.LATEST_NODEJS_LTS_VERSION;
import static name.remal.gradle_plugins.sonarlint.OsDetector.DETECTED_OS;
import static name.remal.gradle_plugins.sonarlint.PublishedNodeJs.PUBLISHED_NODEJS_OS;
import static name.remal.gradle_plugins.sonarlint.SonarLintPluginBuildInfo.SONARLINT_PLUGIN_ARTIFACT_ID;
import static name.remal.gradle_plugins.sonarlint.SonarLintPluginBuildInfo.SONARLINT_PLUGIN_GROUP;
import static name.remal.gradle_plugins.sonarlint.SonarLintPluginBuildInfo.SONARLINT_PLUGIN_VERSION;

import com.tisonkun.os.core.OS;
import java.io.File;
import javax.annotation.Nullable;
import javax.inject.Inject;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.dsl.DependencyHandler;

@RequiredArgsConstructor(onConstructor_ = {@Inject})
abstract class NodeJsDetectorPublished extends NodeJsDetector {

    @Nullable
    @Override
    public File detectDefaultNodeJsExecutable() {
        val os = DETECTED_OS.os;
        val arch = DETECTED_OS.arch;
        val isPublished = PUBLISHED_NODEJS_OS.getOrDefault(os, emptySet()).contains(arch);
        if (!isPublished) {
            return null;
        }

        val dependency = getDependencies().create(format(
            "%s:%s:%s:%s@%s",
            SONARLINT_PLUGIN_GROUP,
            SONARLINT_PLUGIN_ARTIFACT_ID,
            SONARLINT_PLUGIN_VERSION,
            os + "-" + arch,
            os == OS.windows ? "exe" : ""
        ));
        val configuration = getConfigurations().detachedConfiguration(dependency);
        val file = configuration.getFiles().iterator().next();

        setExecutePermissions(file);
        checkNodeJsExecutableForTests(file);
        return file;
    }

    @Nullable
    @Override
    public File detectNodeJsExecutable(String version) {
        if (LATEST_NODEJS_LTS_VERSION.equals(version)) {
            return detectDefaultNodeJsExecutable();
        }

        return null;
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
