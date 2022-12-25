package name.remal.gradle_plugins.sonarlint.internal.latest;

import static java.nio.file.Files.createDirectories;
import static name.remal.gradle_plugins.sonarlint.internal.NodeJsInfo.collectNodeJsInfoFor;

import com.google.auto.service.AutoService;
import java.io.File;
import java.nio.file.Path;
import java.util.Optional;
import lombok.SneakyThrows;
import lombok.val;
import name.remal.gradle_plugins.sonarlint.internal.SonarLintExecutionParams;
import name.remal.gradle_plugins.sonarlint.internal.StandaloneGlobalConfigurationFactory;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneGlobalConfiguration;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.plugin.commons.loading.PluginInfo;

@AutoService(StandaloneGlobalConfigurationFactory.class)
final class StandaloneGlobalConfigurationFactoryLatest implements StandaloneGlobalConfigurationFactory {

    @Override
    @SneakyThrows
    public StandaloneGlobalConfiguration create(SonarLintExecutionParams params) {
        val builder = StandaloneGlobalConfiguration.builder()
            .addEnabledLanguages(Language.values())
            .addPlugins(params.getToolClasspath().getFiles().stream()
                .distinct()
                .map(File::toPath)
                .filter(path -> {
                    try {
                        PluginInfo.create(path);
                        return true;
                    } catch (Exception ignored) {
                        return false;
                    }
                })
                .toArray(Path[]::new)
            )
            .setSonarLintUserHome(createDirectories(params.getHomeDir().get().getAsFile().toPath()))
            .setWorkDir(createDirectories(params.getWorkDir().get().getAsFile().toPath()))
            .setLogOutput(new GradleLogOutput());
        updateNodeJsConfiguration(params, builder);
        return builder.build();
    }

    @SuppressWarnings("DataFlowIssue")
    private static void updateNodeJsConfiguration(
        SonarLintExecutionParams params,
        StandaloneGlobalConfiguration.Builder configurationBuilder
    ) {
        val nodeJsInfo = collectNodeJsInfoFor(params);
        configurationBuilder.setNodeJs(
            nodeJsInfo.getNodeJsPath(),
            Optional.ofNullable(nodeJsInfo.getVersion())
                .map(Version::create)
                .orElse(null)
        );
    }

}
