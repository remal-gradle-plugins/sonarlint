package name.remal.gradle_plugins.sonarlint.internal.impl;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.lang.String.join;
import static java.lang.System.nanoTime;
import static java.nio.file.Files.createTempDirectory;
import static java.util.Map.Entry;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.function.Function.identity;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toUnmodifiableList;
import static name.remal.gradle_plugins.sonarlint.SonarLintLanguage.KOTLIN;
import static name.remal.gradle_plugins.sonarlint.SonarLintLanguage.SCALA;
import static name.remal.gradle_plugins.sonarlint.SonarLintLanguageIncludes.getLanguageRelativePathPredicates;
import static name.remal.gradle_plugins.sonarlint.internal.RulesDocumentation.RuleStatus.DISABLED_BY_DEFAULT;
import static name.remal.gradle_plugins.sonarlint.internal.RulesDocumentation.RuleStatus.ENABLED_BY_DEFAULT;
import static name.remal.gradle_plugins.sonarlint.internal.impl.LogOutputViaSlf4j.LOG_OUTPUT_VIA_SLF4J;
import static name.remal.gradle_plugins.sonarlint.internal.impl.NoOpProgressMonitor.NOOP_PROGRESS_MONITOR;
import static name.remal.gradle_plugins.toolkit.LazyProxy.asLazyListProxy;
import static name.remal.gradle_plugins.toolkit.LazyProxy.asLazyMapProxy;
import static name.remal.gradle_plugins.toolkit.LazyValue.lazyValue;
import static name.remal.gradle_plugins.toolkit.ObjectUtils.isNotEmpty;
import static name.remal.gradle_plugins.toolkit.PathUtils.tryToDeleteRecursively;
import static name.remal.gradle_plugins.toolkit.SneakyThrowUtils.sneakyThrow;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import lombok.SneakyThrows;
import name.remal.gradle_plugins.sonarlint.SonarLintLanguage;
import name.remal.gradle_plugins.sonarlint.SonarLintLanguageType;
import name.remal.gradle_plugins.sonarlint.internal.PropertiesDocumentation;
import name.remal.gradle_plugins.sonarlint.internal.PropertiesDocumentation.PropertyDocumentation;
import name.remal.gradle_plugins.sonarlint.internal.RulesDocumentation;
import name.remal.gradle_plugins.sonarlint.internal.SourceFile;
import name.remal.gradle_plugins.toolkit.AbstractClosablesContainer;
import name.remal.gradle_plugins.toolkit.LazyValue;
import name.remal.gradle_plugins.toolkit.ObjectUtils;
import name.remal.gradle_plugins.toolkit.issues.Issue;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.config.PropertyDefinition;
import org.sonar.api.config.PropertyDefinitions;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.server.rule.RuleParamType;
import org.sonar.api.server.rule.RulesDefinition.ExtendedRepository;
import org.sonar.api.server.rule.RulesDefinition.Repository;
import org.sonar.api.server.rule.RulesDefinition.Rule;
import org.sonarsource.sonarlint.core.analysis.api.ActiveRule;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisSchedulerConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleInfo;
import org.sonarsource.sonarlint.core.analysis.container.global.GlobalAnalysisContainer;
import org.sonarsource.sonarlint.core.analysis.container.global.ModuleRegistry;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.plugin.commons.PluginsLoadResult;
import org.sonarsource.sonarlint.core.plugin.commons.PluginsLoader;
import org.sonarsource.sonarlint.core.plugin.commons.PluginsLoader.Configuration;
import org.sonarsource.sonarlint.core.plugin.commons.container.SpringComponentContainer;
import org.sonarsource.sonarlint.core.plugin.commons.loading.PluginInfo;
import org.sonarsource.sonarlint.core.rule.extractor.RuleDefinitionsLoader;
import org.sonarsource.sonarlint.core.rule.extractor.RulesDefinitionExtractorContainer;

@Builder
public class SonarLintService extends AbstractClosablesContainer {

    private static final Logger logger = LoggerFactory.getLogger(SonarLintService.class);

    static {
        SonarLintLogger.get().setTarget(LOG_OUTPUT_VIA_SLF4J);
    }


    @Getter
    @Singular
    private final Collection<File> pluginFiles;


    //#region Base components

    private final LazyValue<PluginsLoadResult> loadedPlugins = lazyValue(() -> {
        var pluginJarLocations = getPluginFiles().stream()
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

        var pluginsConfig = new Configuration(
            pluginJarLocations,
            Set.of(SonarLanguage.values()),
            true,
            Optional.of(Version.create("9999.9999.9999"))
        );

        var loadedPlugins = new PluginsLoader().load(pluginsConfig, Set.of());
        registerCloseable(loadedPlugins.getLoadedPlugins()::close);
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
    private final List<PropertyDefinition> allPropertyDefinitions = asLazyListProxy(() -> {
        var definitions = definitionsExtractorContainer.get()
            .getComponentByType(PropertyDefinitions.class)
            .getAll();

        return List.copyOf(definitions);
    });

    @Unmodifiable
    private final Map<RuleKey, Rule> allRules = asLazyMapProxy(() -> {
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

    //#endregion


    //#region Analysis

    @Unmodifiable
    @VisibleForTesting
    Map<RuleKey, Rule> getEnabledRules(
        Set<SonarLintLanguage> enabledLanguages,
        Set<String> enabledRulesConfig,
        Set<String> disabledRulesConfig
    ) {
        var enabledLanguageIds = enabledLanguages.stream()
            .map(SonarLintLanguageConverter::convertSonarLintLanguage)
            .flatMap(lang -> Stream.of(lang.name(), lang.getSonarLanguageKey()))
            .map(String::toLowerCase)
            .collect(toImmutableSet());

        var enabledRules = getRulesKeys(enabledRulesConfig);
        var disabledRules = getRulesKeys(disabledRulesConfig);

        return allRules.entrySet().stream()
            .filter(entry -> {
                var ruleKey = entry.getKey();
                var rule = entry.getValue();

                var ruleLanguage = rule.repository().language();
                if (!enabledLanguageIds.contains(ruleLanguage.toLowerCase())) {
                    return false;
                }

                if (disabledRules.contains(ruleKey)) {
                    return false;
                }
                if (!rule.activatedByDefault() && !enabledRules.contains(ruleKey)) {
                    return false;
                }

                return true;
            })
            .collect(toImmutableMap(
                Entry::getKey,
                Entry::getValue,
                (oldRule, rule) -> rule
            ));
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private final LazyValue<GlobalAnalysisContainer> analysisContainer = lazyValue(() -> {
        var workDir = createTempDirectory(SonarLintService.class.getSimpleName() + "-work-");
        var userHomeDir = createTempDirectory(SonarLintService.class.getSimpleName() + "-userHome-");

        var analysisSchedulerConfiguration = AnalysisSchedulerConfiguration.builder()
            .setWorkDir(workDir)
            .setExtraProperties(Map.of(
                "sonar.userHome", userHomeDir.toString()
            ))
            .setClientPid(-1)
            .build();

        var container = new GlobalAnalysisContainer(
            analysisSchedulerConfiguration,
            loadedPlugins.get().getLoadedPlugins()
        );
        container.startComponents();
        registerCloseable(container::stopComponents);

        registerCloseable(() -> tryToDeleteRecursively(workDir));
        registerCloseable(() -> tryToDeleteRecursively(userHomeDir));

        return container;
    });

    private final LazyValue<ModuleRegistry> moduleRegistry = lazyValue(() -> {
        var moduleRegistry = analysisContainer.get().getModuleRegistry();
        registerCloseable(moduleRegistry::stopAll);
        return moduleRegistry;
    });

    private final Object nodeJsBridgeServerStartMutex = new Object[0];

    @SuppressWarnings({"java:S107", "java:S3776"})
    public Collection<Issue> analyze(
        String moduleId,
        File repositoryRoot,
        Collection<SourceFile> sourceFiles,
        Map<String, String> sonarProperties,
        Set<SonarLintLanguage> enabledLanguages,
        Set<String> enabledRulesConfig,
        Set<String> disabledRulesConfig,
        Map<String, Map<String, String>> rulesPropertiesConfig,
        @Nullable Consumer<String> logMessagesConsumer
    ) {
        if (sourceFiles.isEmpty()) {
            return List.of();
        }

        return withLogMessagesConsumer(logMessagesConsumer, () ->
            withSingleThreadedFirstFrontendScan(sourceFiles, sonarProperties, enabledLanguages, () -> {
                var inputFiles = sourceFiles.stream()
                    .filter(Objects::nonNull)
                    .map(SimpleClientInputFile::new)
                    .map(ClientInputFile.class::cast)
                    .collect(toUnmodifiableList());

                var clientFileSystem = new SimpleClientModuleFileSystem(inputFiles);
                var moduleInfo = new ClientModuleInfo(moduleId, clientFileSystem);
                var moduleRegistry = this.moduleRegistry.get();
                moduleRegistry.registerModule(moduleInfo);
                try {
                    var enabledRules = getEnabledRules(
                        enabledLanguages,
                        enabledRulesConfig,
                        disabledRulesConfig
                    );
                    var rulesProperties = getRuleProperties(rulesPropertiesConfig);
                    var activeRules = enabledRules.entrySet().stream()
                        .map(entry -> {
                            var ruleKey = entry.getKey();
                            var rule = entry.getValue();

                            var activeRule = new ActiveRule(ruleKey.toString(), rule.repository().language());

                            var ruleProperties = rulesProperties.get(ruleKey);
                            if (ruleProperties != null) {
                                activeRule.setParams(ruleProperties);
                            }

                            return activeRule;
                        })
                        .collect(toUnmodifiableList());


                    var analysisConfiguration = AnalysisConfiguration.builder()
                        .setBaseDir(repositoryRoot.toPath())
                        .addInputFiles(inputFiles)
                        .putAllExtraProperties(sonarProperties)
                        .addActiveRules(activeRules)
                        .build();


                    Collection<Issue> issues = new LinkedHashSet<>();
                    var issueConverter = new SonarIssueConverter(allRules);
                    Consumer<org.sonarsource.sonarlint.core.analysis.api.Issue> issueListener = sonarIssue -> {
                        synchronized (issues) {
                            var issue = issueConverter.convert(sonarIssue);
                            if (issue != null) {
                                issues.add(issue);
                            }
                        }
                    };


                    var moduleContainer = requireNonNull(moduleRegistry.getContainerFor(moduleId));
                    var startTime = nanoTime();
                    moduleContainer.analyze(
                        analysisConfiguration,
                        issueListener,
                        NOOP_PROGRESS_MONITOR,
                        null
                    );
                    System.err.printf("%s: %d%n", moduleId, NANOSECONDS.toMillis(nanoTime() - startTime));

                    return issues;

                } finally {
                    moduleRegistry.unregisterModule(moduleId);
                }
            })
        );
    }

    private final Object frontendScanMutex = new Object[0];

    @SneakyThrows
    private <T> T withSingleThreadedFirstFrontendScan(
        Collection<SourceFile> sourceFiles,
        Map<String, String> sonarProperties,
        Set<SonarLintLanguage> enabledLanguages,
        Callable<T> action
    ) {
        var frontendRelativePathPredicate = ALWAYS_FALSE_RELATIVE_PATH_PREDICATE;
        for (var entry : getLanguageRelativePathPredicates(sonarProperties).entrySet()) {
            if (enabledLanguages.contains(entry.getKey())
                && entry.getKey().getType() == SonarLintLanguageType.FRONTEND
            ) {
                frontendRelativePathPredicate = frontendRelativePathPredicate.or(entry.getValue());
            }
        }
        var hasAnyFrontendSourceFile = sourceFiles.stream()
            .map(SourceFile::getRelativePath)
            .anyMatch(frontendRelativePathPredicate);
        if (!hasAnyFrontendSourceFile) {
            return action.call();
        }

        synchronized (frontendScanMutex) {
            return action.call();
        }
    }

    //#endregion


    //#region Documentation

    @VisibleForTesting
    PropertiesDocumentation collectPropertiesDocumentationWithoutEnrichment() {
        var propertiesDoc = new PropertiesDocumentation();
        allPropertyDefinitions.forEach(propDef -> propertiesDoc.property(propDef.key(), propDoc -> {
            propDoc.setName(propDef.name());
            propDoc.setDescription(propDef.description());
            Optional.ofNullable(propDef.type())
                .map(Enum::name)
                .ifPresent(propDoc::setType);
            propDoc.setDefaultValue(propDef.defaultValue());
        }));
        return propertiesDoc;
    }

    public PropertiesDocumentation collectPropertiesDocumentation(@Nullable Consumer<String> logMessagesConsumer) {
        return withLogMessagesConsumer(logMessagesConsumer, () -> {
            var propertiesDoc = collectPropertiesDocumentationWithoutEnrichment();

            propertiesDoc.getProperties().computeIfAbsent("sonar.kotlin.file.suffixes", propertyKey -> {
                if (!loadedPlugins.get().getLoadedPlugins().getAllPluginInstancesByKeys().containsKey("kotlin")) {
                    return null;
                }

                return PropertyDocumentation.builder()
                    .name("File Suffixes")
                    .description("List of suffixes for files to analyze.")
                    .type("STRING")
                    .defaultValue(join(",", KOTLIN.getDefaultFileSuffixes()))
                    .build();
            });

            propertiesDoc.getProperties().computeIfAbsent("sonar.scala.file.suffixes", propertyKey -> {
                if (!loadedPlugins.get().getLoadedPlugins().getAllPluginInstancesByKeys().containsKey("sonarscala")) {
                    return null;
                }

                return PropertyDocumentation.builder()
                    .name("File Suffixes")
                    .description("List of suffixes for files to analyze.")
                    .type("STRING")
                    .defaultValue(join(",", SCALA.getDefaultFileSuffixes()))
                    .build();
            });

            return propertiesDoc;
        });
    }

    @SneakyThrows
    public RulesDocumentation collectRulesDocumentation(@Nullable Consumer<String> logMessagesConsumer) {
        return withLogMessagesConsumer(logMessagesConsumer, () -> {
            var rulesDoc = new RulesDocumentation();
            allRules.forEach((key, rule) -> rulesDoc.rule(key.toString(), ruleDoc -> {
                ruleDoc.setName(rule.name());

                if (rule.activatedByDefault()) {
                    ruleDoc.setStatus(ENABLED_BY_DEFAULT);
                } else {
                    ruleDoc.setStatus(DISABLED_BY_DEFAULT);
                }

                Optional.ofNullable(rule.repository().language())
                    .flatMap(SonarLanguage::forKey)
                    .map(SonarLanguage::getSonarLanguageKey)
                    .ifPresent(ruleDoc::setLanguage);

                rule.params().forEach(param -> ruleDoc.param(param.key(), paramDoc -> {
                    paramDoc.setDescription(param.description());
                    Optional.ofNullable(param.type())
                        .map(RuleParamType::type)
                        .ifPresent(paramDoc::setType);
                    paramDoc.setDefaultValue(param.defaultValue());
                    paramDoc.setPossibleValues(param.type().values());
                }));
            }));
            return rulesDoc;
        });
    }

    //#endregion


    //#region Utils

    @SuppressWarnings("UnnecessaryLambda")
    private static final Predicate<String> ALWAYS_FALSE_RELATIVE_PATH_PREDICATE = __ -> false;

    @SneakyThrows
    private static <T> T withLogMessagesConsumer(@Nullable Consumer<String> logMessagesConsumer, Callable<T> action) {
        if (logMessagesConsumer == null) {
            return action.call();
        }

        var logger = SonarLintLogger.get();
        var prevTarget = logger.getTargetForCopy();
        var newTarget = new LogOutputViaConsumer(logMessagesConsumer);
        logger.setTarget(newTarget);
        try {
            return action.call();

        } finally {
            logger.setTarget(prevTarget);
        }
    }

    @Unmodifiable
    private static Set<RuleKey> getRulesKeys(Collection<String> ruleKeyStrings) {
        return ruleKeyStrings.stream()
            .filter(ObjectUtils::isNotEmpty)
            .map(RuleKey::parse)
            .collect(toImmutableSet());
    }

    @Unmodifiable
    private static Map<RuleKey, Map<String, String>> getRuleProperties(
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


    static {
        try {
            new URL("jar", "", "file:test.jar!/resource.txt").openConnection().setDefaultUseCaches(false);
        } catch (IOException e) {
            throw sneakyThrow(e);
        }
    }

    //#endregion

}
