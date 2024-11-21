package name.remal.gradle_plugins.sonarlint.internal.impl;

import static java.util.Arrays.stream;
import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;
import static lombok.AccessLevel.PRIVATE;
import static name.remal.gradle_plugins.sonarlint.internal.impl.SimpleLogOutput.SIMPLE_LOG_OUTPUT;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.CustomLog;
import lombok.NoArgsConstructor;
import lombok.val;
import name.remal.gradle_plugins.sonarlint.internal.NodeJsFound;
import name.remal.gradle_plugins.sonarlint.internal.SonarLintExecutionParams;
import org.sonar.api.Plugin;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.server.rule.RulesDefinition.Rule;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.plugin.commons.PluginsLoadResult;
import org.sonarsource.sonarlint.core.plugin.commons.PluginsLoader;
import org.sonarsource.sonarlint.core.plugin.commons.PluginsLoader.Configuration;
import org.sonarsource.sonarlint.core.plugin.commons.loading.PluginInfo;
import org.sonarsource.sonarlint.core.rule.extractor.RulesDefinitionExtractorContainer;

@NoArgsConstructor(access = PRIVATE)
@CustomLog
abstract class SonarLintUtils {

    static {
        SonarLintLogger.setTarget(SIMPLE_LOG_OUTPUT);
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
        Set<SonarLanguage> enabledLanguages,
        @Nullable Version nodeJsVersion
    ) {
        val config = new Configuration(
            pluginJarLocations,
            enabledLanguages,
            true,
            Optional.ofNullable(nodeJsVersion)
        );
        return new PluginsLoader().load(config, emptySet());
    }


    public static Set<SonarLanguage> getEnabledLanguages(SonarLintExecutionParams params) {
        val includedLanguages = params.getIncludedLanguages().get();
        val excludedLanguages = params.getExcludedLanguages().get();
        return stream(SonarLanguage.values())
            .filter(language -> includedLanguages.isEmpty() || isLanguageInFilter(language, includedLanguages))
            .filter(language -> excludedLanguages.isEmpty() || !isLanguageInFilter(language, excludedLanguages))
            .collect(toCollection(LinkedHashSet::new));
    }

    private static boolean isLanguageInFilter(SonarLanguage language, Collection<String> filter) {
        return filter.stream().anyMatch(id ->
            id.equalsIgnoreCase(language.getSonarLanguageKey())
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


    public static Map<RuleKey, Rule> extractRules(
        Map<String, Plugin> pluginInstancesByKeys,
        Set<SonarLanguage> enabledLanguages
    ) {
        val container = new RulesDefinitionExtractorContainer(pluginInstancesByKeys);
        container.execute();

        val context = container.getRulesDefinitionContext();
        val rules = context.repositories().stream()
            .filter(repo -> !repo.isExternal())
            .filter(repo -> {
                val language = SonarLanguage.forKey(repo.language()).orElse(null);
                return language != null && enabledLanguages.contains(language);
            })
            .flatMap(repo -> repo.rules().stream())
            .collect(toList());

        val result = new LinkedHashMap<RuleKey, Rule>(rules.size());
        for (val rule : rules) {
            val key = RuleKey.of(rule.repository().key(), rule.key());
            result.putIfAbsent(key, rule);
        }
        return result;
    }

}
