package name.remal.gradle_plugins.sonarlint.internal.latest;

import static java.nio.file.Files.createDirectories;
import static java.util.Arrays.stream;
import static name.remal.gradle_plugins.sonarlint.internal.NodeJsInfo.collectNodeJsInfoFor;
import static name.remal.gradle_plugins.toolkit.ObjectUtils.isEmpty;

import com.google.auto.service.AutoService;
import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
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
        val includedLanguages = params.getIncludedLanguages().getOrNull();
        val excludedLanguages = params.getExcludedLanguages().getOrNull();
        val enabledLanguages = stream(Language.values())
            .filter(language -> isEmpty(includedLanguages) || isLanguageInFilter(language, includedLanguages))
            .filter(language -> isEmpty(excludedLanguages) || !isLanguageInFilter(language, excludedLanguages))
            .toArray(Language[]::new);
        val builder = StandaloneGlobalConfiguration.builder()
            .addEnabledLanguages(enabledLanguages)
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

    private static boolean isLanguageInFilter(Language language, Collection<String> filter) {
        return filter.stream().anyMatch(id ->
            id.equalsIgnoreCase(language.getLanguageKey())
                || id.equalsIgnoreCase(language.getLabel())
                || id.equalsIgnoreCase(language.name())
        );
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
