package name.remal.gradle_plugins.sonarlint.internal.impl;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.function.Function.identity;
import static java.util.function.Predicate.not;
import static lombok.AccessLevel.PRIVATE;
import static name.remal.gradle_plugins.sonarlint.internal.impl.LogOutputViaSlf4j.LOG_OUTPUT_VIA_SLF4J;
import static name.remal.gradle_plugins.toolkit.LazyProxy.asLazyListProxy;
import static name.remal.gradle_plugins.toolkit.LazyProxy.asLazyMapProxy;
import static name.remal.gradle_plugins.toolkit.LazyValue.lazyValue;
import static name.remal.gradle_plugins.toolkit.ObjectUtils.isNotEmpty;
import static name.remal.gradle_plugins.toolkit.SneakyThrowUtils.sneakyThrow;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import name.remal.gradle_plugins.toolkit.AbstractClosablesContainer;
import name.remal.gradle_plugins.toolkit.LazyValue;
import name.remal.gradle_plugins.toolkit.ObjectUtils;
import org.jetbrains.annotations.Unmodifiable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.sonarsource.sonarlint.core.rule.extractor.RuleSettings;
import org.sonarsource.sonarlint.core.rule.extractor.RulesDefinitionExtractorContainer;

@RequiredArgsConstructor
abstract class AbstractSonarLintService<Params extends AbstractSonarLintServiceParams>
    extends AbstractClosablesContainer {

    @Getter(PRIVATE)
    protected final Params params;


    protected final Logger logger = LoggerFactory.getLogger(this.getClass());

    static {
        SonarLintLogger.get().setTarget(LOG_OUTPUT_VIA_SLF4J);
    }


    protected final LazyValue<PluginsLoadResult> loadedPlugins = lazyValue(() -> {
        var params = getParams();

        var pluginJarLocations = params.getPluginFiles().stream()
            .filter(Objects::nonNull)
            .map(File::toPath)
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
        registerCloseable(loadedPlugins.getLoadedPlugins()::close);
        return loadedPlugins;
    });

    private final LazyValue<SpringComponentContainer> definitionsExtractorContainer = lazyValue(() -> {
        var container = new RulesDefinitionExtractorContainer(
            loadedPlugins.get().getLoadedPlugins().getAllPluginInstancesByKeys(),
            new RuleSettings(Map.of())
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


    @SneakyThrows
    protected static <T> T withThreadLogger(Callable<T> action) {
        var logger = SonarLintLogger.get();
        var prevTarget = logger.getTargetForCopy();
        logger.setTarget(LOG_OUTPUT_VIA_SLF4J);
        try {
            return action.call();

        } finally {
            logger.setTarget(prevTarget);
        }
    }


    static {
        try {
            new URL("jar", "", "file:test.jar!/resource.txt").openConnection().setDefaultUseCaches(false);
        } catch (IOException e) {
            throw sneakyThrow(e);
        }
    }

}
