package name.remal.gradle_plugins.sonarlint.internal.impl;

import static java.nio.file.Files.createDirectories;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toCollection;
import static lombok.AccessLevel.PRIVATE;
import static name.remal.gradle_plugins.sonarlint.internal.impl.GradleLogOutput.GRADLE_LOG_OUTPUT;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.CustomLog;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.val;
import name.remal.gradle_plugins.sonarlint.internal.NodeJsFound;
import name.remal.gradle_plugins.sonarlint.internal.SonarLintExecutionParams;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneGlobalConfiguration;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.plugin.commons.PluginsLoadResult;
import org.sonarsource.sonarlint.core.plugin.commons.PluginsLoader;
import org.sonarsource.sonarlint.core.plugin.commons.PluginsLoader.Configuration;
import org.sonarsource.sonarlint.core.plugin.commons.loading.PluginInfo;

@NoArgsConstructor(access = PRIVATE)
@CustomLog
abstract class SonarLintUtils {

    static {
        SonarLintLogger.setTarget(GRADLE_LOG_OUTPUT);
    }


    public static Set<Path> getPluginJarLocations(SonarLintExecutionParams params) {
        return params.getPluginsClasspath().getFiles().stream()
            .distinct()
            .map(File::toPath)
            .filter(path -> {
                try {
                    val pluginInfo = PluginInfo.create(path);
                    logger.debug("plugin={}: {}", pluginInfo, path);
                    return true;
                } catch (Exception ignored) {
                    logger.debug("not a plugin: {}", path);
                    return false;
                }
            })
            .collect(toCollection(LinkedHashSet::new));
    }


    public static PluginsLoadResult loadPlugins(SonarLintExecutionParams params) {
        val pluginJarLocations = getPluginJarLocations(params);
        val enabledLanguages = getEnabledLanguages(params);
        val nodeJsVersion = getNodeJsVersion(params);
        return loadPlugins(pluginJarLocations, enabledLanguages, nodeJsVersion);
    }

    public static PluginsLoadResult loadPlugins(
        Set<Path> pluginJarLocations,
        Set<Language> enabledLanguages,
        @Nullable Version nodeJsVersion
    ) {
        val config = new Configuration(
            pluginJarLocations,
            enabledLanguages,
            true,
            Optional.ofNullable(nodeJsVersion)
        );
        return new PluginsLoader().load(config);
    }


    public static Set<Language> getEnabledLanguages(SonarLintExecutionParams params) {
        val includedLanguages = params.getIncludedLanguages().get();
        val excludedLanguages = params.getExcludedLanguages().get();
        return stream(Language.values())
            .filter(language -> includedLanguages.isEmpty() || isLanguageInFilter(language, includedLanguages))
            .filter(language -> excludedLanguages.isEmpty() || !isLanguageInFilter(language, excludedLanguages))
            .collect(toCollection(LinkedHashSet::new));
    }

    private static boolean isLanguageInFilter(Language language, Collection<String> filter) {
        return filter.stream().anyMatch(id ->
            id.equalsIgnoreCase(language.getLanguageKey())
                || id.equalsIgnoreCase(language.getPluginKey())
                || id.equalsIgnoreCase(language.name())
        );
    }


    @Nullable
    public static Path getNodeJsExecutable(SonarLintExecutionParams params) {
        return params.getNodeJsInfo()
            .map(NodeJsFound::getExecutable)
            .map(File::toPath)
            .getOrNull();
    }

    @Nullable
    public static Version getNodeJsVersion(SonarLintExecutionParams params) {
        return params.getNodeJsInfo()
            .map(NodeJsFound::getVersion)
            .map(Version::create)
            .getOrNull();
    }


    @SneakyThrows
    @SuppressWarnings("DataFlowIssue")
    public static StandaloneGlobalConfiguration createEngineConfig(SonarLintExecutionParams params) {
        return StandaloneGlobalConfiguration.builder()
            .addEnabledLanguages(getEnabledLanguages(params).toArray(new Language[0]))
            .addPlugins(getPluginJarLocations(params).toArray(new Path[0]))
            .setSonarLintUserHome(createDirectories(params.getHomeDir().get().getAsFile().toPath()))
            .setWorkDir(createDirectories(params.getWorkDir().get().getAsFile().toPath()))
            .setLogOutput(GRADLE_LOG_OUTPUT)
            .setNodeJs(getNodeJsExecutable(params), getNodeJsVersion(params))
            .build();
    }

}
