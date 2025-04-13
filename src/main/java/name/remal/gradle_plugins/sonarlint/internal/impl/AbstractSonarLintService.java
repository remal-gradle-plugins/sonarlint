package name.remal.gradle_plugins.sonarlint.internal.impl;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.function.Function.identity;
import static java.util.function.Predicate.not;
import static lombok.AccessLevel.PRIVATE;
import static name.remal.gradle_plugins.sonarlint.internal.impl.SimpleLogOutput.SIMPLE_LOG_OUTPUT;
import static name.remal.gradle_plugins.toolkit.LazyProxy.asLazyListProxy;
import static name.remal.gradle_plugins.toolkit.LazyProxy.asLazyMapProxy;
import static name.remal.gradle_plugins.toolkit.LazyValue.lazyValue;
import static name.remal.gradle_plugins.toolkit.ObjectUtils.isNotEmpty;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import name.remal.gradle_plugins.toolkit.AbstractClosablesContainer;
import name.remal.gradle_plugins.toolkit.LazyValue;
import name.remal.gradle_plugins.toolkit.ObjectUtils;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.jetbrains.annotations.Unmodifiable;
import org.sonar.api.config.PropertyDefinition;
import org.sonar.api.config.PropertyDefinitions;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonar.api.server.rule.RulesDefinition.ExtendedRepository;
import org.sonar.api.server.rule.RulesDefinition.Repository;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.plugin.commons.PluginsLoadResult;
import org.sonarsource.sonarlint.core.plugin.commons.PluginsLoader;
import org.sonarsource.sonarlint.core.plugin.commons.PluginsLoader.Configuration;
import org.sonarsource.sonarlint.core.plugin.commons.container.SpringComponentContainer;
import org.sonarsource.sonarlint.core.plugin.commons.loading.PluginInfo;
import org.sonarsource.sonarlint.core.rule.extractor.RuleDefinitionsLoader;
import org.sonarsource.sonarlint.core.rule.extractor.RulesDefinitionExtractorContainer;

@RequiredArgsConstructor
abstract class AbstractSonarLintService<Params extends AbstractSonarLintServiceParams>
    extends AbstractClosablesContainer {

    @Getter(PRIVATE)
    protected final Params params;


    protected final Logger logger = Logging.getLogger(this.getClass());

    static {
        SonarLintLogger.get().setTarget(SIMPLE_LOG_OUTPUT);
    }


    protected final LazyValue<PluginsLoadResult> loadedPlugins = lazyValue(() -> {
        var params = getParams();

        var pluginJarLocations = params.getPluginPaths().stream()
            .filter(Objects::nonNull)
            .distinct()
            .filter(path -> {
                try {
                    var pluginInfo = PluginInfo.create(path);
                    logger.debug("plugin={}: {}", pluginInfo, path);
                    return true;
                } catch (Exception e) {
                    logger.debug("not a plugin: " + path, e);
                    return false;
                }
            })
            .collect(toImmutableSet());

        var sonarLanguages = params.getLanguagesToProcess().stream()
            .map(SonarLintLanguageConverter::convertSonarLintLanguage)
            .collect(toImmutableSet());

        var pluginsConfig = new Configuration(
            pluginJarLocations,
            sonarLanguages,
            true,
            Optional.of(Version.create("9999.9999.9999"))
        );

        var loadedPlugins = new PluginsLoader().load(pluginsConfig, Set.of());
        registerCloseable(loadedPlugins);
        return loadedPlugins;
    });

    private final LazyValue<SpringComponentContainer> definitionsExtractorContainer = lazyValue(() -> {
        var container = new RulesDefinitionExtractorContainer(
            loadedPlugins.get().getLoadedPlugins().getAllPluginInstancesByKeys()
        );
        container.startComponents();
        registerCloseable(container::stopComponents);
        return container;
    });

    @Unmodifiable
    protected final List<PropertyDefinition> allPropertyDefinitions = asLazyListProxy(() -> {
        var definitions = definitionsExtractorContainer.get()
            .getComponentByType(PropertyDefinitions.class)
            .getAll();

        return List.copyOf(definitions);
    });

    @Unmodifiable
    protected final Map<RuleKey, RulesDefinition.Rule> allRules = asLazyMapProxy(() -> {
        var rulesDefinitionContext = definitionsExtractorContainer.get()
            .getComponentByType(RuleDefinitionsLoader.class)
            .getContext();
        return rulesDefinitionContext.repositories().stream()
            .filter(not(Repository::isExternal))
            .map(ExtendedRepository::rules)
            .flatMap(Collection::stream)
            .collect(toImmutableMap(
                rule -> RuleKey.of(rule.repository().key(), rule.key()),
                identity(),
                (oldRule, rule) -> rule
            ));
    });


    @Unmodifiable
    protected static Set<RuleKey> getRulesKeys(Collection<String> ruleKeyStrings) {
        return ruleKeyStrings.stream()
            .filter(ObjectUtils::isNotEmpty)
            .map(RuleKey::parse)
            .collect(toImmutableSet());
    }

    @Unmodifiable
    protected static Map<RuleKey, Map<String, String>> getRuleProperties(
        Map<String, Map<String, String>> rulePropertiesWithStringKeys
    ) {
        return rulePropertiesWithStringKeys.entrySet().stream()
            .filter(entry -> isNotEmpty(entry.getKey()) && isNotEmpty(entry.getValue()))
            .collect(toImmutableMap(
                entry -> RuleKey.parse(entry.getKey()),
                Entry::getValue,
                (oldProps, props) -> props
            ));
    }

}
